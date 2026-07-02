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

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.{CellUtil, TableName}
import org.apache.hadoop.hbase.client.{Result, Scan}
import org.apache.hadoop.hbase.io.ImmutableBytesWritable
import org.apache.hadoop.hbase.mapreduce.{IdentityTableMapper, TableInputFormat, TableMapReduceUtil}
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.mapred.JobConf
import org.apache.hadoop.mapreduce.Job
import org.apache.hadoop.security.UserGroupInformation
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.{SerializableWritable, SparkContext}
import org.apache.yetus.audience.InterfaceAudience

import scala.reflect.ClassTag

/**
 * Narrow Spark 4 port of `org.apache.hadoop.hbase.spark.HBaseContext`: enough for datasource scans
 * via `NewHBaseRDD` / TableInputFormat. Bulk load, streaming, and imperative helpers are intentionally
 * omitted for now and will be implemented later (see HBASE-30178).
 */
@InterfaceAudience.Public
class HBaseContext(@transient val sc: SparkContext, @transient val config: Configuration)
  extends Serializable
    with Logging {

  @transient protected var tmpHdfsConfiguration: Configuration = config

  {
    val j = Job.getInstance(config)
    TableMapReduceUtil.initCredentials(j)
  }

  val broadcastedConf: Broadcast[SerializableWritable[Configuration]] =
    sc.broadcast(new SerializableWritable(config))

  LatestHBaseContextCache.latest = this

  /**
   * Produces an RDD of type U by scanning an HBase table and applying a transformation function
   * to each (key, result) pair.
   *
   * @param tableName the name of the HBase table to scan
   * @param scan      the Scan object defining the read parameters
   * @param f         transformation function applied to each (ImmutableBytesWritable, Result) pair
   * @tparam U        the element type of the resulting RDD
   * @return          RDD containing the transformed scan results
   */
  def hbaseRDD[U: ClassTag](
                             tableName: TableName,
                             scan: Scan,
                             f: ((ImmutableBytesWritable, Result)) => U): RDD[U] = {
    applyMapFunction(buildNewHBaseRDD(tableName, scan), f)
  }

  private def buildNewHBaseRDD(
                                tableName: TableName,
                                scan: Scan): NewHBaseRDD[ImmutableBytesWritable, Result] = {

    val mapJob = Job.getInstance(getConf(broadcastedConf))
    TableMapReduceUtil.initCredentials(mapJob)
    TableMapReduceUtil.initTableMapperJob(
      tableName,
      scan,
      classOf[IdentityTableMapper],
      null,
      null,
      mapJob)

    val jconf = new JobConf(mapJob.getConfiguration)
    val jobCreds = jconf.getCredentials()
    UserGroupInformation.setConfiguration(sc.hadoopConfiguration)
    jobCreds.mergeAll(UserGroupInformation.getCurrentUser().getCredentials())

    new NewHBaseRDD(
      sc,
      classOf[TableInputFormat],
      classOf[ImmutableBytesWritable],
      classOf[Result],
      mapJob.getConfiguration,
      this)
  }

  private def applyMapFunction[U: ClassTag](
                                             rdd: RDD[(ImmutableBytesWritable, Result)],
                                             f: ((ImmutableBytesWritable, Result)) => U): RDD[U] = {
    rdd.map(f)
  }

  /**
   * Produces an RDD of raw (ImmutableBytesWritable, Result) pairs by scanning an HBase table.
   *
   * @param tableName the name of the HBase table to scan
   * @param scans     the Scan object defining the read parameters
   * @return          RDD of (ImmutableBytesWritable, Result) pairs
   */
  def hbaseRDD(
                tableName: TableName,
                scans: Scan): RDD[(ImmutableBytesWritable, Result)] = {
    hbaseRDD[(ImmutableBytesWritable, Result)](
      tableName,
      scans,
      (r: (ImmutableBytesWritable, Result)) => r)
  }

  /**
   * Scans an HBase table and returns an RDD of Spark SQL Rows, extracting the specified columns
   * from each result. This method handles cell-to-Row conversion internally, avoiding Spark 4
   * closure serialization issues that arise when client code maps over raw HBase results in
   * spark-shell.
   *
   * @param tableName the name of the HBase table to scan
   * @param scan      the Scan object defining the read parameters
   * @param columns   ordered sequence of column qualifiers to extract from each row
   * @return          RDD[Row] with one field per column (null if a column is absent in a result)
   */
  def hbaseRDDAsRows(
                      tableName: String,
                      scan: Scan,
                      columns: Seq[String]): RDD[Row] = {
    val rdd = buildNewHBaseRDD(TableName.valueOf(tableName), scan)
    val cols = columns.toArray
    rdd.map { case (_, result) =>
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
  }

  private[hbase] def getConf(
                              configBroadcast: Broadcast[SerializableWritable[Configuration]]): Configuration = {

    if (tmpHdfsConfiguration == null) {
      try {
        tmpHdfsConfiguration = configBroadcast.value.value
      } catch {
        case ex: Exception => logError("Unable to getConfig from broadcast", ex)
      }
    }
    tmpHdfsConfiguration
  }
}

/** Same semantics as Spark 3: last constructed `HBaseContext` wins. */
@InterfaceAudience.Private
object LatestHBaseContextCache {
  var latest: HBaseContext = null
}