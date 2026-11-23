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

## Java batch API on Spark 4

Spark 4 applications can use `org.apache.hbase.connectors.spark:hbase-spark4`
(Scala 2.13, JDK 17+) and `org.apache.hadoop.hbase.spark.JavaHBaseContext`.
The batch API retains the Spark 3 Java method signatures, including `scala.Tuple2`
callback arguments. Applications must recompile against the Spark 4 dependencies.
Java callers use Spark's Java function interfaces and `java.util.Iterator` without
supplying Scala collection conversions or ClassTags.

For example, with an existing table `example` and column family `cf`:

```java
import java.util.Arrays;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.spark.JavaHBaseContext;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

public class JavaHBaseBatchExample {
  public static void main(String[] args) {
    try (JavaSparkContext jsc = new JavaSparkContext(
        new SparkConf().setAppName("JavaHBaseBatchExample"))) {
      JavaHBaseContext context = new JavaHBaseContext(jsc, HBaseConfiguration.create());
      TableName table = TableName.valueOf("example");
      byte[] family = Bytes.toBytes("cf");
      byte[] qualifier = Bytes.toBytes("value");
      JavaRDD<String> keys = jsc.parallelize(Arrays.asList("row1", "row2"));
      context.bulkPut(keys, table, key -> new Put(Bytes.toBytes(key))
          .addColumn(family, qualifier, Bytes.toBytes("hello " + key)));
      JavaRDD<String> values = context.bulkGet(table, 100, keys,
          key -> new Get(Bytes.toBytes(key)),
          result -> Bytes.toString(result.getValue(family, qualifier)));
      System.out.println(values.collect());
    }
  }
}
```

`foreachPartition` and `mapPartitions` supply a connector-managed Connection:
callbacks must close the Tables, scanners and mutators they create, while leaving
that Connection open. Both `hbaseRDD` scan overloads and the `bulkDelete` API are
also available. `bulkLoad` and `bulkLoadThinRows` generate staging HFiles; callers
must subsequently import them, for example with `LoadIncrementalHFiles.doBulkLoad`.
For `bulkLoadThinRows`, each input record must contain all cells for its row key,
and each row key must occur only once. Legacy DStream wrappers are not included.

## Configuration and Installation
**Client-side** (Spark) configuration:
- The HBase configuration file `hbase-site.xml` should be made available to Spark, it can be copied to `$SPARK_CONF_DIR` (default is $SPARK_HOME/conf`)

**Server-side** (HBase region servers) configuration:
- The following jars need to be in the CLASSPATH of the HBase region servers:
  - scala-library, hbase-spark, and hbase-spark-protocol-shaded.
- The server-side configuration is needed for column filter pushdown
  - if you cannot perform the server-side configuration, consider using `.option("hbase.spark.pushdown.columnfilter", false)`
- The Scala library version must match the Scala version (2.11 or 2.12) used for compiling the connector.

## Predicate pushdown limits

Spark 3 and Spark 4 evaluate predicates in Spark when the connector cannot safely
encode both the comparison and any row-key bounds. This includes comparisons on
date, timestamp, decimal, floating-point, Avro and custom-SerDes fields, and range
comparisons on byte fields. A compound AND or OR is accepted only when both sides
are supported; a residual predicate never supplies row-key bounds.

Range comparisons are supported only with the exact built-in `NaiveEncoder`.
Custom encoders, including subclasses of `NaiveEncoder`, may change comparison
tags or range encoding, so their range predicates stay in Spark. Equality, string
prefix and NULL checks are checked separately. Disabling
`hbase.spark.pushdown.columnfilter` retains safe row-key pruning.

NULL checks in residual predicates also prevent qualifier pruning for Scan and
Get operations. Reading all qualifiers preserves rows that exist only through
cells outside the requested columns, at the cost of reading additional data.
Cell batching (`hbase.spark.query.batchsize`) is disabled for scans with NULL
checks or a server-side SQL filter, since predicate evaluation requires complete
rows. A message is logged at INFO when a positive batch size is ignored.

The SQL pushdown filter declares that it needs complete rows, including when
scanner limits or PREAD switching would otherwise produce partial results.
Deploy the updated pushdown filter on the RegionServers as well.
