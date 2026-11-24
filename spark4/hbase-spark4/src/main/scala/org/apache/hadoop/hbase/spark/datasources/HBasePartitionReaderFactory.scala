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

import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.connector.read.{InputPartition, PartitionReader, PartitionReaderFactory}
import org.apache.spark.sql.sources.Filter
import org.apache.spark.sql.types.StructType
import org.apache.yetus.audience.InterfaceAudience

/**
 * This is a new class in the spark4 module. Implements PartitionReaderFactory for
 * creating HBasePartitionReader instances on executors.
 *
 * In the spark 3 DS V1 model, there was no factory, the RDD.compute(partition) call directly created the iterator.
 *
 *
 * @param requiredSchema
 * @param properties
 * @param catalog
 * @param pushedFilters
 * @param encoderClsName
 * @param usePushDownColumnFilter
 */
@InterfaceAudience.Private
class HBasePartitionReaderFactory(
    requiredSchema: StructType,
    properties: Map[String, String],
    catalog: HBaseTableCatalog,
    pushedFilters: Array[Filter],
    encoderClsName: String,
    usePushDownColumnFilter: Boolean,
    wrappedConf: SerializableConfiguration,
    readOptions: HBaseReadOptions)
    extends PartitionReaderFactory
    with Serializable {

  override def createReader(partition: InputPartition): PartitionReader[InternalRow] = {
    val hbasePartition = partition.asInstanceOf[HBaseInputPartition]
    new HBasePartitionReader(
      hbasePartition,
      requiredSchema,
      properties,
      catalog,
      pushedFilters,
      encoderClsName,
      usePushDownColumnFilter,
      wrappedConf,
      readOptions)
  }
}
