Hbase Kafka Proxy

Welcome to the hbase kafka proxy.  The purpose of this proxy is to act as a 'fake peer'.  It
receives replication events from it's peer and applies a set of rules (stored in
kafka-route-rules.xml) to determine if the event should be forwared to a topic.  If the
mutation matches one of the rules, the mutation is converted to an avro object and the
item is placed into the topic.

The service sets up a bare bones region server, so it will use the values in hbase-site.xml.  If
you wish to override those values, pass them as -Dkey=value.

To Use:

1. Make sure the hbase command is in your path.  The proxy uses the 'hbase classpath' command to
find the hbase libraries.

2. Create any topics in your kafka broker that you wish to use.

3. set up kafka-route-rules.xml.  This file controls how the mutations are routed.  There are
two kinds of rules: route and drop.  drop: any mutation that matches this rule will be dropped.
route: any mutation that matches this rule will be routed to the configured topic.

Each rule has the following parameters:
- table
- columnFamily
- qualifier

The qualifier parameter can contain simple wildcard expressions (start and end only).

Examples

<rules>
	<rule action="route" table="default:mytable" topic="foo" />
</rules>


This causes all mutations done to default:mytable to be routed to kafka topic 'foo'


<rules>
	<rule action="route" table="default:mytable" columnFamily="mycf" qualifier="myqualifier"
	topic="mykafkatopic"/>
</rules>

This will cause all mutations from default:mytable columnFamily mycf and qualifier myqualifier
to be routed to mykafkatopic.


<rules>
	<rule action="drop" table="default:mytable" columnFamily="mycf" qualifier="secret*"/>
	<rule action="route" table="default:mytable" columnFamily="mycf" topic="mykafkatopic"/>
</rules>

This combination will route all mutations from default:mytable columnFamily mycf to mykafkatopic
unless they start with 'secret'.  Items matching that rule will be dropped.  The way the rule is
written, all other mutations for column family mycf will be routed to the 'mykafka' topic.

4. Service arguments

--kafkabrokers (or -b) <kafka brokers (comma delmited)>
--routerulesfile (or -r) <file with rules to route to kafka (defaults to kafka-route-rules.xml)>
--kafkaproperties (or -f) <Path to properties file that has the kafka connection properties>
--peername (or -p) name of hbase peer to use (defaults to hbasekafka)
--znode (or -z) root znode (defaults to /kafkaproxy)
--enablepeer (or -e) enable peer on startup (defaults to false)]
--auto (or -a) auto create peer


5. start the service.
   - make sure the hbase command is in your path
   - ny default, the service looks for route-rules.xml in the conf directory, you can specify a
     differeent file or location with the -r argument

bin/hbase-connectors-daemon.sh start kafkaproxy -a -e -p wootman -b localhost:9092 -r ~/kafka-route-rules.xml

this:
- starts the kafka proxy
- passes the -a.  The proxy will create the replication peer specified by -p if it does not exist
  (not required, but it savecs some busy work).
- enables the peer (-e) the proxy will enable the peer when the service starts (not required, you can
  manually enable the peer in the hbase shell)


Notes:
1. The proxy will connect to the zookeeper in hbase-site.xml by default.  You can override this by
   passing -Dhbase.zookeeper.quorum

 bin/hbase-connectors-daemon.sh start kafkaproxy -Dhbase.zookeeper.quorum=localhost:1234 ..... other args ....

2. route rules only support unicode characters.
3. I do not have access to a secured hadoop clsuter to test this on.

Message format

Messages are in avro format, this is the schema:

{"namespace": "org.apache.hadoop.hbase.kafka",
 "type": "record",
 "name": "HBaseKafkaEvent",
 "fields": [
    {"name": "key", "type": "bytes"},
    {"name": "timestamp",  "type": "long" },
    {"name": "delete",  "type": "boolean" },
    {"name": "value", "type": "bytes"},
    {"name": "qualifier", "type": "bytes"},
    {"name": "family", "type": "bytes"},
    {"name": "table", "type": "bytes"}
 ]
}

Any language that supports Avro should be able to consume the messages off the topic.


Testing Utility

A utility is included to test the routing rules.

bin/hbase-connectors-daemon.sh start kafkaproxytest -k <kafka.broker> -t <topic to listen to>

the messages will be dumped in string format under logs/

TODO:
1. Some properties passed into the region server are hard-coded.
2. The avro objects should be generic
3. Allow rules to be refreshed without a restart
4. Get this tested on a secure (TLS & Kerberos) enabled cluster.