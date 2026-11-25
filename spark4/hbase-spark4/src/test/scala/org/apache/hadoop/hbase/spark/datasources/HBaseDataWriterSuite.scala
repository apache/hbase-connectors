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
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.types._
import org.apache.spark.unsafe.types.UTF8String
import org.scalatest.funsuite.AnyFunSuite

class HBaseDataWriterSuite extends AnyFunSuite {

  private val catalog = s"""{
    |"table":{"namespace":"default", "name":"writertable"},
    |"rowkey":"key",
    |"columns":{
    |"key":{"cf":"rowkey", "col":"key", "type":"string"},
    |"name":{"cf":"cf", "col":"name", "type":"string"}
    |}}""".stripMargin
  private val properties =
    Map(HBaseTableCatalog.tableCatalog -> catalog, HBaseSparkConf.BULKGET_SIZE -> "10")
  private val hbaseCatalog = HBaseTableCatalog(properties)
  private val schema =
    StructType(Seq(StructField("key", StringType), StructField("name", StringType)))

  private def newWriter(
      conf: SerializableConfiguration,
      writerSchema: StructType = schema): HBaseDataWriter =
    new HBaseDataWriter(writerSchema, properties, hbaseCatalog, conf)

  private def row(key: String, name: String): InternalRow =
    InternalRow(UTF8String.fromString(key), UTF8String.fromString(name))

  test("commit flushes buffered puts and close releases the table and connection once") {
    val stub = new TableStub()
    stub.withCachedConnection {
      (conf, smartConn) =>
        val writer = newWriter(conf)
        assert(refCount(smartConn) == 2)
        writer.write(row("row1", "Alice"))
        writer.write(row("row2", "Bob"))
        assert(stub.puts == 0)
        writer.commit()
        assert(stub.puts == 2)
        assert(stub.closes == 0)
        assert(refCount(smartConn) == 2)
        writer.close()
        writer.close()
        assert(stub.closes == 1)
        assert(refCount(smartConn) == 1)
    }
  }

  test("abort drops buffered puts and close releases the table and connection once") {
    val stub = new TableStub()
    stub.withCachedConnection {
      (conf, smartConn) =>
        val writer = newWriter(conf)
        writer.write(row("row1", "Alice"))
        writer.abort()
        writer.close()
        assert(stub.puts == 0)
        assert(stub.closes == 1)
        assert(refCount(smartConn) == 1)
    }
  }

  test("failed commit followed by abort and close releases the connection once") {
    val stub = new TableStub(failPut = true)
    stub.withCachedConnection {
      (conf, smartConn) =>
        // Same order as Spark's DataWritingSparkTask: commit fails, then abort, then close.
        val writer = newWriter(conf)
        writer.write(row("row1", "Alice"))
        intercept[IOException](writer.commit())
        writer.abort()
        writer.close()
        assert(stub.closes == 1)
        assert(refCount(smartConn) == 1)
    }
  }

  test("close releases the connection when closing the table fails") {
    val stub = new TableStub(failClose = true)
    stub.withCachedConnection {
      (conf, smartConn) =>
        val writer = newWriter(conf)
        writer.commit()
        intercept[IOException](writer.close())
        assert(refCount(smartConn) == 1)
        writer.close()
        assert(stub.closes == 1)
        assert(refCount(smartConn) == 1)
    }
  }

  test("invalid schema fails before taking a connection") {
    val stub = new TableStub()
    stub.withCachedConnection {
      (conf, smartConn) =>
        val noRowKey = StructType(Seq(StructField("name", StringType)))
        intercept[IllegalArgumentException](newWriter(conf, noRowKey))
        assert(refCount(smartConn) == 1)
    }
  }

  test("getTable failure releases the connection") {
    val stub = new TableStub(failGetTable = true)
    stub.withCachedConnection {
      (conf, smartConn) =>
        intercept[IOException](newWriter(conf))
        assert(refCount(smartConn) == 1)
    }
  }
}
