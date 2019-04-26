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

# HBase Changelog

## Release connector-1.0.0 - Unreleased (as of 2019-04-26)



### NEW FEATURES:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-13992](https://issues.apache.org/jira/browse/HBASE-13992) | Integrate SparkOnHBase into HBase |  Major | hbase-connectors, spark |
| [HBASE-14150](https://issues.apache.org/jira/browse/HBASE-14150) | Add BulkLoad functionality to HBase-Spark Module |  Major | hbase-connectors, spark |
| [HBASE-14181](https://issues.apache.org/jira/browse/HBASE-14181) | Add Spark DataFrame DataSource to HBase-Spark Module |  Minor | hbase-connectors, spark |
| [HBASE-14340](https://issues.apache.org/jira/browse/HBASE-14340) | Add second bulk load option to Spark Bulk Load to send puts as the value |  Minor | hbase-connectors, spark |
| [HBASE-14849](https://issues.apache.org/jira/browse/HBASE-14849) | Add option to set block cache to false on SparkSQL executions |  Major | hbase-connectors, spark |
| [HBASE-15572](https://issues.apache.org/jira/browse/HBASE-15572) | Adding optional timestamp semantics to HBase-Spark |  Major | hbase-connectors, spark |
| [HBASE-17933](https://issues.apache.org/jira/browse/HBASE-17933) | [hbase-spark]  Support Java api for bulkload |  Major | hbase-connectors, spark |
| [HBASE-15320](https://issues.apache.org/jira/browse/HBASE-15320) | HBase connector for Kafka Connect |  Major | hbase-connectors, Replication |


### IMPROVEMENTS:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-14515](https://issues.apache.org/jira/browse/HBASE-14515) | Allow spark module unit tests to be skipped with a profile |  Minor | build, hbase-connectors, spark |
| [HBASE-14158](https://issues.apache.org/jira/browse/HBASE-14158) | Add documentation for Initial Release for HBase-Spark Module integration |  Major | documentation, hbase-connectors, spark |
| [HBASE-14159](https://issues.apache.org/jira/browse/HBASE-14159) | Resolve warning introduced by HBase-Spark module |  Minor | build, hbase-connectors, spark |
| [HBASE-15434](https://issues.apache.org/jira/browse/HBASE-15434) | [findbugs] Exclude scala generated source and protobuf generated code in hbase-spark module |  Major | hbase-connectors, spark |
| [HBASE-16638](https://issues.apache.org/jira/browse/HBASE-16638) | Reduce the number of Connection's created in classes of hbase-spark module |  Critical | hbase-connectors, spark |
| [HBASE-16823](https://issues.apache.org/jira/browse/HBASE-16823) | Add examples in HBase Spark module |  Major | hbase-connectors, spark |
| [HBASE-17549](https://issues.apache.org/jira/browse/HBASE-17549) | HBase-Spark Module : Incorrect log at println and unwanted comment code |  Major | hbase-connectors, spark |
| [HBASE-18176](https://issues.apache.org/jira/browse/HBASE-18176) | add enforcer rule to make sure hbase-spark / scala aren't dependencies of unexpected modules |  Major | build, hbase-connectors, spark |
| [HBASE-21491](https://issues.apache.org/jira/browse/HBASE-21491) | [hbase-connectors] Edit on spark connector README |  Trivial | hbase-connectors |
| [HBASE-21841](https://issues.apache.org/jira/browse/HBASE-21841) | Allow inserting null values throw DataSource API |  Major | spark |
| [HBASE-21880](https://issues.apache.org/jira/browse/HBASE-21880) | [hbase-connectors] clean up site target |  Minor | hbase-connectors |
| [HBASE-21842](https://issues.apache.org/jira/browse/HBASE-21842) | Properly use flatten-maven-plugin in hbase-connectors |  Major | hbase-connectors |
| [HBASE-21931](https://issues.apache.org/jira/browse/HBASE-21931) | [hbase-connectors] Bump surefire version |  Major | hbase-connectors |
| [HBASE-14789](https://issues.apache.org/jira/browse/HBASE-14789) | Enhance the current spark-hbase connector |  Major | hbase-connectors, spark |


### BUG FIXES:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-14377](https://issues.apache.org/jira/browse/HBASE-14377) | JavaHBaseContextSuite not being run |  Critical | hbase-connectors, spark |
| [HBASE-14406](https://issues.apache.org/jira/browse/HBASE-14406) | The dataframe datasource filter is wrong, and will result in data loss or unexpected behavior |  Blocker | hbase-connectors, spark |
| [HBASE-15184](https://issues.apache.org/jira/browse/HBASE-15184) | SparkSQL Scan operation doesn't work on kerberos cluster |  Critical | hbase-connectors, spark |
| [HBASE-16804](https://issues.apache.org/jira/browse/HBASE-16804) | JavaHBaseContext.streamBulkGet is void but should be JavaDStream |  Major | hbase-connectors, spark |
| [HBASE-17547](https://issues.apache.org/jira/browse/HBASE-17547) | HBase-Spark Module : TableCatelog doesn't support multiple columns from Single Column family |  Major | hbase-connectors, spark |
| [HBASE-17574](https://issues.apache.org/jira/browse/HBASE-17574) | Clean up how to run tests under hbase-spark module |  Major | hbase-connectors, spark |
| [HBASE-15597](https://issues.apache.org/jira/browse/HBASE-15597) | Clean up configuration keys used in hbase-spark module |  Critical | hbase-connectors, spark |
| [HBASE-17909](https://issues.apache.org/jira/browse/HBASE-17909) | Redundant exclusion of jruby-complete in pom of hbase-spark |  Minor | hbase-connectors, spark |
| [HBASE-17546](https://issues.apache.org/jira/browse/HBASE-17546) | Incorrect syntax at HBase-Spark Module Examples |  Minor | hbase-connectors, spark |
| [HBASE-19387](https://issues.apache.org/jira/browse/HBASE-19387) | HBase-spark snappy.SnappyError on Arm64 |  Minor | hbase-connectors, spark, test |
| [HBASE-16179](https://issues.apache.org/jira/browse/HBASE-16179) | Fix compilation errors when building hbase-spark against Spark 2.0 |  Critical | hbase-connectors, spark |
| [HBASE-20124](https://issues.apache.org/jira/browse/HBASE-20124) | Make hbase-spark module work with hadoop3 |  Major | dependencies, hadoop3, hbase-connectors, spark |
| [HBASE-20177](https://issues.apache.org/jira/browse/HBASE-20177) | Fix warning: Class org.apache.hadoop.minikdc.MiniKdc not found in hbase-spark |  Minor | hbase-connectors |
| [HBASE-20375](https://issues.apache.org/jira/browse/HBASE-20375) | Remove use of getCurrentUserCredentials in hbase-spark module |  Major | hbase-connectors, spark |
| [HBASE-20880](https://issues.apache.org/jira/browse/HBASE-20880) | Fix for warning It would fail on the following input in hbase-spark |  Minor | hbase-connectors |
| [HBASE-21038](https://issues.apache.org/jira/browse/HBASE-21038) | SAXParseException when hbase.spark.use.hbasecontext=false |  Major | hbase-connectors |
| [HBASE-20175](https://issues.apache.org/jira/browse/HBASE-20175) | hbase-spark needs scala dependency convergance |  Major | dependencies, hbase-connectors, spark |
| [HBASE-21429](https://issues.apache.org/jira/browse/HBASE-21429) | [hbase-connectors] pom refactoring adding kafka dir intermediary |  Minor | hbase-connectors, kafka |
| [HBASE-21431](https://issues.apache.org/jira/browse/HBASE-21431) | [hbase-connectors] Fix build and test issues |  Blocker | hbase-connectors |
| [HBASE-21434](https://issues.apache.org/jira/browse/HBASE-21434) | [hbase-connectors] Cleanup of kafka dependencies; clarify hadoop version |  Major | hbase-connectors, kafka |
| [HBASE-21446](https://issues.apache.org/jira/browse/HBASE-21446) | [hbase-connectors] Update spark and scala versions; add some doc on how to generate artifacts with different versions |  Major | hbase-connectors, spark |
| [HBASE-21448](https://issues.apache.org/jira/browse/HBASE-21448) | [hbase-connectors] Make compile/tests pass on scala 2.10 AND 2.11 |  Major | hbase-connectors, spark |
| [HBASE-21878](https://issues.apache.org/jira/browse/HBASE-21878) | [hbase-connectors] Fix hbase-checkstyle version reference |  Critical | hbase-connectors |
| [HBASE-21923](https://issues.apache.org/jira/browse/HBASE-21923) | [hbase-connectors] Make apache-rat pass |  Critical | hbase-connectors |
| [HBASE-21450](https://issues.apache.org/jira/browse/HBASE-21450) | [documentation] Point spark doc at hbase-connectors spark |  Major | documentation, hbase-connectors, spark |


### TESTS:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-18175](https://issues.apache.org/jira/browse/HBASE-18175) | Add hbase-spark integration test into hbase-spark-it |  Critical | hbase-connectors, spark |
| [HBASE-20176](https://issues.apache.org/jira/browse/HBASE-20176) | Fix warnings about Logging import in hbase-spark test code |  Minor | hbase-connectors |


### SUB-TASKS:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-15336](https://issues.apache.org/jira/browse/HBASE-15336) | Support Dataframe writer to the spark connector |  Major | hbase-connectors, spark |
| [HBASE-15333](https://issues.apache.org/jira/browse/HBASE-15333) | [hbase-spark] Enhance dataframe filters to handle naively encoded short, integer, long, float and double |  Major | hbase-connectors, spark |
| [HBASE-15473](https://issues.apache.org/jira/browse/HBASE-15473) | Documentation for the usage of hbase dataframe user api (JSON, Avro, etc) |  Blocker | documentation, hbase-connectors, spark |
| [HBASE-19482](https://issues.apache.org/jira/browse/HBASE-19482) | Fix Checkstyle errors in hbase-spark-it |  Minor | hbase-connectors |
| [HBASE-19597](https://issues.apache.org/jira/browse/HBASE-19597) | Fix Checkstyle errors in hbase-spark |  Minor | hbase-connectors, spark |
| [HBASE-21002](https://issues.apache.org/jira/browse/HBASE-21002) | Create assembly and scripts to start Kafka Proxy |  Minor | hbase-connectors |
| [HBASE-21435](https://issues.apache.org/jira/browse/HBASE-21435) | [hbase-connectors] Cleanup of kafka dependencies; clarify hadoop version; addendum |  Minor | hbase-connectors, kafka |


### OTHER:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-14184](https://issues.apache.org/jira/browse/HBASE-14184) | Fix indention and typo in JavaHBaseContext |  Minor | hbase-connectors, spark |
| [HBASE-21022](https://issues.apache.org/jira/browse/HBASE-21022) | Review kafka-connection repo's POMs |  Major | hbase-connectors, kafka |
| [HBASE-20257](https://issues.apache.org/jira/browse/HBASE-20257) | hbase-spark should not depend on com.google.code.findbugs.jsr305 |  Minor | build, hbase-connectors, spark |
| [HBASE-21273](https://issues.apache.org/jira/browse/HBASE-21273) | Move classes out of org.apache.spark namespace |  Major | hbase-connectors, spark |
| [HBASE-21432](https://issues.apache.org/jira/browse/HBASE-21432) | [hbase-connectors] Add Apache Yetus integration for hbase-connectors repository |  Major | build, hbase-connectors |
| [HBASE-22221](https://issues.apache.org/jira/browse/HBASE-22221) | Extend kafka-proxy documentation with required hbase settings |  Major | hbase-connectors |
| [HBASE-22210](https://issues.apache.org/jira/browse/HBASE-22210) | Fix hbase-connectors-assembly to include every jar |  Major | hbase-connectors |
| [HBASE-22266](https://issues.apache.org/jira/browse/HBASE-22266) | Add yetus personality to connectors to avoid scaladoc issues |  Major | hbase-connectors |
| [HBASE-22257](https://issues.apache.org/jira/browse/HBASE-22257) | Remove json4s and jackson dependency from hbase spark connector |  Major | hbase-connectors |


