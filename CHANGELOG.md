
<!---
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
-->
# HBASE Changelog

## Release hbase-connectors-1.0.1 - Unreleased (as of 2023-10-18)



### NEW FEATURES:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-28152](https://issues.apache.org/jira/browse/HBASE-28152) | Replace scala.util.parsing.json with org.json4s.jackson which used in Spark too |  Major | spark |
| [HBASE-28137](https://issues.apache.org/jira/browse/HBASE-28137) | Add scala-parser-combinators dependency to connectors for Spark 3.4 |  Major | spark |
| [HBASE-27397](https://issues.apache.org/jira/browse/HBASE-27397) | Spark-hbase support for 'startWith' predicate. |  Minor | hbase-connectors |


### IMPROVEMENTS:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-27625](https://issues.apache.org/jira/browse/HBASE-27625) | Bump commons-lang3 to 3.12.0, surefire to 3.0.0 and dependency manage reload4j to 1.2.25 to support jdk11 |  Major | hbase-connectors, spark |
| [HBASE-27705](https://issues.apache.org/jira/browse/HBASE-27705) | Respect SparkContext hadoop configuration |  Major | spark |
| [HBASE-27639](https://issues.apache.org/jira/browse/HBASE-27639) | Support hbase-connectors compilation with HBase 2.5.3, Hadoop 3.2.4 and Spark 3.2.3 |  Major | hbase-connectors, spark |
| [HBASE-26534](https://issues.apache.org/jira/browse/HBASE-26534) | Update dependencies in hbase-connectors: HBase version to 2.4.8, and make Hadoop 3 and Spark 3 defaults |  Minor | hadoop3, hbase-connectors, spark |
| [HBASE-25684](https://issues.apache.org/jira/browse/HBASE-25684) | Dependency manage log4j in hbase-connectors |  Minor | hbase-connectors |
| [HBASE-25326](https://issues.apache.org/jira/browse/HBASE-25326) | Allow hbase-connector to be used with Apache Spark 3.0 |  Minor | . |
| [HBASE-23606](https://issues.apache.org/jira/browse/HBASE-23606) | Remove external deprecations in hbase-connectors |  Minor | . |
| [HBASE-24230](https://issues.apache.org/jira/browse/HBASE-24230) | Support user-defined version timestamp when bulk load data |  Minor | hbase-connectors |
| [HBASE-23608](https://issues.apache.org/jira/browse/HBASE-23608) | Remove redundant groupId from spark module in hbase-connectors |  Trivial | . |
| [HBASE-23592](https://issues.apache.org/jira/browse/HBASE-23592) | Refactor tests in hbase-kafka-proxy in hbase-connectors |  Trivial | . |
| [HBASE-24110](https://issues.apache.org/jira/browse/HBASE-24110) | Move to Apache parent POM version 23 for connectors |  Minor | hbase-connectors |
| [HBASE-23603](https://issues.apache.org/jira/browse/HBASE-23603) | Update Apache POM to version 21 for hbase-connectors |  Trivial | . |
| [HBASE-23607](https://issues.apache.org/jira/browse/HBASE-23607) | Update Maven plugins in hbase-connectors |  Minor | . |
| [HBASE-23579](https://issues.apache.org/jira/browse/HBASE-23579) | Fix Checkstyle errors in hbase-connectors |  Minor | . |
| [HBASE-23586](https://issues.apache.org/jira/browse/HBASE-23586) | Use StandardCharsets instead of String in TestQualifierMatching in hbase-connectors |  Trivial | . |
| [HBASE-23580](https://issues.apache.org/jira/browse/HBASE-23580) | Refactor TestRouteRules in hbase-connectors |  Trivial | . |
| [HBASE-23576](https://issues.apache.org/jira/browse/HBASE-23576) | Bump Checkstyle from 8.11 to 8.18 in hbase-connectors |  Minor | . |
| [HBASE-22817](https://issues.apache.org/jira/browse/HBASE-22817) | Use hbase-shaded dependencies in hbase-spark |  Major | hbase-connectors |
| [HBASE-23075](https://issues.apache.org/jira/browse/HBASE-23075) | Upgrade jackson to version 2.9.10 due to CVE-2019-16335 and CVE-2019-14540 |  Major | dependencies, hbase-connectors, REST, security |


### BUG FIXES:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-26863](https://issues.apache.org/jira/browse/HBASE-26863) | Rowkey pushdown does not work with complex conditions |  Major | hbase-connectors |
| [HBASE-27488](https://issues.apache.org/jira/browse/HBASE-27488) | [hbase-connectors] Duplicate result when searching HBase by Spark |  Major | hbase-connectors |
| [HBASE-27656](https://issues.apache.org/jira/browse/HBASE-27656) | Make sure the close method of the  SmartSonnection is called |  Major | spark |
| [HBASE-27176](https://issues.apache.org/jira/browse/HBASE-27176) | [hbase-connectors] Fail to build hbase-connectors because of checkstyle error |  Major | build, hbase-connectors |
| [HBASE-27801](https://issues.apache.org/jira/browse/HBASE-27801) | Remove redundant avro.version property from Kafka connector |  Minor | hbase-connectors, kafka |
| [HBASE-27630](https://issues.apache.org/jira/browse/HBASE-27630) | hbase-spark bulkload stage directory limited to hdfs only |  Major | spark |
| [HBASE-27624](https://issues.apache.org/jira/browse/HBASE-27624) | Cannot Specify Namespace via the hbase.table Option in Spark Connector |  Major | hbase-connectors, spark |
| [HBASE-22338](https://issues.apache.org/jira/browse/HBASE-22338) | LICENSE file only contains Apache 2.0 |  Critical | hbase-connectors |
| [HBASE-26211](https://issues.apache.org/jira/browse/HBASE-26211) | [hbase-connectors] Pushdown filters in Spark do not work correctly with long types |  Major | hbase-connectors |
| [HBASE-25236](https://issues.apache.org/jira/browse/HBASE-25236) | [hbase-connectors] Run package phase on spark modules |  Major | hbase-connectors |
| [HBASE-24276](https://issues.apache.org/jira/browse/HBASE-24276) | hbase spark connector doesn't support writing to table not in default namespace |  Major | hbase-connectors, spark |
| [HBASE-24088](https://issues.apache.org/jira/browse/HBASE-24088) | Solve the ambiguous reference for scala 2.12 |  Minor | hbase-connectors |
| [HBASE-23295](https://issues.apache.org/jira/browse/HBASE-23295) | hbase-connectors HBaseContext should use most recent delegation token |  Major | hbase-connectors |
| [HBASE-23351](https://issues.apache.org/jira/browse/HBASE-23351) | Updating hbase version to 2.2.2 |  Major | hbase-connectors |
| [HBASE-23348](https://issues.apache.org/jira/browse/HBASE-23348) | Spark's createTable method throws an exception while the table is being split |  Major | hbase-connectors |
| [HBASE-23346](https://issues.apache.org/jira/browse/HBASE-23346) | Import ReturnCode in SparkSQLPushDownFilter |  Major | hbase-connectors |
| [HBASE-23327](https://issues.apache.org/jira/browse/HBASE-23327) | Add missing maven functions for hb\_maven |  Critical | hbase-connectors |
| [HBASE-23059](https://issues.apache.org/jira/browse/HBASE-23059) | Run mvn install for root in precommit |  Major | hbase-connectors |
| [HBASE-22711](https://issues.apache.org/jira/browse/HBASE-22711) | Spark connector doesn't use the given mapping when inserting data |  Major | hbase-connectors |
| [HBASE-22674](https://issues.apache.org/jira/browse/HBASE-22674) | precommit docker image installs JRE over JDK (multiple repos) |  Critical | build, hbase-connectors |
| [HBASE-22336](https://issues.apache.org/jira/browse/HBASE-22336) | Add CHANGELOG, README and RELEASENOTES to binary tarball |  Critical | hbase-connectors |
| [HBASE-22329](https://issues.apache.org/jira/browse/HBASE-22329) | Fix for warning The parameter forkMode is deprecated since version in hbase-spark-it |  Minor | hbase-connectors |
| [HBASE-22320](https://issues.apache.org/jira/browse/HBASE-22320) | hbase-connectors personality skips non-scaladoc tests |  Critical | . |
| [HBASE-22319](https://issues.apache.org/jira/browse/HBASE-22319) | Fix for warning The assembly descriptor contains a filesystem-root relative reference |  Minor | hbase-connectors |


### SUB-TASKS:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-28054](https://issues.apache.org/jira/browse/HBASE-28054) | [hbase-connectors] Add spotless in hbase-connectors pre commit check |  Major | build, community, hbase-connectors, jenkins |
| [HBASE-28006](https://issues.apache.org/jira/browse/HBASE-28006) | [hbase-connectors] Run spotless:apply on code base |  Major | build, hbase-connectors |
| [HBASE-27178](https://issues.apache.org/jira/browse/HBASE-27178) | [hbase-connectors] Add spotless plugin to format code (including scala code) |  Major | build, hbase-connectors |
| [HBASE-25136](https://issues.apache.org/jira/browse/HBASE-25136) | Migrate HBase-Connectors-PreCommit jenkins job from Hadoop to hbase |  Major | hbase-connectors, jenkins |


### OTHER:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-27114](https://issues.apache.org/jira/browse/HBASE-27114) | Upgrade scalatest maven plugin for thread-safety |  Major | build, spark |
| [HBASE-27883](https://issues.apache.org/jira/browse/HBASE-27883) | [hbase-connectors] Use log4j2 instead of log4j for logging |  Blocker | hbase-connectors |
| [HBASE-27679](https://issues.apache.org/jira/browse/HBASE-27679) | Bump junit to 4.13.2 in hbase-connectors |  Major | hbase-connectors |
| [HBASE-27680](https://issues.apache.org/jira/browse/HBASE-27680) | Bump hbase, hbase-thirdparty, hadoop and spark for hbase-connectors |  Major | hbase-connectors |
| [HBASE-27678](https://issues.apache.org/jira/browse/HBASE-27678) | Update checkstyle in hbase-connectors |  Major | hbase-connectors |
| [HBASE-27285](https://issues.apache.org/jira/browse/HBASE-27285) | Fix sonar report paths |  Minor | hbase-connectors |
| [HBASE-27272](https://issues.apache.org/jira/browse/HBASE-27272) | Enable code coverage reporting to SonarQube in hbase-connectors |  Minor | hbase-connectors |
| [HBASE-26664](https://issues.apache.org/jira/browse/HBASE-26664) | HBASE-26664 hbase-connector upgrades extra-enforcer-rules to 1.5.1 |  Major | hbase-connectors |
| [HBASE-26334](https://issues.apache.org/jira/browse/HBASE-26334) | Upgrade commons-io to 2.11.0 in hbase-connectors |  Major | hbase-connectors |
| [HBASE-26314](https://issues.apache.org/jira/browse/HBASE-26314) | Upgrade commons-io to 2.8.0 in hbase-connectors |  Major | hbase-connectors |
| [HBASE-25579](https://issues.apache.org/jira/browse/HBASE-25579) | HBase Connectors pom should include nexus staging repo management |  Major | community, hbase-connectors |
| [HBASE-25479](https://issues.apache.org/jira/browse/HBASE-25479) | [connectors] Purge use of VisibleForTesting |  Major | hbase-connectors |
| [HBASE-25388](https://issues.apache.org/jira/browse/HBASE-25388) | Replacing Producer implementation with an extension of MockProducer on testing side in hbase-connectors |  Major | hbase-connectors |
| [HBASE-24883](https://issues.apache.org/jira/browse/HBASE-24883) | Migrate hbase-connectors testing to ci-hadoop |  Major | build, hbase-connectors |
| [HBASE-24261](https://issues.apache.org/jira/browse/HBASE-24261) | Redo all of our github notification integrations on new ASF infra feature |  Major | community, hbase-connectors |
| [HBASE-23565](https://issues.apache.org/jira/browse/HBASE-23565) | Execute tests in hbase-connectors precommit |  Critical | hbase-connectors |
| [HBASE-23032](https://issues.apache.org/jira/browse/HBASE-23032) | Upgrade to Curator 4.2.0 |  Major | . |
| [HBASE-22599](https://issues.apache.org/jira/browse/HBASE-22599) | Let hbase-connectors compile against HBase 2.2.0 |  Major | hbase-connectors |
| [HBASE-22698](https://issues.apache.org/jira/browse/HBASE-22698) | [hbase-connectors] Add license header to README.md |  Major | hbase-connectors |
| [HBASE-22636](https://issues.apache.org/jira/browse/HBASE-22636) | hbase spark module README is in txt format. |  Trivial | hbase-connectors |
| [HBASE-22318](https://issues.apache.org/jira/browse/HBASE-22318) | Fix for warning The POM for org.glassfish:javax.el:jar is missing |  Minor | hbase-connectors |


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
