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

import org.apache.hadoop.hbase.spark.Logging
import org.apache.spark.sql.connector.read.{Scan, ScanBuilder, SupportsPushDownFilters, SupportsPushDownRequiredColumns}
import org.apache.spark.sql.sources._
import org.apache.spark.sql.types.StructType
import org.apache.yetus.audience.InterfaceAudience
import scala.collection.mutable.ListBuffer

@InterfaceAudience.Private
class HBaseScanBuilder(schema: StructType, properties: Map[String, String])
    extends ScanBuilder
    with SupportsPushDownFilters
    with SupportsPushDownRequiredColumns
    with Logging {

  private val catalog = HBaseTableCatalog(properties)
  private val encoderClsName =
    properties.getOrElse(HBaseSparkConf.QUERY_ENCODER, HBaseSparkConf.DEFAULT_QUERY_ENCODER)
  @transient private val encoder = JavaBytesEncoder.create(encoderClsName)

  private var _pushedFilters: Array[Filter] = Array.empty
  private var requiredSchema: StructType = schema

  override def pushFilters(filters: Array[Filter]): Array[Filter] = {
    val supported = new ListBuffer[Filter]()
    val unsupported = new ListBuffer[Filter]()

    filters.foreach {
      case f @ EqualTo(attr, _) if catalog.sMap.map.contains(attr) => supported += f
      case f @ LessThan(attr, _) if catalog.sMap.map.contains(attr) => supported += f
      case f @ GreaterThan(attr, _) if catalog.sMap.map.contains(attr) => supported += f
      case f @ LessThanOrEqual(attr, _) if catalog.sMap.map.contains(attr) => supported += f
      case f @ GreaterThanOrEqual(attr, _) if catalog.sMap.map.contains(attr) => supported += f
      case f @ StringStartsWith(attr, _) if catalog.sMap.map.contains(attr) => supported += f
      case f @ IsNull(attr) if catalog.sMap.map.contains(attr) => supported += f
      case f @ IsNotNull(attr) if catalog.sMap.map.contains(attr) => supported += f
      case f @ Or(_, _) => supported += f
      case f @ And(_, _) => supported += f
      case f => unsupported += f
    }

    _pushedFilters = supported.toArray
    unsupported.toArray
  }

  override def pushedFilters(): Array[Filter] = _pushedFilters

  override def pruneColumns(requiredSchema: StructType): Unit = {
    this.requiredSchema = requiredSchema
  }

  override def build(): Scan = {
    new HBaseScan(requiredSchema, properties, catalog, _pushedFilters, encoderClsName, encoder)
  }
}
