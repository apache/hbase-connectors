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
-->
# HBASE  hbase-connectors-1.0.1 Release Notes

These release notes cover new developer and user-facing incompatibilities, important issues, features, and major improvements.


---

* [HBASE-26534](https://issues.apache.org/jira/browse/HBASE-26534) | *Minor* | **Update dependencies in hbase-connectors: HBase version to 2.4.8, and make Hadoop 3 and Spark 3 defaults**

HBASE-26534 upgrades hbase-thirdparty to 4.0.1, hbase to 2.4.9, spark to 3.1.2, and hadoop to 3.2.0. Also It builds with spark3 with scala-2.12 and hadoop3 profile as default option.


---

* [HBASE-26334](https://issues.apache.org/jira/browse/HBASE-26334) | *Major* | **Upgrade commons-io to 2.11.0 in hbase-connectors**

Upgraded commons-io to 2.11.0.


---

* [HBASE-26314](https://issues.apache.org/jira/browse/HBASE-26314) | *Major* | **Upgrade commons-io to 2.8.0 in hbase-connectors**

Upgraded commons-io to 2.8.0.


---

* [HBASE-23576](https://issues.apache.org/jira/browse/HBASE-23576) | *Minor* | **Bump Checkstyle from 8.11 to 8.18 in hbase-connectors**

Bumped the Checkstyle version from 8.11 to 8.18


---

* [HBASE-22817](https://issues.apache.org/jira/browse/HBASE-22817) | *Major* | **Use hbase-shaded dependencies in hbase-spark**

<!-- markdown -->
The HBase connector for working with Apache Spark now works with the shaded client artifacts provided by the Apache HBase project and avoids adding additional third party dependencies to the classpath.



