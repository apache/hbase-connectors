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

## Scala and Spark Versions

To generate an artifact for a different [spark version](https://mvnrepository.com/artifact/org.apache.spark/spark-core) and/or [scala version](https://www.scala-lang.org/download/all.html), pass command-line options as follows (changing version numbers appropriately):

```
$ mvn -Dspark.version=2.2.2 -Dscala.version=2.11.7 -Dscala.binary.version=2.11 clean install
```

See above linked spark version to match spark version and supported scala version.
