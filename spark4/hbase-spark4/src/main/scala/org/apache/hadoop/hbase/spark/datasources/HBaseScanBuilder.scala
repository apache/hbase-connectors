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

/**
 * This is a new class in the spark4 module.
 * Implements ScanBuilder, SupportsPushDownFilters, and SupportsPushDownRequiredColumns.
 * This is where Catalyst negotiates with the connector. Spark calls pushFilters() with
 * candidate predicates and the builder accepts what it can handle and returns the rest.
 * Spark calls pruneColumns() to say which columns it actually needs.
 * Then build() produces the final scan plan.
 *
 * In the spark 3 V1 model, this negotiation happened implicitly via
 * PrunedFilteredScan.buildScan(requiredColumns, filters) as a single method call with no back-and-forth.
 *
 * @param schema
 * @param properties
 */
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
  private var _rowKeyFilters: Array[Filter] = Array.empty
  private var requiredSchema: StructType = schema

  override def pushFilters(filters: Array[Filter]): Array[Filter] = {
    val hasCompositeRowKey = catalog.getRowKey.size > 1

    def isSupported(f: Filter): Boolean = f match {
      case EqualTo(attr, _) => isSupportedField(attr)
      case LessThan(attr, _) => isSupportedField(attr)
      case GreaterThan(attr, _) => isSupportedField(attr)
      case LessThanOrEqual(attr, _) => isSupportedField(attr)
      case GreaterThanOrEqual(attr, _) => isSupportedField(attr)
      case StringStartsWith(attr, _) => isSupportedField(attr)
      case IsNull(attr) => isSupportedField(attr)
      case IsNotNull(attr) => isSupportedField(attr)
      case Or(left, right) => isSupported(left) && isSupported(right)
      case And(left, right) => isSupported(left) && isSupported(right)
      case _ => false
    }

    def isSupportedField(attr: String): Boolean = {
      catalog.sMap.map.get(attr) match {
        case Some(field) => !(hasCompositeRowKey && field.isRowKey)
        case None => false
      }
    }

    val supported = new ListBuffer[Filter]()
    val unsupported = new ListBuffer[Filter]()

    filters.foreach { f =>
      if (isSupported(f)) supported += f
      else unsupported += f
    }

    _rowKeyFilters = supported.toArray

    val usePushDown = properties
      .get(HBaseSparkConf.PUSHDOWN_COLUMN_FILTER)
      .map(_.toBoolean)
      .getOrElse(HBaseSparkConf.DEFAULT_PUSHDOWN_COLUMN_FILTER)

    if (!usePushDown) {
      _pushedFilters = Array.empty
      return filters
    }

    _pushedFilters = supported.toArray
    unsupported.toArray
  }

  override def pushedFilters(): Array[Filter] = _pushedFilters

  override def pruneColumns(requiredSchema: StructType): Unit = {
    this.requiredSchema = requiredSchema
  }

  override def build(): Scan = {
    new HBaseScan(requiredSchema, properties, catalog, _pushedFilters, _rowKeyFilters,
      encoderClsName, encoder)
  }
}
