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

# Apache HBase&trade; Spark Connector

## Spark, Scala and Configurable Options

To generate an artifact for a different [Spark version](https://mvnrepository.com/artifact/org.apache.spark/spark-core) and/or [Scala version](https://www.scala-lang.org/download/all.html),
[Hadoop version](https://mvnrepository.com/artifact/org.apache.hadoop/hadoop-core), or [HBase version](https://mvnrepository.com/artifact/org.apache.hbase/hbase), pass command-line options as follows (changing version numbers appropriately):

```
$ mvn -Dspark.version=3.1.2 -Dscala.version=2.12.10 -Dhadoop-three.version=3.2.0 -Dscala.binary.version=2.12 -Dhbase.version=2.4.8 clean install
```

Note: to build the connector with Spark 2.x, compile it with `-Dscala.binary.version=2.11` and use the profile `-Dhadoop.profile=2.0`

## Configuration and Installation
**Client-side** (Spark) configuration:
- The HBase configuration file `hbase-site.xml` should be made available to Spark, it can be copied to `$SPARK_CONF_DIR` (default is $SPARK_HOME/conf`)

**Server-side** (HBase region servers) configuration:
- The following jars need to be in the CLASSPATH of the HBase region servers:
  - scala-library, hbase-spark, and hbase-spark-protocol-shaded.
- The server-side configuration is needed for column filter pushdown
  - if you cannot perform the server-side configuration, consider using `.option("hbase.spark.pushdown.columnfilter", false)`
- The Scala library version must match the Scala version (2.11 or 2.12) used for compiling the connector.

