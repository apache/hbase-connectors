<!---
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Apache HBase&trade; Kafka Proxy

Welcome to the HBase kafka proxy. The purpose of this proxy is to act as a _fake peer_.
It receives replication events from a peer cluster and applies a set of rules (stored in
a _kafka-route-rules.xml_ file) to determine if the event should be forwarded to a 
kafka topic. If the mutation matches a rule, the mutation is converted to an avro object
and the item is placed into the topic.

The service sets up a bare bones RegionServer, so it will use the values in any
_hbase-site.xml_ it finds on CLASSPATH.  If you wish to override those values,
pass them as properties on the command line; i.e `-Dkey=value`.

## Usage

1. Make sure the `hbase` command is in your path. The proxy runs `hbase classpath` to find hbase libraries.
2. Create any topics in your kafka broker that you wish to use.
3. Set up _kafka-route-rules.xml_.  This file controls how the mutations are routed.  There are two kinds of rules: _route_ and _drop_.
 * _drop_: any mutation that matches this rule will be dropped.
 * _route_: any mutation that matches this rule will be routed to the configured topic.

Each rule has the following parameters:

* table
* columnFamily
* qualifier

The qualifier parameter can contain simple wildcard expressions (start and end only).

### Examples

```
<rules>
 <rule action="route" table="default:mytable" topic="foo" />
</rules>
```

This causes all mutations to `default:mytable` to be routed to the kafka topic `foo`.

```
<rules>
 <rule action="route" table="default:mytable" columnFamily="mycf" qualifier="myqualifier" topic="mykafkatopic"/>
</rules>
```

This will cause all mutations to `default:mytable` columnFamily `mycf` and qualifier `myqualifier`
to be routed to `mykafkatopic`.

```
<rules>
 <rule action="drop" table="default:mytable" columnFamily="mycf" qualifier="secret*"/>
 <rule action="route" table="default:mytable" columnFamily="mycf" topic="mykafkatopic"/>
</rules>
```

This combination will route all mutations from `default:mytable` columnFamily `mycf` to
`mykafkatopic` unless they start with `'secret'`. Items matching that rule will be dropped.
The way the rule is written, all other mutations for column family `mycf` will be routed
to the `mykafka` topic.

### Setting up HBase

1. Enable replication `hbase.replication=true`.
2. Enable table replication in shell. Table name is `table` and column family is `cf` in the
following example:
```
disable 'table'
alter 'table', {NAME => 'cf', REPLICATION_SCOPE => 1}
enable 'table'
```

## Service Arguments

```
--kafkabrokers    (or -b) <kafka brokers (comma delmited)>
--routerulesfile  (or -r) <file with rules to route to kafka (defaults to kafka-route-rules.xml)>
--kafkaproperties (or -f) <Path to properties file that has the kafka connection properties>
--peername        (or -p) name of hbase peer to use (defaults to hbasekafka)
--znode           (or -z) root znode (defaults to /kafkaproxy)
--enablepeer      (or -e) enable peer on startup (defaults to false)]
--auto            (or -a) auto create peer
```

## Starting the Service

* Make sure the `hbase` command is in your path.
* By default, the service looks for _kafka-route-rules.xml_ in the conf directory. You can
specify a different file or location with the `-r` argument.

For example:

```
$ bin/hbase-connectors-daemon.sh start kafkaproxy -a -e -p <peer> -b <kafka.address>:<kafka.port>
```

This:
* Starts the kafka proxy.
* Passes `-a` so proxy will create the replication peer specified by `-p` if it does not exist
(not required, but it saves some busy work).
* Enables the peer (`-e`) when the service starts (not required, you can manually enable the
peer in the shell).
* The proxy will use _conf/kafka-route-rules.xml_ by default.

## Notes

1. The proxy will connect to the zookeeper in `hbase-site.xml` by default.  You can override this
by passing `-Dhbase.zookeeper.quorum`.
2. Route rules only support unicode characters.

### Message Format

Messages are in avro format, this is the schema:

```{"namespace": "org.apache.hadoop.hbase.kafka",
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
```

Any language that supports Avro should be able to consume the messages off the topic.

## Testing Utility

A utility is included to test the routing rules.

```
$ bin/hbase-connectors-daemon.sh start kafkaproxytest -k <kafka.broker> -t <topic to listen to>
```

The messages will be dumped in string format under `logs/`.

## TODO
1. Some properties passed into the region server are hard-coded.
2. The avro objects should be generic.
3. Allow rules to be refreshed without a restart.
4. Get this tested on a secure (TLS & Kerberos) enabled cluster.
