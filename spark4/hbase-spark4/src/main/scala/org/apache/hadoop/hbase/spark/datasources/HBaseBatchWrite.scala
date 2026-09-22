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
import org.apache.spark.sql.connector.write.{BatchWrite, DataWriterFactory, PhysicalWriteInfo,
  WriterCommitMessage}
import org.apache.spark.sql.types.StructType
import org.apache.yetus.audience.InterfaceAudience

/**
 * This is a new class in the spark4 module. Implements BatchWrite for the DS V2 write path.
 * Runs on the driver. Optionally creates the HBase table (when the "newtable" option is set),
 * then produces an HBaseDataWriterFactory that is serialized to executors.
 *
 * In the spark 3 DS V1 model, table creation was in HBaseRelation.createTable() and the write
 * was driven by InsertableRelation.insert() using saveAsHadoopDataset with TableOutputFormat.
 *
 * @param schema
 * @param properties
 */
@InterfaceAudience.Private
class HBaseBatchWrite(schema: StructType, properties: Map[String, String])
    extends BatchWrite
    with Logging {

  private val catalog = HBaseTableCatalog(properties)

  createTableIfNeeded()

  override def createBatchWriterFactory(info: PhysicalWriteInfo): DataWriterFactory = {
    val hadoopConf = SparkSession.active.sparkContext.hadoopConfiguration
    val hbaseConf = HBaseConfiguration.create(hadoopConf)
    properties.get(HBaseSparkConf.HBASE_CONFIG_LOCATION)
      .foreach(_.split(",").foreach(r => hbaseConf.addResource(new Path(r))))
    val wrappedConf = new SerializableConfiguration(hbaseConf)

    new HBaseDataWriterFactory(schema, properties, catalog, wrappedConf)
  }

  override def useCommitCoordinator(): Boolean = false

  override def commit(messages: Array[WriterCommitMessage]): Unit = {}

  override def abort(messages: Array[WriterCommitMessage]): Unit = {}

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
