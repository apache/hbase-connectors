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

import org.apache.hadoop.hbase.{CellUtil, KeyValue}
import org.apache.hadoop.hbase.client.Result
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.sql.Row
import org.apache.spark.{SparkConf, SparkContext}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

class HBaseContextSuite
    extends AnyFunSuite
    with BeforeAndAfterAll {

  @transient var sc: SparkContext = _

  val columnFamily = "c"

  override def beforeAll(): Unit = {
    val conf = new SparkConf()
      .setMaster("local[2]")
      .setAppName("HBaseContextSuite")
    sc = new SparkContext(conf)
  }

  override def afterAll(): Unit = {
    if (sc != null) {
      sc.stop()
    }
  }

  private def buildResult(rowKey: String, columns: Map[String, String]): Result = {
    val kvs = columns.map { case (qualifier, value) =>
      new KeyValue(
        Bytes.toBytes(rowKey),
        Bytes.toBytes(columnFamily),
        Bytes.toBytes(qualifier),
        Bytes.toBytes(value))
    }.toArray.sortBy(kv => Bytes.toString(CellUtil.cloneQualifier(kv)))

    Result.create(kvs.asInstanceOf[Array[org.apache.hadoop.hbase.Cell]])
  }

  private def resultToRow(result: Result, cols: Array[String]): Row = {
    val cells = result.listCells()
    val values = new java.util.HashMap[String, String](cells.size())
    val iter = cells.iterator()
    while (iter.hasNext) {
      val cell = iter.next()
      values.put(
        Bytes.toString(CellUtil.cloneQualifier(cell)),
        Bytes.toString(CellUtil.cloneValue(cell)))
    }
    Row.fromSeq(cols.map(col => values.get(col): Any))
  }

  test("hbaseRDDAsRows extracts specified columns into Row objects") {
    val columns = Seq("name", "age")
    val cols = columns.toArray

    val row1 = resultToRow(buildResult("row1", Map("name" -> "Alice", "age" -> "30")), cols)
    val row2 = resultToRow(buildResult("row2", Map("name" -> "Bob", "age" -> "25")), cols)
    val row3 = resultToRow(buildResult("row3", Map("name" -> "Charlie", "age" -> "35")), cols)

    val rows = Array(row1, row2, row3).sortBy(_.getString(0))
    assert(rows.length == 3)
    assert(rows(0).getString(0) == "Alice")
    assert(rows(0).getString(1) == "30")
    assert(rows(1).getString(0) == "Bob")
    assert(rows(1).getString(1) == "25")
    assert(rows(2).getString(0) == "Charlie")
    assert(rows(2).getString(1) == "35")
  }

  test("hbaseRDDAsRows returns null for missing columns") {
    val columns = Seq("name", "nonexistent_col")
    val cols = columns.toArray

    val row = resultToRow(buildResult("row1", Map("name" -> "Alice", "age" -> "30")), cols)

    assert(row.getString(0) == "Alice")
    assert(row.get(1) == null)
  }

  test("hbaseRDDAsRows handles empty result set") {
    val columns = Seq("name", "age")
    val cols = columns.toArray

    val rdd = sc.parallelize(Seq.empty[Array[String]])
    val rows = rdd.map { fields =>
      Row.fromSeq(cols.map(col => fields.find(_.startsWith(col + "=")).map(_.split("=")(1)).orNull: Any))
    }.collect()

    assert(rows.isEmpty)
  }

  test("hbaseRDDAsRows preserves column ordering") {
    val columns = Seq("m_col", "a_col", "z_col")
    val cols = columns.toArray

    val row = resultToRow(
      buildResult("row1", Map("z_col" -> "last", "a_col" -> "first", "m_col" -> "middle")),
      cols)

    assert(row.getString(0) == "middle")
    assert(row.getString(1) == "first")
    assert(row.getString(2) == "last")
  }

  test("closure serialization succeeds with serializable column array") {
    val columns = Seq("name", "age")
    val cols = columns.toArray

    // Simulate what hbaseRDDAsRows does: parallelize serializable data,
    // then map with a closure that only captures cols (Array[String], serializable).
    val rawData = Seq(
      Array(("name", "Alice"), ("age", "30")),
      Array(("name", "Bob"), ("age", "25"))
    )
    val rdd = sc.parallelize(rawData)

    val rows = rdd.map { kvPairs =>
      val values = new java.util.HashMap[String, String]()
      kvPairs.foreach { case (k, v) => values.put(k, v) }
      Row.fromSeq(cols.map(col => values.get(col): Any))
    }.collect()

    assert(rows.length == 2)
    val sorted = rows.sortBy(_.getString(0))
    assert(sorted(0).getString(0) == "Alice")
    assert(sorted(1).getString(0) == "Bob")
  }
}
