# HBase Changelog

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

# Be careful doing manual edits in this file. Do not change format
# of release header or remove the below marker. This file is generated.
# DO NOT REMOVE THIS MARKER; FOR INTERPOLATING CHANGES!-->
## Release connector-1.0.0 - Unreleased (as of 2019-04-23)



### NEW FEATURES:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-15320](https://issues.apache.org/jira/browse/HBASE-15320) | HBase connector for Kafka Connect |  Major | Replication |


### IMPROVEMENTS:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-21491](https://issues.apache.org/jira/browse/HBASE-21491) | [hbase-connectors] Edit on spark connector README |  Trivial | hbase-connectors |
| [HBASE-21841](https://issues.apache.org/jira/browse/HBASE-21841) | Allow inserting null values throw DataSource API |  Major | spark |
| [HBASE-21880](https://issues.apache.org/jira/browse/HBASE-21880) | [hbase-connectors] clean up site target |  Minor | hbase-connectors |
| [HBASE-21842](https://issues.apache.org/jira/browse/HBASE-21842) | Properly use flatten-maven-plugin in hbase-connectors |  Major | hbase-connectors |
| [HBASE-21931](https://issues.apache.org/jira/browse/HBASE-21931) | [hbase-connectors] Bump surefire version |  Major | hbase-connectors |


### BUG FIXES:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-21429](https://issues.apache.org/jira/browse/HBASE-21429) | [hbase-connectors] pom refactoring adding kafka dir intermediary |  Minor | hbase-connectors, kafka |
| [HBASE-21431](https://issues.apache.org/jira/browse/HBASE-21431) | [hbase-connectors] Fix build and test issues |  Blocker | hbase-connectors |
| [HBASE-21434](https://issues.apache.org/jira/browse/HBASE-21434) | [hbase-connectors] Cleanup of kafka dependencies; clarify hadoop version |  Major | hbase-connectors, kafka |
| [HBASE-21446](https://issues.apache.org/jira/browse/HBASE-21446) | [hbase-connectors] Update spark and scala versions; add some doc on how to generate artifacts with different versions |  Major | hbase-connectors, spark |
| [HBASE-21448](https://issues.apache.org/jira/browse/HBASE-21448) | [hbase-connectors] Make compile/tests pass on scala 2.10 AND 2.11 |  Major | hbase-connectors, spark |
| [HBASE-21878](https://issues.apache.org/jira/browse/HBASE-21878) | [hbase-connectors] Fix hbase-checkstyle version reference |  Critical | hbase-connectors |
| [HBASE-21923](https://issues.apache.org/jira/browse/HBASE-21923) | [hbase-connectors] Make apache-rat pass |  Critical | hbase-connectors |


### SUB-TASKS:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-21002](https://issues.apache.org/jira/browse/HBASE-21002) | Create assembly and scripts to start Kafka Proxy |  Minor | hbase-connectors |
| [HBASE-21435](https://issues.apache.org/jira/browse/HBASE-21435) | [hbase-connectors] Cleanup of kafka dependencies; clarify hadoop version; addendum |  Minor | hbase-connectors, kafka |


### OTHER:

| JIRA | Summary | Priority | Component |
|:---- |:---- | :--- |:---- |
| [HBASE-21432](https://issues.apache.org/jira/browse/HBASE-21432) | [hbase-connectors] Add Apache Yetus integration for hbase-connectors repository |  Major | build, hbase-connectors |
| [HBASE-22221](https://issues.apache.org/jira/browse/HBASE-22221) | Extend kafka-proxy documentation with required hbase settings |  Major | hbase-connectors |
| [HBASE-22210](https://issues.apache.org/jira/browse/HBASE-22210) | Fix hbase-connectors-assembly to include every jar |  Major | hbase-connectors |
| [HBASE-22266](https://issues.apache.org/jira/browse/HBASE-22266) | Add yetus personality to connectors to avoid scaladoc issues |  Major | hbase-connectors |
| [HBASE-22257](https://issues.apache.org/jira/browse/HBASE-22257) | Remove json4s and jackson dependency from hbase spark connector |  Major | hbase-connectors |


