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
import org.apache.spark.sql.connector.read.{Batch, Scan}
import org.apache.spark.sql.sources._
import org.apache.spark.sql.types.StructType
import org.apache.yetus.audience.InterfaceAudience
import scala.collection.mutable.ListBuffer

@InterfaceAudience.Private
class HBaseScan(
    requiredSchema: StructType,
    properties: Map[String, String],
    catalog: HBaseTableCatalog,
    pushedFilters: Array[Filter],
    encoderClsName: String,
    @transient encoder: BytesEncoder)
    extends Scan
    with Logging {

  override def readSchema(): StructType = requiredSchema

  override def toBatch: Batch = {
    val rowKeyFilter = buildRowKeyFilter()
    new HBaseBatch(requiredSchema, properties, catalog, rowKeyFilter, pushedFilters, encoderClsName)
  }

  private def buildRowKeyFilter(): RowKeyFilter = {
    var superRowKeyFilter: RowKeyFilter = null
    val queryValueList = new ListBuffer[Array[Byte]]

    pushedFilters.foreach { f =>
      val rowKeyFilter = new RowKeyFilter()
      traverseFilterTree(rowKeyFilter, queryValueList, f)
      if (superRowKeyFilter == null) {
        superRowKeyFilter = rowKeyFilter
      } else {
        superRowKeyFilter.mergeIntersect(rowKeyFilter)
      }
    }

    if (superRowKeyFilter == null) {
      superRowKeyFilter = new RowKeyFilter
    }
    superRowKeyFilter
  }

  private def traverseFilterTree(
      parentRowKeyFilter: RowKeyFilter,
      valueArray: ListBuffer[Array[Byte]],
      filter: Filter): Unit = {
    filter match {
      case EqualTo(attr, value) =>
        val field = catalog.sMap.map.get(attr).orNull
        if (field != null && field.isRowKey) {
          parentRowKeyFilter.mergeIntersect(new RowKeyFilter(Utils.toBytes(value, field), null))
        }
      case LessThan(attr, value) =>
        val field = catalog.sMap.map.get(attr).orNull
        if (field != null && field.isRowKey) {
          val b = encoder.ranges(value)
          var inc = false
          b.map(_.less.map { x =>
            val r = new RowKeyFilter(null, new ScanRange(x.upper, inc, x.low, true))
            inc = true
            r
          }).map(x => x.reduce((i, j) => i.mergeUnion(j)))
            .map(parentRowKeyFilter.mergeIntersect(_))
        }
      case GreaterThan(attr, value) =>
        val field = catalog.sMap.map.get(attr).orNull
        if (field != null && field.isRowKey) {
          val b = encoder.ranges(value)
          var inc = false
          b.map(_.greater.map { x =>
            val r = new RowKeyFilter(null, new ScanRange(x.upper, true, x.low, inc))
            inc = true
            r
          }).map(x => x.reduce((i, j) => i.mergeUnion(j)))
            .map(parentRowKeyFilter.mergeIntersect(_))
        }
      case LessThanOrEqual(attr, value) =>
        val field = catalog.sMap.map.get(attr).orNull
        if (field != null && field.isRowKey) {
          val b = encoder.ranges(value)
          b.map(_.less.map(x => new RowKeyFilter(null, new ScanRange(x.upper, true, x.low, true))))
            .map(x => x.reduce((i, j) => i.mergeUnion(j)))
            .map(parentRowKeyFilter.mergeIntersect(_))
        }
      case GreaterThanOrEqual(attr, value) =>
        val field = catalog.sMap.map.get(attr).orNull
        if (field != null && field.isRowKey) {
          val b = encoder.ranges(value)
          b.map(
            _.greater.map(x => new RowKeyFilter(null, new ScanRange(x.upper, true, x.low, true))))
            .map(x => x.reduce((i, j) => i.mergeUnion(j)))
            .map(parentRowKeyFilter.mergeIntersect(_))
        }
      case StringStartsWith(attr, value) =>
        val field = catalog.sMap.map.get(attr).orNull
        if (field != null && field.isRowKey) {
          val p = Utils.toBytes(value, field)
          val endRange = Utils.incrementByteArray(p)
          parentRowKeyFilter.mergeIntersect(
            new RowKeyFilter(null, new ScanRange(endRange, false, p, true)))
        }
      case Or(left, right) =>
        traverseFilterTree(parentRowKeyFilter, valueArray, left)
        val rightSideRowKeyFilter = new RowKeyFilter
        traverseFilterTree(rightSideRowKeyFilter, valueArray, right)
        parentRowKeyFilter.mergeUnion(rightSideRowKeyFilter)
      case And(left, right) =>
        traverseFilterTree(parentRowKeyFilter, valueArray, left)
        val rightSideRowKeyFilter = new RowKeyFilter
        traverseFilterTree(rightSideRowKeyFilter, valueArray, right)
        parentRowKeyFilter.mergeIntersect(rightSideRowKeyFilter)
      case _ =>
    }
  }
}
