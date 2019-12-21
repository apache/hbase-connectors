/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.kafka;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.ReplicationPeerNotFoundException;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.regionserver.HRegionServer;
import org.apache.hadoop.hbase.regionserver.HRegionServerCommandLine;
import org.apache.hadoop.hbase.replication.ReplicationPeerConfig;
import org.apache.hadoop.hbase.replication.ReplicationPeerConfigBuilder;
import org.apache.hadoop.hbase.replication.ReplicationPeerDescription;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.VersionInfo;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.yetus.audience.InterfaceAudience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hbase.thirdparty.org.apache.commons.cli.CommandLine;
import org.apache.hbase.thirdparty.org.apache.commons.cli.DefaultParser;
import org.apache.hbase.thirdparty.org.apache.commons.cli.HelpFormatter;
import org.apache.hbase.thirdparty.org.apache.commons.cli.Options;
import org.apache.hbase.thirdparty.org.apache.commons.cli.ParseException;



/**
 * hbase to kafka bridge.
 *
 * Starts up a region server and receives replication events, just like a peer
 * cluster member.  It takes the events and cell by cell determines how to
 * route them (see kafka-route-rules.xml)
 */
@InterfaceAudience.Private
public final class KafkaProxy {
  private static final Logger LOG = LoggerFactory.getLogger(KafkaProxy.class);

  public static final String KAFKA_PROXY_RULES_FILE = "kafkaproxy.rule.file";
  public static final String KAFKA_PROXY_KAFKA_PROPERTIES = "kafkaproxy.kafka.properties";
  public static final String KAFKA_PROXY_KAFKA_BROKERS = "kafkaproxy.kafka.brokers";

  private static Map<String,String> DEFAULT_PROPERTIES = new HashMap<>();
  private static Map<String,String> CAN_OVERRIDE_DEFAULT_PROPERTIES = new HashMap<>();


  static {
    DEFAULT_PROPERTIES.put("hbase.cluster.distributed","true");
    DEFAULT_PROPERTIES.put("zookeeper.znode.parent","/kafkaproxy");
    DEFAULT_PROPERTIES.put("hbase.regionserver.info.port","17010");
    DEFAULT_PROPERTIES.put("hbase.client.connection.impl",
            "org.apache.hadoop.hbase.kafka.KafkaBridgeConnection");
    DEFAULT_PROPERTIES.put("hbase.regionserver.admin.service","false");
    DEFAULT_PROPERTIES.put("hbase.regionserver.client.service","false");
    DEFAULT_PROPERTIES.put("hbase.wal.provider",
            "org.apache.hadoop.hbase.wal.DisabledWALProvider");
    DEFAULT_PROPERTIES.put("hbase.regionserver.workers","false");
    DEFAULT_PROPERTIES.put("hfile.block.cache.size","0.0001");
    DEFAULT_PROPERTIES.put("hbase.mob.file.cache.size","0");
    DEFAULT_PROPERTIES.put("hbase.masterless","true");
    DEFAULT_PROPERTIES.put("hbase.regionserver.metahandler.count","1");
    DEFAULT_PROPERTIES.put("hbase.regionserver.replication.handler.count","1");
    DEFAULT_PROPERTIES.put("hbase.regionserver.handler.count","1");
    DEFAULT_PROPERTIES.put("hbase.ipc.server.read.threadpool.size","3");

    CAN_OVERRIDE_DEFAULT_PROPERTIES.put("hbase.regionserver.port","17020");
  }

  private static void printUsageAndExit(Options options, int exitCode) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("hbase kafkaproxy start", "", options,
      "\nTo run the kafka proxy as a daemon, execute " +
        "hbase-connectors-daemon.sh start|stop kafkaproxy \n" +
        "[--kafkabrokers (or -b) <kafka brokers (comma delmited)>] \n" +
        "[--routerulesfile (or -r) <file with rules to route to kafka "
        + "(defaults to kafka-route-rules.xm)>] \n" +
        "[--kafkaproperties (or -f) <Path to properties file that "
        + "has the kafka connection properties>] \n" +
        "[--peername (or -p) name of hbase peer to use (defaults to hbasekafka)]\n  " +
        "[--znode (or -z) root znode (defaults to /kafkaproxy)]  \n" +
        "[--enablepeer (or -e) enable peer on startup (defaults to false)]\n  " +
        "[--auto (or -a) auto create peer]  " +
        "\n", true);
    System.exit(exitCode);
  }

  /**
   * private constructor
   */
  private KafkaProxy() {

  }

  /**
   * Start the service
   * @param args program arguments
   * @throws Exception on error
   */
  public static void main(String[] args) throws Exception {

    Map<String,String> otherProps = new HashMap<>();

    Options options = new Options();

    options.addRequiredOption("b", "kafkabrokers", true,
      "Kafka Brokers (comma delimited)");
    options.addOption("r", "routerulesfile", true,
      "file that has routing rules (defaults to conf/kafka-route-rules.xml");
    options.addOption("f", "kafkaproperties", true,
      "Path to properties file that has the kafka connection properties");
    options.addRequiredOption("p", "peername", true,
        "Name of hbase peer");
    options.addOption("z", "znode", true,
        "root zode to use in zookeeper (defaults to /kafkaproxy)");
    options.addOption("a", "autopeer", false,
        "Create a peer automatically to the hbase cluster");
    options.addOption("e", "enablepeer", false,
        "enable peer on startup (defaults to false)");

    LOG.info("STARTING executorService " + HRegionServer.class.getSimpleName());
    VersionInfo.logVersion();

    Configuration conf = HBaseConfiguration.create();
    CommandLine commandLine = null;

    Configuration commandLineConf = new Configuration();
    commandLineConf.clear();

    GenericOptionsParser parser = new GenericOptionsParser(commandLineConf, args);
    String[] restArgs = parser.getRemainingArgs();

    try {
      commandLine = new DefaultParser().parse(options, restArgs);
    } catch (ParseException e) {
      LOG.error("Could not parse: ", e);
      printUsageAndExit(options, -1);
    }


    String peer="";
    if (!commandLine.hasOption('p')){
      System.err.println("hbase peer id is required");
      System.exit(-1);
    } else {
      peer = commandLine.getOptionValue('p');
    }

    boolean createPeer = false;
    boolean enablePeer = false;

    if (commandLine.hasOption('a')){
      createPeer=true;
    }

    if (commandLine.hasOption("a")){
      enablePeer=true;
    }

    String rulesFile = StringUtils.defaultIfBlank(
            commandLine.getOptionValue("r"),"kafka-route-rules.xml");

    if (!new File(rulesFile).exists()){
      if (KafkaProxy.class.getClassLoader().getResource(rulesFile)!=null){
        rulesFile = KafkaProxy.class.getClassLoader().getResource(rulesFile).getFile();
      } else {
        System.err.println("Rules file " + rulesFile +
            " is invalid");
        System.exit(-1);
      }
    }

    otherProps.put(KafkaProxy.KAFKA_PROXY_RULES_FILE,rulesFile);

    if (commandLine.hasOption('f')){
      otherProps.put(KafkaProxy.KAFKA_PROXY_KAFKA_PROPERTIES,commandLine.getOptionValue('f'));
    } else if (commandLine.hasOption('b')){
      otherProps.put(KafkaProxy.KAFKA_PROXY_KAFKA_BROKERS,commandLine.getOptionValue('b'));
    } else {
      System.err.println("Kafka connection properites or brokers must be specified");
      System.exit(-1);
    }

    String zookeeperQ = conf.get("hbase.zookeeper.quorum") + ":" +
        conf.get("hbase.zookeeper.property.clientPort");

    ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(20000, 20);

    try (CuratorFramework zk = CuratorFrameworkFactory.newClient(zookeeperQ, retryPolicy);
    ) {
      zk.start();
      String rootZnode = "/kafkaproxy";
      setupZookeeperZnodes(zk,rootZnode,peer);
      checkForOrCreateReplicationPeer(conf,zk,rootZnode,peer,createPeer,enablePeer);
    }

    @SuppressWarnings("unchecked")
    Class<? extends HRegionServer> regionServerClass = (Class<? extends HRegionServer>) conf
        .getClass(HConstants.REGION_SERVER_IMPL, HRegionServer.class);

    List<String> allArgs = DEFAULT_PROPERTIES.keySet().stream()
        .map((argKey)->("-D"+argKey+"="+ DEFAULT_PROPERTIES.get(argKey)))
        .collect(Collectors.toList());

    allArgs.addAll(CAN_OVERRIDE_DEFAULT_PROPERTIES.keySet().stream()
            .filter((argKey)->commandLineConf.get(argKey,"").equalsIgnoreCase(""))
            .map((argKey)->("-D"+argKey+"="+ CAN_OVERRIDE_DEFAULT_PROPERTIES.get(argKey)))
            .collect(Collectors.toList()));

    for (Map.Entry<String,String> k : commandLineConf){
      allArgs.add("-D"+k.getKey()+"="+k.getValue());
    }

    otherProps.keySet().stream()
        .map((argKey)->("-D"+argKey+"="+ otherProps.get(argKey)))
        .forEach((item)->allArgs.add(item));

    Arrays.stream(restArgs)
        .filter((arg)->(arg.startsWith("-D")||arg.equals("start")))
        .forEach((arg)->allArgs.add(arg));

    // is start there?
    if (allArgs.stream()
            .filter((arg)->arg.equalsIgnoreCase("start")).count() < 1){
      allArgs.add("start");
    }

    String[] newArgs=new String[allArgs.size()];
    allArgs.toArray(newArgs);

    new HRegionServerCommandLine(regionServerClass).doMain(newArgs);
  }


  /**
   * Set up the needed znodes under the rootZnode
   * @param zk CuratorFramework framework instance
   * @param rootZnode Root znode
   * @throws Exception If an error occurs
   */
  public static void setupZookeeperZnodes(CuratorFramework zk, String rootZnode,String peer)
          throws Exception {
    // always gives the same uuid for the same name
    UUID uuid = UUID.nameUUIDFromBytes(Bytes.toBytes(peer));
    String newValue = uuid.toString();
    byte []uuidBytes = Bytes.toBytes(newValue);
    String idPath=rootZnode+"/hbaseid";
    if (zk.checkExists().forPath(idPath) == null) {
      zk.create().forPath(rootZnode);
      zk.create().forPath(rootZnode +"/hbaseid",uuidBytes);
    } else {
      // If the znode is there already make sure it has the
      // expected value for the peer name.
      byte[] znodeBytes = zk.getData().forPath(idPath).clone();
      if (!Bytes.equals(znodeBytes,uuidBytes)){
        String oldValue = Bytes.toString(znodeBytes);
        LOG.warn("znode "+idPath+" has unexpected value "+ oldValue
            +" expecting " + newValue + " "
            + " (did the peer name for the proxy change?) "
            + "Updating value");
        zk.setData().forPath(idPath, uuidBytes);
      }
    }
  }

  /**
   * Poll for the configured peer or create it if it does not exist
   *  (controlled by createIfMissing)
   * @param hbaseConf the hbase configuratoin
   * @param zk CuratorFramework object
   * @param basePath base znode.
   * @param peerName id if the peer to check for/create
   * @param enablePeer if the peer is detected or created, enable it.
   * @param createIfMissing if the peer doesn't exist, create it and peer to it.
   */
  public static void checkForOrCreateReplicationPeer(Configuration hbaseConf,
                        CuratorFramework zk,
                        String basePath,
                        String peerName, boolean createIfMissing,
                        boolean enablePeer) {
    try (Connection conn = ConnectionFactory.createConnection(hbaseConf);
       Admin admin = conn.getAdmin()) {

      boolean peerThere = false;

      while (!peerThere) {
        try {
          ReplicationPeerConfig peerConfig = admin.getReplicationPeerConfig(peerName);
          if (peerConfig !=null) {
            peerThere=true;
          }
        } catch (ReplicationPeerNotFoundException e) {
          if (createIfMissing) {
            ReplicationPeerConfigBuilder builder = ReplicationPeerConfig.newBuilder();
            // get the current cluster's ZK config
            String zookeeperQ = hbaseConf.get("hbase.zookeeper.quorum") +
                ":" +
                hbaseConf.get("hbase.zookeeper.property.clientPort");
            String znodePath = zookeeperQ + ":"+basePath;
            ReplicationPeerConfig rconf = builder.setClusterKey(znodePath).build();
            admin.addReplicationPeer(peerName, rconf);
            peerThere = true;
          }
        }

        if (peerThere) {
          if (enablePeer){
            LOG.info("enable peer,{}", peerName);
            List<ReplicationPeerDescription> peers = admin.listReplicationPeers().stream()
                    .filter(peer -> peer.getPeerId().equals(peerName))
                    .filter(peer -> !peer.isEnabled())
                    .collect(Collectors.toList());
            if (!peers.isEmpty()){
              admin.enableReplicationPeer(peerName);
            }
          }
          break;
        } else {
          LOG.info("peer "+
                  peerName+" not found, service will not completely start until the peer exists");
        }
        Thread.sleep(5000);
      }

      LOG.info("found replication peer " + peerName);

    } catch (Exception e) {
      LOG.error("Exception running proxy ",e);
    }
  }
}
