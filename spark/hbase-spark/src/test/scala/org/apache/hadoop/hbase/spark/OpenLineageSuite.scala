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
package org.apache.hadoop.hbase.spark

import io.openlineage.spark.agent.OpenLineageSparkListener
import java.io.File
import org.apache.hadoop.hbase.{HBaseTestingUtility, TableName}
import org.apache.hadoop.hbase.client.{ConnectionFactory, Put}
import org.apache.hadoop.hbase.spark.datasources.{HBaseSparkConf, HBaseTableCatalog}
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.SparkConf
import org.apache.spark.sql.{SparkSession, SQLContext}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FunSuite}
import org.scalatest.Matchers.convertToAnyShouldWrapper
import org.scalatest.concurrent.Eventually
import scala.collection.mutable.ArrayBuffer
import scala.io.Source

class OpenLineageSuite
    extends FunSuite
    with Eventually
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with Logging {
  @transient var sc: SparkSession = null
  var TEST_UTIL: HBaseTestingUtility = new HBaseTestingUtility

  val t1TableName = "t1"
  val t2TableName = "t2"
  val columnFamily = "c"
  var sqlContext: SQLContext = null

  val timestamp = 1234567890000L
  val lineageFile = File.createTempFile(s"openlineage_test_${System.nanoTime()}", ".log")

  override def beforeAll() {

    TEST_UTIL.startMiniCluster

    logInfo(" - minicluster started")
    try
      TEST_UTIL.deleteTable(TableName.valueOf(t1TableName))
    catch {
      case e: Exception => logInfo(" - no table " + t1TableName + " found")
    }
    try
      TEST_UTIL.deleteTable(TableName.valueOf(t2TableName))
    catch {
      case e: Exception => logInfo(" - no table " + t2TableName + " found")
    }

    logInfo(" - creating table " + t1TableName)
    TEST_UTIL.createTable(TableName.valueOf(t1TableName), Bytes.toBytes(columnFamily))
    logInfo(" - created table")
    logInfo(" - creating table " + t2TableName)
    TEST_UTIL.createTable(TableName.valueOf(t2TableName), Bytes.toBytes(columnFamily))
    logInfo(" - created table")

    val sparkConf = new SparkConf
    sparkConf.set(HBaseSparkConf.QUERY_CACHEBLOCKS, "true")
    sparkConf.set(HBaseSparkConf.QUERY_BATCHSIZE, "100")
    sparkConf.set(HBaseSparkConf.QUERY_CACHEDROWS, "100")
    sparkConf.set("spark.extraListeners", classOf[OpenLineageSparkListener].getCanonicalName)
    sparkConf.set("spark.openlineage.transport.type", "file")
    sparkConf.set("spark.openlineage.transport.location", lineageFile.getAbsolutePath)

    sc = SparkSession
      .builder()
      .master("local")
      .appName("openlineage-test")
      .config(sparkConf)
      .getOrCreate();
    val connection = ConnectionFactory.createConnection(TEST_UTIL.getConfiguration)
    try {
      val t1Table = connection.getTable(TableName.valueOf(t1TableName))

      try {
        var put = new Put(Bytes.toBytes("get1"))
        put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("a"), Bytes.toBytes("foo1"))
        put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("b"), Bytes.toBytes("1"))
        put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("i"), Bytes.toBytes(1))
        t1Table.put(put)
        put = new Put(Bytes.toBytes("get2"))
        put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("a"), Bytes.toBytes("foo2"))
        put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("b"), Bytes.toBytes("4"))
        put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("i"), Bytes.toBytes(4))
        put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("z"), Bytes.toBytes("FOO"))
        t1Table.put(put)
        put = new Put(Bytes.toBytes("get3"))
        put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("a"), Bytes.toBytes("foo3"))
        put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("b"), Bytes.toBytes("8"))
        put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("i"), Bytes.toBytes(8))
        t1Table.put(put)
        put = new Put(Bytes.toBytes("get4"))
        put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("a"), Bytes.toBytes("foo4"))
        put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("b"), Bytes.toBytes("10"))
        put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("i"), Bytes.toBytes(10))
        put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("z"), Bytes.toBytes("BAR"))
        t1Table.put(put)
        put = new Put(Bytes.toBytes("get5"))
        put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("a"), Bytes.toBytes("foo5"))
        put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("b"), Bytes.toBytes("8"))
        put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("i"), Bytes.toBytes(8))
        t1Table.put(put)
      } finally {
        t1Table.close()
      }
    } finally {
      connection.close()
    }

    new HBaseContext(sc.sparkContext, TEST_UTIL.getConfiguration)
  }

  override def afterAll() {
    TEST_UTIL.deleteTable(TableName.valueOf(t1TableName))
    logInfo("shuting down minicluster")
    TEST_UTIL.shutdownMiniCluster()

    sc.stop()
  }

  override def beforeEach(): Unit = {
    DefaultSourceStaticUtils.lastFiveExecutionRules.clear()
  }

  test("Test rowKey point only rowKey query") {
    val hbaseTable1Catalog =
      s"""{
         |"table":{"namespace":"default", "name":"t1"},
         |"rowkey":"key",
         |"columns":{
         |"KEY_FIELD":{"cf":"rowkey", "col":"key", "type":"string"},
         |"A_FIELD":{"cf":"c", "col":"a", "type":"string"},
         |"B_FIELD":{"cf":"c", "col":"b", "type":"string"}
         |}
         |}""".stripMargin

    val hbaseTable2Catalog =
      s"""{
         |"table":{"namespace":"default", "name":"t2"},
         |"rowkey":"key",
         |"columns":{
         |"KEY_FIELD":{"cf":"rowkey", "col":"key", "type":"string"},
         |"OUTPUT_A_FIELD":{"cf":"c", "col":"a", "type":"string"},
         |"OUTPUT_B_FIELD":{"cf":"c", "col":"b", "type":"string"}
         |}
         |}""".stripMargin

    val results = sc.read
      .options(Map(HBaseTableCatalog.tableCatalog -> hbaseTable1Catalog))
      .format("org.apache.hadoop.hbase.spark")
      .load()

    results.createOrReplaceTempView("tempview");

    val outputDf =
      sc.sql("SELECT KEY_FIELD, A_FIELD AS OUTPUT_A_FIELD, B_FIELD AS OUTPUT_B_FIELD FROM tempview")

    outputDf.write
      .format("org.apache.hadoop.hbase.spark")
      .options(Map(HBaseTableCatalog.tableCatalog -> hbaseTable2Catalog))
      .save()

    val events = eventually {
      val eventLog = parseEventLog(lineageFile); eventLog.size shouldBe 1; eventLog
    }

    val json = events.head
    assert(((json \\ "inputs")(0) \ "name") == JString("default.t1"))
    assert(((json \\ "inputs")(0) \ "namespace") == JString("hbase://127.0.0.1"))
    assert(((json \\ "outputs")(0) \ "name") == JString("default.t2"))
    assert(((json \\ "outputs")(0) \ "namespace") == JString("hbase://127.0.0.1"))
  }

  def parseEventLog(file: File): List[JValue] = {
    val source = Source.fromFile(file)
    val eventlist = ArrayBuffer.empty[JValue]
    for (line <- source.getLines()) {
      val event = parse(line)
      for {
        JObject(child) <- event
        JField("inputs", JArray(inputs)) <- child
        JField("outputs", JArray(outputs)) <- child
        JField("eventType", JString(eventType)) <- child
        if outputs.nonEmpty && inputs.nonEmpty && eventType == "COMPLETE"
      } yield eventlist += event
    }
    eventlist.toList
  }
}
