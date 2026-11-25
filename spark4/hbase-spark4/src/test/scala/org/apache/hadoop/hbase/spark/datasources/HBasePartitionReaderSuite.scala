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

import java.io.IOException
import org.apache.hadoop.hbase.spark.datasources.TableStub.refCount
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.sql.sources.Filter
import org.apache.spark.sql.types._
import org.scalatest.funsuite.AnyFunSuite

class HBasePartitionReaderSuite extends AnyFunSuite {

  private val catalog = s"""{
    |"table":{"namespace":"default", "name":"readertable"},
    |"rowkey":"key",
    |"columns":{
    |"key":{"cf":"rowkey", "col":"key", "type":"string"},
    |"name":{"cf":"cf", "col":"name", "type":"string"}
    |}}""".stripMargin
  private val properties = Map(HBaseTableCatalog.tableCatalog -> catalog)
  private val hbaseCatalog = HBaseTableCatalog(properties)
  private val schema =
    StructType(Seq(StructField("key", StringType), StructField("name", StringType)))

  // Two scan ranges, so the reader opens two scanners while it is constructed.
  private val partition = HBaseInputPartition(
    0,
    Seq(
      Range(None, Some(Bound(Bytes.toBytes("m"), false))),
      Range(Some(Bound(Bytes.toBytes("m"), true)), None)),
    Seq.empty)

  private def newReader(
      conf: SerializableConfiguration,
      requiredSchema: StructType = schema,
      readerProperties: Map[String, String] = properties): HBasePartitionReader =
    new HBasePartitionReader(
      partition,
      requiredSchema,
      readerProperties,
      hbaseCatalog,
      Array.empty[Filter],
      HBaseSparkConf.DEFAULT_QUERY_ENCODER,
      false,
      conf,
      HBaseReadOptions(false, -1, HBaseSparkConf.DEFAULT_BULKGET_SIZE))

  test("close releases the scanners, table and connection once") {
    val stub = new TableStub()
    stub.withCachedConnection {
      (conf, smartConn) =>
        val reader = newReader(conf)
        assert(stub.scannersOpened == 2)
        assert(refCount(smartConn) == 2)
        assert(!reader.next())
        reader.close()
        reader.close()
        assert(stub.scannerCloses == 2)
        assert(stub.closes == 1)
        assert(refCount(smartConn) == 1)
    }
  }

  test("close releases the table and connection when a scanner fails to close") {
    val stub = new TableStub(failScannerClose = true)
    stub.withCachedConnection {
      (conf, smartConn) =>
        val reader = newReader(conf)
        val e = intercept[IllegalStateException](reader.close())
        assert(e.getSuppressed.length == 1)
        assert(stub.scannerCloses == 2)
        assert(stub.closes == 1)
        assert(refCount(smartConn) == 1)
    }
  }

  test("failure to open a scanner releases the open scanners, table and connection") {
    val stub = new TableStub(failScannerAt = 2)
    stub.withCachedConnection {
      (conf, smartConn) =>
        intercept[IOException](newReader(conf))
        assert(stub.scannerCloses == 1)
        assert(stub.closes == 1)
        assert(refCount(smartConn) == 1)
    }
  }

  test("invalid time options release the table and connection") {
    val stub = new TableStub()
    stub.withCachedConnection {
      (conf, smartConn) =>
        val invalid = properties ++ Map(
          HBaseSparkConf.TIMESTAMP -> "1",
          HBaseSparkConf.TIMERANGE_START -> "0")
        intercept[IllegalArgumentException](newReader(conf, readerProperties = invalid))
        assert(stub.scannersOpened == 0)
        assert(stub.closes == 1)
        assert(refCount(smartConn) == 1)
    }
  }

  test("getTable failure releases the connection") {
    val stub = new TableStub(failGetTable = true)
    stub.withCachedConnection {
      (conf, smartConn) =>
        intercept[IOException](newReader(conf))
        assert(refCount(smartConn) == 1)
    }
  }

  test("unknown required column fails before taking a connection") {
    val stub = new TableStub()
    stub.withCachedConnection {
      (conf, smartConn) =>
        val unknown = StructType(Seq(StructField("missing", StringType)))
        intercept[NoSuchElementException](newReader(conf, requiredSchema = unknown))
        assert(refCount(smartConn) == 1)
    }
  }
}
