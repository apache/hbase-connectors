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

# HBase  connector-1.0.0 Release Notes

These release notes cover new developer and user-facing incompatibilities, important issues, features, and major improvements.


---

* [HBASE-13992](https://issues.apache.org/jira/browse/HBASE-13992) | *Major* | **Integrate SparkOnHBase into HBase**

This release includes initial support for running Spark against HBase with a richer feature set than was previously possible with MapReduce bindings:

\* Support for Spark and Spark Streaming against Spark 2.1.1
\* RDD/DStream formation from scan operations
\* convenience methods for interacting with HBase from an HBase backed RDD / DStream instance
\* examples in both the Spark Java API and Spark Scala API
\* support for running against a secure HBase cluster


---

* [HBASE-14849](https://issues.apache.org/jira/browse/HBASE-14849) | *Major* | **Add option to set block cache to false on SparkSQL executions**

For user configurable parameters for HBase datasources. Please refer to org.apache.hadoop.hbase.spark.datasources.HBaseSparkConf for details.

User can either set them in SparkConf, which will take effect globally, or configure it per table, which will overwrite the value set in SparkConf. If not set, the default value will take effect.

Currently three parameters are supported.
1. spark.hbase.blockcache.enable for blockcache enable/disable. Default is enable,  but note that this potentially may slow down the system.
2. spark.hbase.cacheSize for cache size when performing HBase table scan. Default value is 1000
3. spark.hbase.batchNum for the batch number when performing HBase table scan. Default value is 1000.


---

* [HBASE-15184](https://issues.apache.org/jira/browse/HBASE-15184) | *Critical* | **SparkSQL Scan operation doesn't work on kerberos cluster**

Before this patch, users of the spark HBaseContext would fail due to lack of  privilege exceptions.

Note:
\* It is preferred to have spark in spark-on-yarn mode if Kerberos is used.
\* This is orthogonal to issues with a kerberized spark cluster via InputFormats.


---

* [HBASE-15572](https://issues.apache.org/jira/browse/HBASE-15572) | *Major* | **Adding optional timestamp semantics to HBase-Spark**

Right now the timestamp is always latest. With this patch, users can select timestamps they want.
In this patch, 4 parameters, "timestamp", "minTimestamp", "maxiTimestamp" and "maxVersions" are added to HBaseSparkConf. Users can select a timestamp, they can also select a time range with minimum timestamp and maximum timestamp.


---

* [HBASE-17574](https://issues.apache.org/jira/browse/HBASE-17574) | *Major* | **Clean up how to run tests under hbase-spark module**

Run test under root dir or hbase-spark dir
1. mvn test //run all small and medium java tests, and all scala tests
2. mvn test -P skipSparkTests //skip all scala and java tests in hbase-spark
3. mvn test -P runAllTests //run all tests, including scala and all java test even the large test

Run specified test case, since we have two plugins, we need specify both java and scala.
When only test scala or jave test case, disable the other one use -Dxx=None as follow:
1. mvn test -Dtest=TestJavaHBaseContext -DwildcardSuites=None // java unit test
2. mvn test -Dtest=None -DwildcardSuites=org.apache.hadoop.hbase.spark.BulkLoadSuite //scala unit test, only support full name in scalatest plugin


---

* [HBASE-17933](https://issues.apache.org/jira/browse/HBASE-17933) | *Major* | **[hbase-spark]  Support Java api for bulkload**

<!-- markdown -->
The integration module for Apache Spark now includes Java-friendly equivalents for the `bulkLoad` and `bulkLoadThinRows` methods in `JavaHBaseContext`.


---

* [HBASE-18175](https://issues.apache.org/jira/browse/HBASE-18175) | *Critical* | **Add hbase-spark integration test into hbase-spark-it**

<!-- markdown -->
HBase now ships with an integration test for our integration with Apache Spark.

You can run this test on a cluster by using an equivalent to the below, e.g. if the version of HBase is 2.0.0-alpha-2

```
spark-submit --class org.apache.hadoop.hbase.spark.IntegrationTestSparkBulkLoad HBASE_HOME/lib/hbase-spark-it-2.0.0-alpha-2-tests.jar -Dhbase.spark.bulkload.chainlength=500000 -m slowDeterministic
```


---

* [HBASE-16179](https://issues.apache.org/jira/browse/HBASE-16179) | *Critical* | **Fix compilation errors when building hbase-spark against Spark 2.0**

As of this JIRA, Spark version is upgraded from 1.6 to 2.1.1


---

* [HBASE-21002](https://issues.apache.org/jira/browse/HBASE-21002) | *Minor* | **Create assembly and scripts to start Kafka Proxy**

Adds a kafka proxy that appears to hbase as a replication peer. Use to tee table edits to kafka. Has mechanism for dropping/routing updates. See https://github.com/apache/hbase-connectors/tree/master/kafka for documentation.


---

* [HBASE-21434](https://issues.apache.org/jira/browse/HBASE-21434) | *Major* | **[hbase-connectors] Cleanup of kafka dependencies; clarify hadoop version**

Cleaned up kafka submodule dependencies. Added used dependencies to pom and removed the unused. Depends explicitly on hadoop2. No messing w/ hadoop3 versions.


---

* [HBASE-21446](https://issues.apache.org/jira/browse/HBASE-21446) | *Major* | **[hbase-connectors] Update spark and scala versions; add some doc on how to generate artifacts with different versions**

Updates our hbase-spark integration so defaults spark 2.4.0 (October 2018) from 2.1.1 and Scala 2.11.12 (from 2.11.8).


---

* [HBASE-15320](https://issues.apache.org/jira/browse/HBASE-15320) | *Major* | **HBase connector for Kafka Connect**

This commit adds a kafka connector. The connectors acts as a replication peer and sends modifications in HBase to kafka.

For further information, please refer to kafka/README.md.


---

* [HBASE-14789](https://issues.apache.org/jira/browse/HBASE-14789) | *Major* | **Enhance the current spark-hbase connector**

New features in hbase-spark:
\* native type support (short, int, long, float, double),
\* support for Dataframe writes,
\* avro support,
\* catalog can be defined in json.

