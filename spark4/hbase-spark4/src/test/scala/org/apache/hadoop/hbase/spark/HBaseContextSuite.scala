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

import org.apache.hadoop.hbase.{CellUtil, HBaseTestingUtility, TableName}
import org.apache.hadoop.hbase.client._
import org.apache.hadoop.hbase.filter.FirstKeyOnlyFilter
import org.apache.hadoop.hbase.io.ImmutableBytesWritable
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.{SparkConf, SparkContext}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import scala.collection.mutable.ListBuffer

class HBaseContextSuite extends AnyFunSuite with BeforeAndAfterAll with Logging {

  @transient var sc: SparkContext = _
  var hbaseContext: HBaseContext = _
  val TEST_UTIL = new HBaseTestingUtility

  val tableName = "t1"
  val columnFamily = "c"

  override def beforeAll(): Unit = {
    TEST_UTIL.startMiniCluster()
    logInfo(" - minicluster started")

    TEST_UTIL.createTable(TableName.valueOf(tableName), Bytes.toBytes(columnFamily))
    logInfo(" - created table " + tableName)

    populateTestData()

    val sparkConf = new SparkConf()
      .setMaster("local[2]")
      .setAppName("HBaseContextSuite")
      .set("spark.hadoopRDD.ignoreEmptySplits", "false")
    sc = new SparkContext(sparkConf)

    hbaseContext = new HBaseContext(sc, TEST_UTIL.getConfiguration)
  }

  override def afterAll(): Unit = {
    if (sc != null) {
      sc.stop()
    }
    TEST_UTIL.shutdownMiniCluster()
    TEST_UTIL.cleanupTestDir()
  }

  private def populateTestData(): Unit = {
    val connection = ConnectionFactory.createConnection(TEST_UTIL.getConfiguration)
    val table = connection.getTable(TableName.valueOf(tableName))
    try {
      val puts = Array(
        makePut("row1", "a", "value1"),
        makePut("row2", "a", "value2"),
        makePut("row2", "b", "value2b"),
        makePut("row3", "a", "value3"),
        makePut("row4", "a", "value4"),
        makePut("row5", "a", "value5"))
      puts.foreach(table.put)
    } finally {
      table.close()
      connection.close()
    }
  }

  private def makePut(rowKey: String, qualifier: String, value: String): Put = {
    val put = new Put(Bytes.toBytes(rowKey))
    put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes(qualifier), Bytes.toBytes(value))
    put
  }

  test("hbaseRDD with map function transforms scan results") {
    val scan = new Scan()
    val cf = Bytes.toBytes(columnFamily)
    val qual = Bytes.toBytes("a")

    val rdd = hbaseContext.hbaseRDD[String](
      TableName.valueOf(tableName),
      scan,
      (r: (ImmutableBytesWritable, org.apache.hadoop.hbase.client.Result)) => {
        val key = Bytes.toString(r._1.copyBytes())
        val value = Bytes.toString(CellUtil.cloneValue(r._2.getColumnLatestCell(cf, qual)))
        s"$key=$value"
      })

    val results = rdd.collect().sorted
    assert(results.length == 5)
    assert(results.contains("row1=value1"))
    assert(results.contains("row2=value2"))
    assert(results.contains("row3=value3"))
    assert(results.contains("row4=value4"))
    assert(results.contains("row5=value5"))
  }

  test("hbaseRDD raw scan returns correct results with start/stop row") {
    val scan = new Scan()
    scan.withStartRow(Bytes.toBytes("row2"))
    scan.withStopRow(Bytes.toBytes("row4"))

    val rdd = hbaseContext.hbaseRDD(TableName.valueOf(tableName), scan)

    val results = rdd.map(r => Bytes.toString(r._1.copyBytes())).collect().sorted
    assert(results.length == 2)
    assert(results(0) == "row2")
    assert(results(1) == "row3")
  }

  test("hbaseRDD with FirstKeyOnlyFilter limits cells per row") {
    val scan = new Scan()
    scan.withStartRow(Bytes.toBytes("row2"))
    scan.withStopRow(Bytes.toBytes("row3"))
    scan.setFilter(new FirstKeyOnlyFilter())

    val rdd = hbaseContext.hbaseRDD(TableName.valueOf(tableName), scan)

    val cellCount = rdd.map(r => r._2.listCells().size()).collect().sum
    // row2 has 2 cells (a, b) but FirstKeyOnlyFilter returns only 1 per row
    assert(cellCount == 1)
  }

  test("hbaseRDDAsRows extracts specified columns into Row objects") {
    val scan = new Scan()
    scan.withStartRow(Bytes.toBytes("row1"))
    scan.withStopRow(Bytes.toBytes("row2"))

    val rdd = hbaseContext.hbaseRDDAsRows(tableName, scan, Seq("a"))

    val rows = rdd.collect()
    assert(rows.length == 1)
    assert(rows(0).getString(0) == "value1")
  }

  test("hbaseRDDAsRows returns null for missing columns") {
    val scan = new Scan()
    scan.withStartRow(Bytes.toBytes("row1"))
    scan.withStopRow(Bytes.toBytes("row2"))

    val rdd = hbaseContext.hbaseRDDAsRows(tableName, scan, Seq("a", "nonexistent"))

    val rows = rdd.collect()
    assert(rows.length == 1)
    assert(rows(0).getString(0) == "value1")
    assert(rows(0).get(1) == null)
  }

  test("bulkPut to test HBase client") {
    val rdd = sc.parallelize(
      Array[(Array[Byte], Array[(Array[Byte], Array[Byte], Array[Byte])])](
        (
          Bytes.toBytes("put1"),
          Array((Bytes.toBytes(columnFamily), Bytes.toBytes("a"), Bytes.toBytes("foo1")))),
        (
          Bytes.toBytes("put2"),
          Array((Bytes.toBytes(columnFamily), Bytes.toBytes("b"), Bytes.toBytes("foo2")))),
        (
          Bytes.toBytes("put3"),
          Array((Bytes.toBytes(columnFamily), Bytes.toBytes("c"), Bytes.toBytes("foo3"))))))

    hbaseContext.bulkPut[(Array[Byte], Array[(Array[Byte], Array[Byte], Array[Byte])])](
      rdd,
      TableName.valueOf(tableName),
      (putRecord) => {
        val put = new Put(putRecord._1)
        putRecord._2.foreach((putValue) => put.addColumn(putValue._1, putValue._2, putValue._3))
        put
      })

    val connection = ConnectionFactory.createConnection(TEST_UTIL.getConfiguration)
    val table = connection.getTable(TableName.valueOf(tableName))
    try {
      val foo1 = Bytes.toString(
        CellUtil.cloneValue(
          table
            .get(new Get(Bytes.toBytes("put1")))
            .getColumnLatestCell(Bytes.toBytes(columnFamily), Bytes.toBytes("a"))))
      assert(foo1 == "foo1")

      val foo2 = Bytes.toString(
        CellUtil.cloneValue(
          table
            .get(new Get(Bytes.toBytes("put2")))
            .getColumnLatestCell(Bytes.toBytes(columnFamily), Bytes.toBytes("b"))))
      assert(foo2 == "foo2")

      val foo3 = Bytes.toString(
        CellUtil.cloneValue(
          table
            .get(new Get(Bytes.toBytes("put3")))
            .getColumnLatestCell(Bytes.toBytes(columnFamily), Bytes.toBytes("c"))))
      assert(foo3 == "foo3")
    } finally {
      table.close()
      connection.close()
    }
  }

  test("bulkDelete to test HBase client") {
    val connection = ConnectionFactory.createConnection(TEST_UTIL.getConfiguration)
    val table = connection.getTable(TableName.valueOf(tableName))

    try {
      var put = new Put(Bytes.toBytes("delete1"))
      put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("a"), Bytes.toBytes("foo1"))
      table.put(put)
      put = new Put(Bytes.toBytes("delete2"))
      put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("a"), Bytes.toBytes("foo2"))
      table.put(put)
      put = new Put(Bytes.toBytes("delete3"))
      put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("a"), Bytes.toBytes("foo3"))
      table.put(put)
    } finally {
      table.close()
      connection.close()
    }

    val rdd = sc.parallelize(Array[Array[Byte]](Bytes.toBytes("delete1"), Bytes.toBytes("delete3")))

    hbaseContext.bulkDelete[Array[Byte]](
      rdd,
      TableName.valueOf(tableName),
      putRecord => new Delete(putRecord),
      4)

    val connection2 = ConnectionFactory.createConnection(TEST_UTIL.getConfiguration)
    val table2 = connection2.getTable(TableName.valueOf(tableName))
    try {
      assert(
        table2
          .get(new Get(Bytes.toBytes("delete1")))
          .getColumnLatestCell(Bytes.toBytes(columnFamily), Bytes.toBytes("a")) == null)
      assert(
        table2
          .get(new Get(Bytes.toBytes("delete3")))
          .getColumnLatestCell(Bytes.toBytes(columnFamily), Bytes.toBytes("a")) == null)
      assert(
        Bytes
          .toString(
            CellUtil.cloneValue(table2
              .get(new Get(Bytes.toBytes("delete2")))
              .getColumnLatestCell(Bytes.toBytes(columnFamily), Bytes.toBytes("a"))))
          .equals("foo2"))
    } finally {
      table2.close()
      connection2.close()
    }
  }

  test("bulkGet to test HBase client") {
    val connection = ConnectionFactory.createConnection(TEST_UTIL.getConfiguration)
    val table = connection.getTable(TableName.valueOf(tableName))

    try {
      var put = new Put(Bytes.toBytes("get1"))
      put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("a"), Bytes.toBytes("foo1"))
      table.put(put)
      put = new Put(Bytes.toBytes("get2"))
      put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("a"), Bytes.toBytes("foo2"))
      table.put(put)
      put = new Put(Bytes.toBytes("get3"))
      put.addColumn(Bytes.toBytes(columnFamily), Bytes.toBytes("a"), Bytes.toBytes("foo3"))
      table.put(put)
    } finally {
      table.close()
      connection.close()
    }

    val rdd = sc.parallelize(
      Array[Array[Byte]](
        Bytes.toBytes("get1"),
        Bytes.toBytes("get2"),
        Bytes.toBytes("get3"),
        Bytes.toBytes("get4")))

    val getRdd = hbaseContext.bulkGet[Array[Byte], String](
      TableName.valueOf(tableName),
      2,
      rdd,
      record => {
        new Get(record)
      },
      (result: Result) => {
        if (result.listCells() != null) {
          val it = result.listCells().iterator()
          val B = new StringBuilder

          B.append(Bytes.toString(result.getRow) + ":")

          while (it.hasNext) {
            val cell = it.next()
            val q = Bytes.toString(CellUtil.cloneQualifier(cell))
            if (q.equals("counter")) {
              B.append("(" + q + "," + Bytes.toLong(CellUtil.cloneValue(cell)) + ")")
            } else {
              B.append("(" + q + "," + Bytes.toString(CellUtil.cloneValue(cell)) + ")")
            }
          }
          B.toString
        } else {
          ""
        }
      })
    val getArray = getRdd.collect()

    assert(getArray.length == 4)
    assert(getArray.contains("get1:(a,foo1)"))
    assert(getArray.contains("get2:(a,foo2)"))
    assert(getArray.contains("get3:(a,foo3)"))
  }

  test("foreachPartition with connection") {
    val tName = tableName
    val cf = columnFamily
    val rdd = sc.parallelize(
      Array[(Array[Byte], Array[Byte])](
        (Bytes.toBytes("fp1"), Bytes.toBytes("value_fp1")),
        (Bytes.toBytes("fp2"), Bytes.toBytes("value_fp2")),
        (Bytes.toBytes("fp3"), Bytes.toBytes("value_fp3"))))

    hbaseContext.foreachPartition[(Array[Byte], Array[Byte])](
      rdd,
      (it, connection) => {
        val m = connection.getBufferedMutator(TableName.valueOf(tName))
        it.foreach {
          case (rowKey, value) =>
            val put = new Put(rowKey)
            put.addColumn(Bytes.toBytes(cf), Bytes.toBytes("a"), value)
            m.mutate(put)
        }
        m.flush()
        m.close()
      })

    val connection = ConnectionFactory.createConnection(TEST_UTIL.getConfiguration)
    val table = connection.getTable(TableName.valueOf(tableName))
    try {
      val v1 = Bytes.toString(
        CellUtil.cloneValue(
          table
            .get(new Get(Bytes.toBytes("fp1")))
            .getColumnLatestCell(Bytes.toBytes(columnFamily), Bytes.toBytes("a"))))
      assert(v1 == "value_fp1")

      val v2 = Bytes.toString(
        CellUtil.cloneValue(
          table
            .get(new Get(Bytes.toBytes("fp2")))
            .getColumnLatestCell(Bytes.toBytes(columnFamily), Bytes.toBytes("a"))))
      assert(v2 == "value_fp2")

      val v3 = Bytes.toString(
        CellUtil.cloneValue(
          table
            .get(new Get(Bytes.toBytes("fp3")))
            .getColumnLatestCell(Bytes.toBytes(columnFamily), Bytes.toBytes("a"))))
      assert(v3 == "value_fp3")
    } finally {
      table.close()
      connection.close()
    }
  }

  test("mapPartitions with connection") {
    val tName = tableName
    val cf = columnFamily
    val connection = ConnectionFactory.createConnection(TEST_UTIL.getConfiguration)
    val table = connection.getTable(TableName.valueOf(tName))
    try {
      var put = new Put(Bytes.toBytes("mp1"))
      put.addColumn(Bytes.toBytes(cf), Bytes.toBytes("a"), Bytes.toBytes("val_mp1"))
      table.put(put)
      put = new Put(Bytes.toBytes("mp2"))
      put.addColumn(Bytes.toBytes(cf), Bytes.toBytes("a"), Bytes.toBytes("val_mp2"))
      table.put(put)
    } finally {
      table.close()
      connection.close()
    }

    val rdd = sc.parallelize(Array[Array[Byte]](Bytes.toBytes("mp1"), Bytes.toBytes("mp2")))

    val resultRdd = hbaseContext.mapPartitions[Array[Byte], String](
      rdd,
      (it, conn) => {
        val tbl = conn.getTable(TableName.valueOf(tName))
        try {
          val res = new ListBuffer[String]()
          it.foreach { rowKey =>
            val result = tbl.get(new Get(rowKey))
            val cell = result.getColumnLatestCell(Bytes.toBytes(cf), Bytes.toBytes("a"))
            if (cell != null) {
              res += Bytes.toString(result.getRow) + "=" + Bytes.toString(CellUtil.cloneValue(cell))
            }
          }
          res.iterator
        } finally {
          tbl.close()
        }
      })

    val results = resultRdd.collect().sorted
    assert(results.length == 2)
    assert(results.contains("mp1=val_mp1"))
    assert(results.contains("mp2=val_mp2"))
  }
}
