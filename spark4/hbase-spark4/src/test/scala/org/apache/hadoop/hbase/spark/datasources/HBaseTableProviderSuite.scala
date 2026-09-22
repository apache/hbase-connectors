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

import java.io.{File, FileOutputStream}
import org.apache.hadoop.hbase.{HBaseTestingUtility, TableName}
import org.apache.hadoop.hbase.client.{ConnectionFactory, Put}
import org.apache.hadoop.hbase.spark.Logging
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.sql.types.{StringType, StructField, StructType}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

class HBaseTableProviderSuite extends AnyFunSuite with BeforeAndAfterAll with Logging {

  val TEST_UTIL = new HBaseTestingUtility
  var spark: SparkSession = _
  var configFile: File = _

  val tableName = "test_provider"
  val writeTableName = "test_write"
  val columnFamily = "cf"
  val numRows = 20
  val numRowsWithoutName = 3

  val catalog: String = s"""{
    |"table":{"namespace":"default", "name":"$tableName"},
    |"rowkey":"key",
    |"columns":{
    |"key":{"cf":"rowkey", "col":"key", "type":"string"},
    |"name":{"cf":"$columnFamily", "col":"name", "type":"string"},
    |"age":{"cf":"$columnFamily", "col":"age", "type":"string"},
    |"salary":{"cf":"$columnFamily", "col":"salary", "type":"string"}
    |}
    |}""".stripMargin

  override def beforeAll(): Unit = {
    TEST_UTIL.startMiniCluster()
    logInfo(" - minicluster started")

    TEST_UTIL.createTable(TableName.valueOf(tableName), Bytes.toBytes(columnFamily))
    logInfo(s" - created table $tableName")
    TEST_UTIL.createTable(TableName.valueOf(writeTableName), Bytes.toBytes(columnFamily))
    logInfo(s" - created table $writeTableName")

    populateTestData()

    val tmpDir = new File("target", "test-tmp")
    tmpDir.mkdirs()
    configFile = File.createTempFile("hbase-site", ".xml", tmpDir)
    configFile.deleteOnExit()
    val out = new FileOutputStream(configFile)
    try {
      TEST_UTIL.getConfiguration.writeXml(out)
    } finally {
      out.close()
    }

    spark = SparkSession.builder()
      .master("local[2]")
      .appName("HBaseTableProviderSuite")
      .config("spark.hadoopRDD.ignoreEmptySplits", "false")
      .getOrCreate()
  }

  override def afterAll(): Unit = {
    if (spark != null) {
      spark.stop()
    }
    if (configFile != null) {
      configFile.delete()
    }
    TEST_UTIL.shutdownMiniCluster()
    TEST_UTIL.cleanupTestDir()
  }

  private def populateTestData(): Unit = {
    val connection = ConnectionFactory.createConnection(TEST_UTIL.getConfiguration)
    val table = connection.getTable(TableName.valueOf(tableName))
    try {
      for (i <- 0 until numRows) {
        val key = f"row$i%03d"
        val put = new Put(Bytes.toBytes(key))
        put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("name"), Bytes.toBytes(s"Name$i"))
        put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("age"), Bytes.toBytes(s"${20 + i}"))
        put.addColumn(
          Bytes.toBytes(columnFamily),
          Bytes.toBytes("salary"),
          Bytes.toBytes(s"${30000 + i * 1000}"))
        table.put(put)
      }
      for (i <- 0 until numRowsWithoutName) {
        val key = f"noname$i%03d"
        val put = new Put(Bytes.toBytes(key))
        put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("age"), Bytes.toBytes(s"${50 + i}"))
        put.addColumn(
          Bytes.toBytes(columnFamily),
          Bytes.toBytes("salary"),
          Bytes.toBytes(s"${60000 + i * 1000}"))
        table.put(put)
      }
    } finally {
      table.close()
      connection.close()
    }
  }

  private def loadTable() = {
    spark.read
      .format("org.apache.hadoop.hbase.spark.datasources.HBaseTableProvider")
      .option("catalog", catalog)
      .option(HBaseSparkConf.HBASE_CONFIG_LOCATION, configFile.getAbsolutePath)
      .load()
  }

  test("full table scan returns all rows") {
    val df = loadTable()
    assert(df.count() == numRows + numRowsWithoutName)
  }

  test("select subset of columns") {
    val df = loadTable().select("key", "name").filter("key LIKE 'row%'")
    assert(df.columns.length == 2)
    assert(df.count() == numRows)
    val firstRow = df.orderBy("key").first()
    assert(firstRow.getString(0) == "row000")
    assert(firstRow.getString(1) == "Name0")
  }

  test("filter with EqualTo on non-row-key column") {
    val df = loadTable().filter("name = 'Name5'")
    assert(df.count() == 1)
    val row = df.first()
    assert(row.getAs[String]("key") == "row005")
  }

  test("filter with row key EqualTo narrows scan") {
    val df = loadTable().filter("key = 'row010'")
    assert(df.count() == 1)
    val row = df.first()
    assert(row.getAs[String]("name") == "Name10")
  }

  test("filter with row key range predicates") {
    val df = loadTable().filter("key >= 'row005' AND key < 'row010'")
    assert(df.count() == 5)
  }

  test("filter with inclusive row key range includes upper bound") {
    val df = loadTable().filter("key >= 'row005' AND key <= 'row010'")
    assert(df.count() == 6)
  }

  test("filter with row key GTE AND LTE on same value returns one row") {
    val df = loadTable().filter("key >= 'row010' AND key <= 'row010'")
    assert(df.count() == 1)
    assert(df.first().getAs[String]("name") == "Name10")
  }

  test("count with filter on non-output columns") {
    val df = loadTable().filter("name = 'Name3' AND age = '23'")
    assert(df.count() == 1)
  }

  test("StringStartsWith filter") {
    val df = loadTable().filter("name LIKE 'Name1%'")
    assert(df.count() == 11)
  }

  test("SQL query via temp view") {
    val df = loadTable()
    df.createOrReplaceTempView("hbase_test")
    val result = spark.sql("SELECT COUNT(*) as cnt FROM hbase_test WHERE age = '25'")
    assert(result.first().getLong(0) == 1)
  }

  test("select all columns preserves schema") {
    val df = loadTable()
    assert(df.schema.fieldNames.sorted.sameElements(Array("age", "key", "name", "salary")))
  }

  test("empty result for non-matching filter") {
    val df = loadTable().filter("name = 'NonExistent'")
    assert(df.count() == 0)
  }

  test("IS NULL filter returns rows missing the column") {
    val df = loadTable().select("key").filter("name IS NULL")
    assert(df.count() == numRowsWithoutName)
    val keys = df.orderBy("key").collect().map(_.getString(0))
    assert(keys.sameElements(Array("noname000", "noname001", "noname002")))
  }

  test("IS NOT NULL filter excludes rows missing the column") {
    val df = loadTable().select("key").filter("name IS NOT NULL")
    assert(df.count() == numRows)
  }

  test("short name 'hbase' alias resolves via ServiceLoader") {
    val df = spark.read
      .format("hbase")
      .option("catalog", catalog)
      .option(HBaseSparkConf.HBASE_CONFIG_LOCATION, configFile.getAbsolutePath)
      .load()
    assert(df.count() == numRows + numRowsWithoutName)
  }

  // --- Write path tests ---

  val writeCatalog: String = s"""{
    |"table":{"namespace":"default", "name":"$writeTableName"},
    |"rowkey":"key",
    |"columns":{
    |"key":{"cf":"rowkey", "col":"key", "type":"string"},
    |"name":{"cf":"$columnFamily", "col":"name", "type":"string"},
    |"age":{"cf":"$columnFamily", "col":"age", "type":"string"},
    |"salary":{"cf":"$columnFamily", "col":"salary", "type":"string"}
    |}
    |}""".stripMargin

  private def loadWriteTable() = {
    spark.read
      .format("org.apache.hadoop.hbase.spark.datasources.HBaseTableProvider")
      .option("catalog", writeCatalog)
      .option(HBaseSparkConf.HBASE_CONFIG_LOCATION, configFile.getAbsolutePath)
      .load()
  }

  test("write DataFrame and read back") {
    val writeSchema = StructType(Seq(
      StructField("key", StringType),
      StructField("name", StringType),
      StructField("age", StringType),
      StructField("salary", StringType)))
    val data = Seq(
      Row("wrow000", "Alice", "30", "50000"),
      Row("wrow001", "Bob", "25", "45000"),
      Row("wrow002", "Carol", "35", "60000"))
    val df = spark.createDataFrame(spark.sparkContext.parallelize(data), writeSchema)

    df.write
      .format("org.apache.hadoop.hbase.spark.datasources.HBaseTableProvider")
      .option("catalog", writeCatalog)
      .option(HBaseSparkConf.HBASE_CONFIG_LOCATION, configFile.getAbsolutePath)
      .mode("append")
      .save()

    val result = loadWriteTable()
    assert(result.count() == 3)
    val rows = result.orderBy("key").collect()
    assert(rows(0).getAs[String]("key") == "wrow000")
    assert(rows(0).getAs[String]("name") == "Alice")
    assert(rows(1).getAs[String]("key") == "wrow001")
    assert(rows(1).getAs[String]("age") == "25")
    assert(rows(2).getAs[String]("salary") == "60000")
  }

  test("write preserves null columns") {
    val writeSchema = StructType(Seq(
      StructField("key", StringType),
      StructField("name", StringType),
      StructField("age", StringType),
      StructField("salary", StringType)))
    val data = Seq(
      Row("wnull000", null, "40", "70000"),
      Row("wnull001", "Dave", null, null))
    val df = spark.createDataFrame(spark.sparkContext.parallelize(data), writeSchema)

    df.write
      .format("org.apache.hadoop.hbase.spark.datasources.HBaseTableProvider")
      .option("catalog", writeCatalog)
      .option(HBaseSparkConf.HBASE_CONFIG_LOCATION, configFile.getAbsolutePath)
      .mode("append")
      .save()

    val result = loadWriteTable().filter("key LIKE 'wnull%'").orderBy("key")
    assert(result.count() == 2)
    val rows = result.collect()
    assert(rows(0).getAs[String]("key") == "wnull000")
    assert(rows(0).getAs[String]("name") == null)
    assert(rows(0).getAs[String]("age") == "40")
    assert(rows(1).getAs[String]("key") == "wnull001")
    assert(rows(1).getAs[String]("name") == "Dave")
    assert(rows(1).getAs[String]("salary") == null)
  }

  test("write with short name 'hbase' alias") {
    val writeSchema = StructType(Seq(
      StructField("key", StringType),
      StructField("name", StringType),
      StructField("age", StringType),
      StructField("salary", StringType)))
    val data = Seq(Row("wshort000", "Eve", "28", "55000"))
    val df = spark.createDataFrame(spark.sparkContext.parallelize(data), writeSchema)

    df.write
      .format("hbase")
      .option("catalog", writeCatalog)
      .option(HBaseSparkConf.HBASE_CONFIG_LOCATION, configFile.getAbsolutePath)
      .mode("append")
      .save()

    val result = loadWriteTable().filter("key = 'wshort000'")
    assert(result.count() == 1)
    assert(result.first().getAs[String]("name") == "Eve")
  }
}
