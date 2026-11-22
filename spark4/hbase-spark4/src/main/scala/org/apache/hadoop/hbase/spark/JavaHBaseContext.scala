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

import java.util.Map
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.{Connection, Delete, Get, Put, Result, Scan}
import org.apache.hadoop.hbase.io.ImmutableBytesWritable
import org.apache.hadoop.hbase.util.Pair
import org.apache.spark.api.java.{JavaRDD, JavaSparkContext}
import org.apache.spark.api.java.function.{FlatMapFunction, Function, VoidFunction}
import org.apache.yetus.audience.InterfaceAudience
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

/**
 * Java facade for the batch operations of HBaseContext. Configuration distribution and
 * connection management are delegated to HBaseContext.
 *
 * @param jsc    the JavaSparkContext used to execute HBase operations
 * @param config the HBase configuration
 */
@InterfaceAudience.Public
class JavaHBaseContext(@transient val jsc: JavaSparkContext, @transient val config: Configuration)
    extends Serializable {
  val hbaseContext = new HBaseContext(jsc.sc, config)

  /**
   * Applies a Java function to each partition with an HBase Connection. The function must
   * not close the supplied Connection; it must close any Tables or mutators it creates.
   */
  def foreachPartition[T](
      javaRdd: JavaRDD[T],
      f: VoidFunction[(java.util.Iterator[T], Connection)]): Unit = {
    hbaseContext.foreachPartition(
      javaRdd.rdd,
      (it: Iterator[T], conn: Connection) => f.call((it.asJava, conn)))
  }

  /**
   * Maps each partition using a Java iterator and an HBase Connection. The function must
   * not close the supplied Connection; it must close any Tables or scanners it creates.
   */
  def mapPartitions[T, R](
      javaRdd: JavaRDD[T],
      f: FlatMapFunction[(java.util.Iterator[T], Connection), R]): JavaRDD[R] = {
    JavaRDD.fromRDD(
      hbaseContext.mapPartitions[T, R](
        javaRdd.rdd,
        (it: Iterator[T], conn: Connection) => f.call((it.asJava, conn)).asScala)(fakeClassTag[R]))(
      fakeClassTag[R])
  }

  /** Writes the Puts produced by the Java function, flushing them before returning. */
  def bulkPut[T](javaRdd: JavaRDD[T], tableName: TableName, f: Function[T, Put]): Unit = {
    hbaseContext.bulkPut(javaRdd.rdd, tableName, (t: T) => f.call(t))
  }

  /** Deletes rows in batches using the Deletes produced by the Java function. */
  def bulkDelete[T](
      javaRdd: JavaRDD[T],
      tableName: TableName,
      f: Function[T, Delete],
      batchSize: Integer): Unit = {
    hbaseContext.bulkDelete(javaRdd.rdd, tableName, (t: T) => f.call(t), batchSize)
  }

  /**
   * Executes Gets in batches and transforms each Result, including empty Results, using
   * the supplied Java function.
   */
  def bulkGet[T, U](
      tableName: TableName,
      batchSize: Integer,
      javaRdd: JavaRDD[T],
      makeGet: Function[T, Get],
      convertResult: Function[Result, U]): JavaRDD[U] = {
    JavaRDD.fromRDD(
      hbaseContext.bulkGet[T, U](
        tableName,
        batchSize,
        javaRdd.rdd,
        (t: T) => makeGet.call(t),
        (r: Result) => convertResult.call(r))(fakeClassTag[U]))(fakeClassTag[U])
  }

  /**
   * Generates HFiles in stagingDir, with one cell produced by mapFunc for each input record.
   * The caller must subsequently load the HFiles into HBase, for example with
   * LoadIncrementalHFiles.doBulkLoad.
   *
   * @param familyHFileWriteOptionsMap per-family HFile write options
   * @param compactionExclude whether to exclude the HFiles from minor compaction
   * @param maxSize maximum HFile size before rolling at a row boundary
   */
  def bulkLoad[T](
      javaRdd: JavaRDD[T],
      tableName: TableName,
      mapFunc: Function[T, Pair[KeyFamilyQualifier, Array[Byte]]],
      stagingDir: String,
      familyHFileWriteOptionsMap: Map[Array[Byte], FamilyHFileWriteOptions],
      compactionExclude: Boolean,
      maxSize: Long): Unit = {
    hbaseContext.bulkLoad[Pair[KeyFamilyQualifier, Array[Byte]]](
      javaRdd.map(mapFunc).rdd,
      tableName,
      t => Iterator.single((t.getFirst, t.getSecond)),
      stagingDir,
      familyHFileWriteOptionsMap,
      compactionExclude,
      maxSize)
  }

  /**
   * Generates HFiles in stagingDir from records containing all cells of a row. Each row key
   * must occur in only one input record. The caller must subsequently load the HFiles into
   * HBase, for example with LoadIncrementalHFiles.doBulkLoad.
   *
   * @param familyHFileWriteOptionsMap per-family HFile write options
   * @param compactionExclude whether to exclude the HFiles from minor compaction
   * @param maxSize maximum HFile size before rolling at a row boundary
   */
  def bulkLoadThinRows[T](
      javaRdd: JavaRDD[T],
      tableName: TableName,
      mapFunc: Function[T, Pair[ByteArrayWrapper, FamiliesQualifiersValues]],
      stagingDir: String,
      familyHFileWriteOptionsMap: Map[Array[Byte], FamilyHFileWriteOptions],
      compactionExclude: Boolean,
      maxSize: Long): Unit = {
    hbaseContext.bulkLoadThinRows[Pair[ByteArrayWrapper, FamiliesQualifiersValues]](
      javaRdd.map(mapFunc).rdd,
      tableName,
      t => (t.getFirst, t.getSecond),
      stagingDir,
      familyHFileWriteOptionsMap,
      compactionExclude,
      maxSize)
  }

  /** Scans an HBase table and transforms each key/Result pair using a Java function. */
  def hbaseRDD[U](
      tableName: TableName,
      scans: Scan,
      f: Function[(ImmutableBytesWritable, Result), U]): JavaRDD[U] = {
    JavaRDD.fromRDD(hbaseContext.hbaseRDD[U](tableName, scans, v => f.call(v))(fakeClassTag[U]))(
      fakeClassTag[U])
  }

  /** Scans an HBase table, returning its raw key/Result pairs. */
  def hbaseRDD(tableName: TableName, scans: Scan): JavaRDD[(ImmutableBytesWritable, Result)] = {
    JavaRDD.fromRDD(hbaseContext.hbaseRDD(tableName, scans))
  }

  // Java generic results are reference types, as in Spark's own JavaRDD adapters.
  private def fakeClassTag[T]: ClassTag[T] = ClassTag.AnyRef.asInstanceOf[ClassTag[T]]
}
