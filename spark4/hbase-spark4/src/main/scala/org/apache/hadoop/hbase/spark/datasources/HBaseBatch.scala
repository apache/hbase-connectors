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
import org.apache.hadoop.hbase.spark.{HBaseConnectionCache, Logging}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.connector.read.{Batch, InputPartition, PartitionReaderFactory}
import org.apache.spark.sql.sources._
import org.apache.spark.sql.types.StructType
import org.apache.yetus.audience.InterfaceAudience

/**
 * This is a new class in the spark4 module. Implements Batch.
 * Responsible for physical planning: splits the read into partitions.
 * Calls RegionLocator.getStartEndKeys() to discover HBase regions,
 * intersects them with the row key filter's scan ranges, and produces an array of InputPartition objects.
 *
 * In the spark 3 DS V1 model, this logic was inside HBaseTableScanRDD.getPartitions().
 *
 * @param requiredSchema
 * @param properties
 * @param catalog
 * @param rowKeyFilter
 * @param pushedFilters
 * @param encoderClsName
 */
@InterfaceAudience.Private
class HBaseBatch(
    requiredSchema: StructType,
    properties: Map[String, String],
    catalog: HBaseTableCatalog,
    rowKeyFilter: RowKeyFilter,
    pushedFilters: Array[Filter],
    encoderClsName: String,
    hasNullCheck: Boolean)
    extends Batch
    with Logging {

  override def planInputPartitions(): Array[InputPartition] = {
    val hadoopConf = SparkSession.active.sparkContext.hadoopConfiguration
    val conf = HBaseConfiguration.create(hadoopConf)
    val configResources = properties.get(HBaseSparkConf.HBASE_CONFIG_LOCATION)
    configResources.foreach(_.split(",").foreach(r => conf.addResource(new Path(r))))

    val connection = HBaseConnectionCache.getConnection(conf)
    try {
      val tableName = s"${catalog.namespace}:${catalog.name}"
      val regionLocator = connection.getRegionLocator(TableName.valueOf(tableName))
      try {
        val keys = regionLocator.getStartEndKeys
        val startKeys = keys.getFirst
        val endKeys = keys.getSecond

        val regions = startKeys.zip(endKeys).zipWithIndex.map { case ((start, end), idx) =>
          HBaseRegion(idx, Some(start), Some(end),
            Some(regionLocator.getRegionLocation(start).getHostname))
        }

        val scanRanges = rowKeyFilter.ranges.toSeq.map { sr =>
          Range(
            Option(sr.lowerBound).filter(_.nonEmpty).map(Bound(_, sr.isLowerBoundEqualTo)),
            Option(sr.upperBound).map(Bound(_, sr.isUpperBoundEqualTo)))
        }
        val points = rowKeyFilter.points.toSeq

        regions.flatMap { region =>
          val regionRange = Range(region)
          val intersectedRanges = Ranges.and(regionRange, scanRanges)
          val intersectedPoints = Points.and(regionRange, points)

          if (intersectedRanges.nonEmpty || intersectedPoints.nonEmpty) {
            Some(HBaseInputPartition(
              region.index, intersectedRanges, intersectedPoints, region.server): InputPartition)
          } else {
            None
          }
        }
      } finally {
        regionLocator.close()
      }
    } finally {
      connection.close()
    }
  }

  override def createReaderFactory(): PartitionReaderFactory = {
    val usePushDownColumnFilter = properties
      .get(HBaseSparkConf.PUSHDOWN_COLUMN_FILTER)
      .map(_.toBoolean)
      .getOrElse(HBaseSparkConf.DEFAULT_PUSHDOWN_COLUMN_FILTER)

    val hadoopConf = SparkSession.active.sparkContext.hadoopConfiguration
    val hbaseConf = HBaseConfiguration.create(hadoopConf)
    properties.get(HBaseSparkConf.HBASE_CONFIG_LOCATION)
      .foreach(_.split(",").foreach(r => hbaseConf.addResource(new Path(r))))
    val wrappedConf = new SerializableConfiguration(hbaseConf)

    new HBasePartitionReaderFactory(
      requiredSchema,
      properties,
      catalog,
      pushedFilters,
      encoderClsName,
      usePushDownColumnFilter,
      wrappedConf,
      hasNullCheck)
  }
}
