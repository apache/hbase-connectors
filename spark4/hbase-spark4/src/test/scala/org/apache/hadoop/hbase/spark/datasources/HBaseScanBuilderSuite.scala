/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.spark.datasources

import org.apache.spark.sql.sources._
import org.apache.spark.sql.types._
import org.scalatest.funsuite.AnyFunSuite

class HBaseScanBuilderSuite extends AnyFunSuite {

  val catalog = s"""{
                    |"table":{"namespace":"default", "name":"testtable"},
                    |"rowkey":"key",
                    |"columns":{
                    |"key":{"cf":"rowkey", "col":"key", "type":"string"},
                    |"name":{"cf":"cf", "col":"name", "type":"string"},
                    |"age":{"cf":"cf", "col":"age", "type":"int"},
                    |"salary":{"cf":"cf", "col":"salary", "type":"double"}
                    |}
                    |}""".stripMargin

  val schema = StructType(
    Seq(
      StructField("key", StringType),
      StructField("name", StringType),
      StructField("age", IntegerType),
      StructField("salary", DoubleType)))

  val properties = Map(HBaseTableCatalog.tableCatalog -> catalog)

  private def newBuilder(): HBaseScanBuilder = new HBaseScanBuilder(schema, properties)

  private def typedBuilder(dataType: String, pushDown: Boolean = true): HBaseScanBuilder = {
    val typedCatalog = s"""{
      |"table":{"namespace":"default", "name":"testtable"},
      |"rowkey":"key",
      |"columns":{
      |"key":{"cf":"rowkey", "col":"key", "type":"$dataType"},
      |"value":{"cf":"cf", "col":"value", "type":"$dataType"}
      |}}""".stripMargin
    val options = Map(
      HBaseTableCatalog.tableCatalog -> typedCatalog,
      HBaseSparkConf.PUSHDOWN_COLUMN_FILTER -> pushDown.toString)
    new HBaseScanBuilder(HBaseTableCatalog(options).toDataType, options)
  }

  test("HBASE-30510 unsafe comparisons remain residual for columns and row keys") {
    val values = Seq[(String, Any)](
      "byte" -> 1.toByte,
      "date" -> java.sql.Date.valueOf("2026-01-01"),
      "timestamp" -> java.sql.Timestamp.valueOf("2026-01-01 00:00:00.123456"),
      "decimal(10,2)" -> new java.math.BigDecimal("1.00"),
      "float" -> 0.0f,
      "double" -> 0.0d)
    for ((dataType, value) <- values; attr <- Seq("key", "value")) {
      val filters = Array[Filter](
        LessThan(attr, value),
        LessThanOrEqual(attr, value),
        GreaterThan(attr, value),
        GreaterThanOrEqual(attr, value)) ++
        (if (dataType == "byte") Array.empty[Filter] else Array[Filter](EqualTo(attr, value)))
      val builder = typedBuilder(dataType)
      assert(builder.pushFilters(filters).toSeq == filters.toSeq, s"$dataType $attr")
      assert(builder.pushedFilters().isEmpty)
      builder.build().toBatch
    }
  }

  test("HBASE-30510 safe primitive comparisons still push down") {
    val values = Seq[(String, Any)](
      "boolean" -> true,
      "short" -> 1.toShort,
      "int" -> 1,
      "long" -> 1L,
      "string" -> "value",
      "binary" -> Array[Byte](1, 2))
    for ((dataType, value) <- values) {
      val builder = typedBuilder(dataType)
      val filters = Array[Filter](EqualTo("value", value), GreaterThan("value", value))
      assert(builder.pushFilters(filters).isEmpty, dataType)
      assert(builder.pushedFilters().toSeq == filters.toSeq)
    }
    val builder = typedBuilder("byte")
    assert(builder.pushFilters(Array(EqualTo("key", 1.toByte))).isEmpty)
  }

  test("HBASE-30510 mixed compound filters are returned intact to Spark") {
    val safe = EqualTo("key", "row001")
    val unsafe = GreaterThan("salary", 0.0d)
    for (compound <- Seq(And(safe, unsafe), Or(safe, unsafe), Or(And(safe, unsafe), safe))) {
      val builder = newBuilder()
      assert(builder.pushFilters(Array(compound, safe)).toSeq == Seq(compound))
      assert(builder.pushedFilters().toSeq == Seq(safe))
    }
  }

  test("HBASE-30510 invalid literals and prefix types are not encoded") {
    val filters = Array[Filter](
      EqualTo("age", 1L),
      GreaterThan("age", 1L),
      EqualTo("name", null),
      GreaterThan("name", null),
      StringStartsWith("age", "1"))
    val builder = newBuilder()
    assert(builder.pushFilters(filters).toSeq == filters.toSeq)
    assert(builder.pushedFilters().isEmpty)
  }

  test("HBASE-30510 null checks do not require comparison encoding") {
    val builder = typedBuilder("timestamp")
    val filters = Array[Filter](IsNull("value"), IsNotNull("value"))
    assert(builder.pushFilters(filters).isEmpty)
    assert(builder.pushedFilters().toSeq == filters.toSeq)
  }

  test("HBASE-30510 Avro primitive comparisons are left to Spark") {
    val avroCatalog = catalog.replace("\"type\":\"int\"", "\"avro\":\"intSchema\"")
    val options = Map(HBaseTableCatalog.tableCatalog -> avroCatalog, "intSchema" -> "\"int\"")
    val builder = new HBaseScanBuilder(HBaseTableCatalog(options).toDataType, options)
    val filters = Array[Filter](EqualTo("age", 1), GreaterThan("age", 1))
    assert(builder.pushFilters(filters).toSeq == filters.toSeq)
    assert(builder.pushedFilters().isEmpty)
  }

  test("HBASE-30510 unknown encoder capabilities do not prune row keys") {
    val options = properties +
      (HBaseSparkConf.QUERY_ENCODER -> classOf[UnverifiedQueryEncoder].getName)
    val builder = new HBaseScanBuilder(schema, options)
    val filter = GreaterThan("key", "row001")
    assert(builder.pushFilters(Array(filter)).toSeq == Seq(filter))
    assert(builder.pushedFilters().isEmpty)
    builder.build().toBatch
  }

  test("EqualTo on known column is pushed") {
    val builder = newBuilder()
    val unsupported = builder.pushFilters(Array(EqualTo("name", "Alice")))
    assert(unsupported.isEmpty)
    assert(builder.pushedFilters().length == 1)
    assert(builder.pushedFilters()(0) == EqualTo("name", "Alice"))
  }

  test("LessThan on known column is pushed") {
    val builder = newBuilder()
    val unsupported = builder.pushFilters(Array(LessThan("age", 30)))
    assert(unsupported.isEmpty)
    assert(builder.pushedFilters().length == 1)
  }

  test("GreaterThan on known column is pushed") {
    val builder = newBuilder()
    val unsupported = builder.pushFilters(Array(GreaterThan("age", 30)))
    assert(unsupported.isEmpty)
    assert(builder.pushedFilters().length == 1)
  }

  test("LessThanOrEqual on known column is pushed") {
    val builder = newBuilder()
    val unsupported = builder.pushFilters(Array(LessThanOrEqual("age", 25)))
    assert(unsupported.isEmpty)
    assert(builder.pushedFilters().length == 1)
  }

  test("GreaterThanOrEqual on known column is pushed") {
    val builder = newBuilder()
    val unsupported = builder.pushFilters(Array(GreaterThanOrEqual("age", 18)))
    assert(unsupported.isEmpty)
    assert(builder.pushedFilters().length == 1)
  }

  test("StringStartsWith on known column is pushed") {
    val builder = newBuilder()
    val unsupported = builder.pushFilters(Array(StringStartsWith("name", "A")))
    assert(unsupported.isEmpty)
    assert(builder.pushedFilters().length == 1)
  }

  test("IsNull on known column is pushed") {
    val builder = newBuilder()
    val unsupported = builder.pushFilters(Array(IsNull("name")))
    assert(unsupported.isEmpty)
    assert(builder.pushedFilters().length == 1)
  }

  test("IsNotNull on known column is pushed") {
    val builder = newBuilder()
    val unsupported = builder.pushFilters(Array(IsNotNull("name")))
    assert(unsupported.isEmpty)
    assert(builder.pushedFilters().length == 1)
  }

  test("filter on unknown column is not pushed") {
    val builder = newBuilder()
    val unsupported = builder.pushFilters(Array(EqualTo("unknown_col", "value")))
    assert(unsupported.length == 1)
    assert(builder.pushedFilters().isEmpty)
  }

  test("Or compound filter is pushed") {
    val builder = newBuilder()
    val filter = Or(EqualTo("name", "Alice"), EqualTo("name", "Bob"))
    val unsupported = builder.pushFilters(Array(filter))
    assert(unsupported.isEmpty)
    assert(builder.pushedFilters().length == 1)
  }

  test("And compound filter is pushed") {
    val builder = newBuilder()
    val filter = And(GreaterThan("age", 20), LessThan("age", 40))
    val unsupported = builder.pushFilters(Array(filter))
    assert(unsupported.isEmpty)
    assert(builder.pushedFilters().length == 1)
  }

  test("mixed supported and unsupported filters are separated") {
    val builder = newBuilder()
    val filters =
      Array[Filter](EqualTo("name", "Alice"), EqualTo("unknown", "value"), GreaterThan("age", 20))
    val unsupported = builder.pushFilters(filters)
    assert(unsupported.length == 1)
    assert(unsupported(0) == EqualTo("unknown", "value"))
    assert(builder.pushedFilters().length == 2)
  }

  test("filter on row key column is pushed") {
    val builder = newBuilder()
    val unsupported = builder.pushFilters(Array(EqualTo("key", "row001")))
    assert(unsupported.isEmpty)
    assert(builder.pushedFilters().length == 1)
  }

  test("pruneColumns reduces required schema") {
    val builder = newBuilder()
    val pruned = StructType(Seq(StructField("key", StringType), StructField("name", StringType)))
    builder.pruneColumns(pruned)
    val scan = builder.build()
    assert(scan.readSchema() == pruned)
  }

  test("multiple pushFilters calls replace previous filters") {
    val builder = newBuilder()
    builder.pushFilters(Array(EqualTo("name", "Alice")))
    assert(builder.pushedFilters().length == 1)
    builder.pushFilters(Array(GreaterThan("age", 20), LessThan("age", 40)))
    assert(builder.pushedFilters().length == 2)
  }
}

class UnverifiedQueryEncoder extends NaiveEncoder {
  override def encode(dt: DataType, value: Any): Array[Byte] =
    throw new AssertionError("Unverified encoder must not encode a pushed comparison")

  override def ranges(value: Any): Option[BoundRanges] =
    throw new AssertionError("Unverified encoder must not prune row keys")
}
