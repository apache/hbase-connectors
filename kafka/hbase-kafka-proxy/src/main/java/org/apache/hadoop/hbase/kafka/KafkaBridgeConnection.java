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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.BufferedMutator;
import org.apache.hadoop.hbase.client.BufferedMutatorParams;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.RegionLocator;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableBuilder;
import org.apache.hadoop.hbase.security.User;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.yetus.audience.InterfaceAudience;

/**
 * a alternative implementation of a connection object that forwards the mutations to a kafka queue
 * depending on the routing rules (see kafka-route-rules.xml).
 * */
@InterfaceAudience.Private
public class KafkaBridgeConnection implements Connection {
  private final Configuration conf;
  private volatile boolean closed = false;
  private TopicRoutingRules routingRules;
  private Producer<byte[],byte[]> producer;
  private DatumWriter<HBaseKafkaEvent> avroWriter =
      new SpecificDatumWriter<>(HBaseKafkaEvent.getClassSchema());


    /**
     * Public constructor
     * @param conf hbase configuration
     * @param pool executor pool
     * @param user user who requested connection
     * @throws IOException on error
     */
  public KafkaBridgeConnection(Configuration conf,
                               ExecutorService pool,
                               User user) throws IOException {
    this.conf = conf;
    setupRules();
    startKafkaConnection();
  }

  /**
   * for testing.
   * @param conf hbase configuration
   * @param routingRules a set of routing rules
   * @param producer a kafka producer
   */
  public KafkaBridgeConnection(Configuration conf, TopicRoutingRules routingRules,
                               Producer<byte[],byte[]> producer) {
    this.conf = conf;
    this.producer = producer;
    this.routingRules = routingRules;
  }

  private void setupRules() throws IOException {
    String file = this.conf.get(KafkaProxy.KAFKA_PROXY_RULES_FILE);
    routingRules = new TopicRoutingRules();
    try (FileInputStream fin = new FileInputStream(file);){
      routingRules.parseRules(fin);
    }
  }

  private void startKafkaConnection() throws IOException {
    Properties configProperties = new Properties();

    String kafkaPropsFile = conf.get(KafkaProxy.KAFKA_PROXY_KAFKA_PROPERTIES,"");
    if (!StringUtils.isEmpty(kafkaPropsFile)){
      try (FileInputStream fs = new java.io.FileInputStream(
          new File(kafkaPropsFile))){
        configProperties.load(fs);
      }
    } else {
      String kafkaServers =conf.get(KafkaProxy.KAFKA_PROXY_KAFKA_BROKERS);
      configProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaServers);
    }

    configProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.ByteArraySerializer");
    configProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.ByteArraySerializer");
    this.producer = new KafkaProducer<byte[], byte[]>(configProperties);
  }



  @Override
  public void abort(String why, Throwable e) {}

  @Override
  public boolean isAborted() {
    return false;
  }

  @Override
  public Configuration getConfiguration() {
    return this.conf;
  }

  @Override
  public BufferedMutator getBufferedMutator(TableName tableName) throws IOException {
    return null;
  }

  @Override
  public BufferedMutator getBufferedMutator(BufferedMutatorParams params) throws IOException {
    return null;
  }

  @Override
  public RegionLocator getRegionLocator(TableName tableName) throws IOException {
    return null;
  }

  /* Without @Override, we can also compile it against HBase 2.1. */
  /* @Override */
  public void clearRegionLocationCache() {
  }

  @Override
  public Admin getAdmin() throws IOException {
    return null;
  }

  @Override
  public void close() throws IOException {
    if (!this.closed) {
      this.closed = true;
      this.producer.close();
    }
  }

  @Override
  public boolean isClosed() {
    return this.closed;
  }

  @Override
  public TableBuilder getTableBuilder(final TableName tn, ExecutorService pool) {
    if (isClosed()) {
      throw new RuntimeException("KafkaBridgeConnection is closed.");
    }
    final Configuration passedInConfiguration = getConfiguration();
    return new TableBuilder() {
      @Override
      public TableBuilder setOperationTimeout(int timeout) {
        return null;
      }

      @Override
      public TableBuilder setRpcTimeout(int timeout) {
        return null;
      }

      @Override
      public TableBuilder setReadRpcTimeout(int timeout) {
        return null;
      }

      @Override
      public TableBuilder setWriteRpcTimeout(int timeout) {
        return null;
      }

      @Override
      public Table build() {
        return new KafkaTableForBridge(tn,passedInConfiguration,routingRules,producer,avroWriter) ;
      }
    };
  }

}
