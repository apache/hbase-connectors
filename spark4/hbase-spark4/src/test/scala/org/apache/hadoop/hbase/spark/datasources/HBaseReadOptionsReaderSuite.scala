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

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.util.{Locale, UUID}
import org.apache.hadoop.hbase.{HBaseConfiguration, HConstants, TableName}
import org.apache.hadoop.hbase.client.{Get, Result, ResultScanner, Scan, Table}
import org.apache.hadoop.hbase.client.metrics.ScanMetrics
import org.apache.hadoop.hbase.spark.{ConnectionMocker, HBaseConnectionCache, HBaseConnectionKey}
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.connector.read.PartitionReaderFactory
import org.apache.spark.sql.util.CaseInsensitiveStringMap
import org.scalatest.funsuite.AnyFunSuite
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._

class HBaseReadOptionsReaderSuite extends AnyFunSuite {
  private val globals = Map(
    HBaseSparkConf.QUERY_CACHEBLOCKS -> "true",
    HBaseSparkConf.QUERY_CACHEDROWS -> "256",
    HBaseSparkConf.BULKGET_SIZE -> "2",
    HBaseSparkConf.QUERY_BATCHSIZE -> "1")

  private val properties = Map(HBaseTableCatalog.tableCatalog -> """{
    |"table":{"namespace":"default", "name":"read_options"},
    |"rowkey":"key",
    |"columns":{"key":{"cf":"rowkey", "col":"key", "type":"string"}}
    |}""".stripMargin)

  test("serialized reader factory applies global settings to Scan and Get batches") {
    checkRead(globals, Map.empty, HBaseReadOptions(true, 256, 2), Seq(2, 2, 1))
  }

  test("per-read options override global settings in Scan and Get batches") {
    val options = Map(
      HBaseSparkConf.QUERY_CACHEBLOCKS.toUpperCase(Locale.ROOT) -> "false",
      HBaseSparkConf.QUERY_CACHEDROWS -> "64",
      HBaseSparkConf.BULKGET_SIZE -> "3",
      HBaseSparkConf.QUERY_BATCHSIZE -> "invalid")
    checkRead(globals, options, HBaseReadOptions(false, 64, 3), Seq(3, 2))
  }

  test("reader factory preserves defaults when global and per-read settings are absent") {
    checkRead(Map.empty, Map.empty, HBaseReadOptions(false, -1, 1000), Seq(5))
  }

  test("per-read nonpositive caching disables a positive global override") {
    for (rows <- Seq(-1, 0)) {
      checkRead(
        globals,
        Map(HBaseSparkConf.QUERY_CACHEDROWS -> rows.toString),
        HBaseReadOptions(true, -1, 2),
        Seq(2, 2, 1))
    }
  }

  private def checkRead(
      globalOptions: Map[String, String],
      options: Map[String, String],
      expected: HBaseReadOptions,
      expectedGetSizes: Seq[Int]): Unit = {
    val sparkConf = new SparkConf(false)
      .setMaster("local[1]")
      .setAppName("HBaseReadOptionsReaderSuite")
      .set("spark.ui.enabled", "false")
      .setAll(globalOptions)
    val spark = SparkSession.builder().config(sparkConf).getOrCreate()
    val hadoopConf = spark.sparkContext.hadoopConfiguration
    hadoopConf.set(HConstants.HBASE_CLIENT_INSTANCE_ID, UUID.randomUUID().toString)
    val conf = HBaseConfiguration.create(hadoopConf)
    val factory =
      try {
        // Session settings must not replace the SparkContext's application configuration.
        spark.conf.set(HBaseSparkConf.QUERY_CACHEBLOCKS, "false")
        spark.conf.set(HBaseSparkConf.QUERY_CACHEDROWS, "999")
        spark.conf.set(HBaseSparkConf.BULKGET_SIZE, "999")
        val catalog = HBaseTableCatalog(properties)
        val table = new HBaseTable(catalog.toDataType, properties)
        val batch =
          table.newScanBuilder(new CaseInsensitiveStringMap(options.asJava)).build().toBatch
        roundTrip(batch.createReaderFactory())
      } finally {
        spark.stop()
        SparkSession.clearActiveSession()
        SparkSession.clearDefaultSession()
      }

    val scans = ArrayBuffer.empty[Scan]
    val getSizes = ArrayBuffer.empty[Int]
    val scanner = new ResultScanner {
      override def next(): Result = null
      override def close(): Unit = {}
      override def renewLease(): Boolean = true
      override def getScanMetrics: ScanMetrics = null
    }
    val table = Proxy
      .newProxyInstance(
        classOf[Table].getClassLoader,
        Array(classOf[Table]),
        new InvocationHandler {
          override def invoke(proxy: AnyRef, method: Method, args: Array[AnyRef]): AnyRef = {
            method.getName match {
              case "getScanner" =>
                scans += new Scan(args(0).asInstanceOf[Scan])
                scanner
              case "get" =>
                getSizes += args(0).asInstanceOf[java.util.List[Get]].size()
                Array.empty[Result]
              case "close" => null
              case other => throw new UnsupportedOperationException(other)
            }
          }
        })
      .asInstanceOf[Table]
    val connection = new ConnectionMocker {
      override def getTable(name: TableName): Table = table
    }
    val key = new HBaseConnectionKey(conf)
    HBaseConnectionCache.getConnection(key, connection).close()
    try {
      val partition =
        HBaseInputPartition(0, Seq(Range(None, None)), (1 to 5).map(i => Bytes.toBytes(s"row$i")))
      val reader = factory.createReader(partition)
      try {
        assert(!reader.next())
      } finally {
        reader.close()
      }
      assert(scans.size == 1)
      assert(scans.head.getCacheBlocks == expected.cacheBlocks)
      assert(scans.head.getCaching == expected.cachedRows)
      assert(scans.head.getBatch == -1)
      assert(getSizes.toSeq == expectedGetSizes)
    } finally {
      HBaseConnectionCache.connectionMap.synchronized {
        HBaseConnectionCache.connectionMap.remove(key)
      }
      connection.close()
    }
  }

  private def roundTrip(factory: PartitionReaderFactory): PartitionReaderFactory = {
    val bytes = new ByteArrayOutputStream()
    val out = new ObjectOutputStream(bytes)
    try {
      out.writeObject(factory)
    } finally {
      out.close()
    }
    val in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray))
    try {
      in.readObject().asInstanceOf[PartitionReaderFactory]
    } finally {
      in.close()
    }
  }
}
