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

import org.apache.hadoop.hbase.{HBaseConfiguration, TableName}
import org.apache.hadoop.hbase.spark.{HBaseConnectionCache, Logging}
import org.apache.spark.sql.connector.read.{Batch, InputPartition, PartitionReaderFactory}
import org.apache.spark.sql.sources._
import org.apache.spark.sql.types.StructType
import org.apache.yetus.audience.InterfaceAudience

@InterfaceAudience.Private
class HBaseBatch(
    requiredSchema: StructType,
    properties: Map[String, String],
    catalog: HBaseTableCatalog,
    rowKeyFilter: RowKeyFilter,
    pushedFilters: Array[Filter],
    encoderClsName: String)
    extends Batch
    with Logging {

  override def planInputPartitions(): Array[InputPartition] = {
    val conf = HBaseConfiguration.create()
    val configResources = properties.get(HBaseSparkConf.HBASE_CONFIG_LOCATION)
    configResources.foreach(_.split(",").foreach(r => conf.addResource(r)))

    val connection = HBaseConnectionCache.getConnection(conf)
    try {
      val tableName = s"${catalog.namespace}:${catalog.name}"
      val regionLocator = connection.getRegionLocator(TableName.valueOf(tableName))
      try {
        val keys = regionLocator.getStartEndKeys
        val startKeys = keys.getFirst
        val endKeys = keys.getSecond

        val regions = startKeys.zip(endKeys).zipWithIndex.map { case ((start, end), idx) =>
          HBaseRegion(idx, Some(start), Some(end))
        }

        val scanRanges = rowKeyFilter.ranges.toSeq
        val points = rowKeyFilter.points.toSeq

        if (scanRanges.isEmpty && points.isEmpty) {
          regions.map { region =>
            HBaseInputPartition(
              region.index,
              region.start.orNull,
              region.end.orNull): InputPartition
          }
        } else {
          regions.flatMap { region =>
            val regionRange = Range(region)
            val intersectedRanges = Ranges.and(regionRange, scanRanges.map { sr =>
              Range(
                Option(sr.lowerBound).filter(_.nonEmpty).map(Bound(_, sr.isLowerBoundEqualTo)),
                Option(sr.upperBound).map(Bound(_, sr.isUpperBoundEqualTo)))
            })
            val intersectedPoints = Points.and(regionRange, points.toSeq)

            if (intersectedRanges.nonEmpty || intersectedPoints.nonEmpty) {
              val startRow = intersectedRanges.headOption.flatMap(_.lower).map(_.b)
                .orElse(intersectedPoints.headOption)
                .orElse(region.start)
                .orNull
              val stopRow = intersectedRanges.lastOption.flatMap(_.upper).map(_.b)
                .orElse(intersectedPoints.lastOption.map(Utils.incrementByteArray))
                .orElse(region.end)
                .orNull
              Some(HBaseInputPartition(region.index, startRow, stopRow): InputPartition)
            } else {
              None
            }
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

    new HBasePartitionReaderFactory(
      requiredSchema,
      properties,
      catalog,
      pushedFilters,
      encoderClsName,
      usePushDownColumnFilter)
  }
}
