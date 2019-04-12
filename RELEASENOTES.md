# RELEASENOTES

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
# HBase  connector-1.0.0 Release Notes

These release notes cover new developer and user-facing incompatibilities, important issues, features, and major improvements.


---

* [HBASE-21002](https://issues.apache.org/jira/browse/HBASE-21002) | *Minor* | **Create assembly and scripts to start Kafka Proxy**

Adds a kafka proxy that appears to hbase as a replication peer. Use to tee table edits to kafka. Has mechanism for dropping/routing updates. See https://github.com/apache/hbase-connectors/tree/master/kafka for documentation.


---

* [HBASE-21434](https://issues.apache.org/jira/browse/HBASE-21434) | *Major* | **[hbase-connectors] Cleanup of kafka dependencies; clarify hadoop version**

Cleaned up kafka submodule dependencies. Added used dependencies to pom and removed the unused. Depends explicitly on hadoop2. No messing w/ hadoop3 versions.


---

* [HBASE-21446](https://issues.apache.org/jira/browse/HBASE-21446) | *Major* | **[hbase-connectors] Update spark and scala versions; add some doc on how to generate artifacts with different versions**

Updates our hbase-spark integration so defaults spark 2.4.0 (October 2018) from 2.1.1 and Scala 2.11.12 (from 2.11.8).



