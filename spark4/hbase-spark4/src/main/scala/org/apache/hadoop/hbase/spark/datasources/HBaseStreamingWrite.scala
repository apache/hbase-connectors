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

import org.apache.hadoop.fs.Path
import org.apache.hadoop.hbase.{HBaseConfiguration, TableName}
import org.apache.hadoop.hbase.client.{ColumnFamilyDescriptorBuilder, TableDescriptorBuilder}
import org.apache.hadoop.hbase.spark.{HBaseConnectionCache, Logging}
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.connector.write.{PhysicalWriteInfo, WriterCommitMessage}
import org.apache.spark.sql.connector.write.streaming.{StreamingDataWriterFactory, StreamingWrite}
import org.apache.spark.sql.types.StructType
import org.apache.yetus.audience.InterfaceAudience

/**
 * This is a new class in the spark4 module. Implements StreamingWrite for the DS V2 streaming write path.
 * Runs on the driver. Optionally creates the HBase table (when the "newtable" option is set with a value
 * greater than 3), then produces an HBaseStreamingDataWriterFactory that is serialized to executors.
 *
 * The table creation and HBase configuration logic mirrors HBaseBatchWrite. Both commit() and abort()
 * are no-ops because HBase puts are idempotent and useCommitCoordinator() returns false.
 *
 * In the spark3 DStream model, streaming writes were driven by HBaseDStreamFunctions.hbaseBulkPut()
 * calling HBaseContext.streamBulkPut() which called bulkPut() on each micro-batch RDD.
 *
 * @param schema
 * @param properties
 */
@InterfaceAudience.Private
class HBaseStreamingWrite(schema: StructType, properties: Map[String, String])
    extends StreamingWrite
    with Logging {

  private val catalog = HBaseTableCatalog(properties)

  createTableIfNeeded()

  override def createStreamingWriterFactory(info: PhysicalWriteInfo): StreamingDataWriterFactory = {
    val hadoopConf = SparkSession.active.sparkContext.hadoopConfiguration
    val hbaseConf = HBaseConfiguration.create(hadoopConf)
    properties.get(HBaseSparkConf.HBASE_CONFIG_LOCATION)
      .foreach(_.split(",").foreach(r => hbaseConf.addResource(new Path(r))))
    val wrappedConf = new SerializableConfiguration(hbaseConf)

    new HBaseStreamingDataWriterFactory(schema, properties, catalog, wrappedConf)
  }

  override def useCommitCoordinator(): Boolean = false

  override def commit(epochId: Long, messages: Array[WriterCommitMessage]): Unit = {}

  override def abort(epochId: Long, messages: Array[WriterCommitMessage]): Unit = {}

  private def createTableIfNeeded(): Unit = {
    val numReg = properties
      .get(HBaseTableCatalog.newTable)
      .map(_.toInt)
      .getOrElse(0)

    if (numReg <= 3) {
      logInfo(s"${HBaseTableCatalog.newTable} is not defined or no larger than 3, " +
        "skip the create table")
      return
    }

    val startKey = Bytes.toBytes(
      properties.getOrElse(HBaseTableCatalog.regionStart, HBaseTableCatalog.defaultRegionStart))
    val endKey = Bytes.toBytes(
      properties.getOrElse(HBaseTableCatalog.regionEnd, HBaseTableCatalog.defaultRegionEnd))

    val hadoopConf = SparkSession.active.sparkContext.hadoopConfiguration
    val conf = HBaseConfiguration.create(hadoopConf)
    properties.get(HBaseSparkConf.HBASE_CONFIG_LOCATION)
      .foreach(_.split(",").foreach(r => conf.addResource(new Path(r))))

    val tName = TableName.valueOf(s"${catalog.namespace}:${catalog.name}")
    val connection = HBaseConnectionCache.getConnection(conf)
    val admin = connection.getAdmin
    try {
      if (!admin.tableExists(tName)) {
        val tableDescBuilder = TableDescriptorBuilder.newBuilder(tName)
        catalog.getColumnFamilies.foreach { cf =>
          logDebug(s"add family $cf to $tName")
          tableDescBuilder.setColumnFamily(
            ColumnFamilyDescriptorBuilder.newBuilder(Bytes.toBytes(cf)).build())
        }
        val splitKeys = Bytes.split(startKey, endKey, numReg)
        admin.createTable(tableDescBuilder.build(), splitKeys)
      }
    } finally {
      admin.close()
      connection.close()
    }
  }
}
