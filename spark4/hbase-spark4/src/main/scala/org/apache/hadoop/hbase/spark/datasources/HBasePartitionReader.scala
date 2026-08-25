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

import org.apache.hadoop.hbase.{CellUtil, HBaseConfiguration, TableName}
import org.apache.hadoop.hbase.client.{Result, ResultScanner, Scan}
import org.apache.hadoop.hbase.spark.{AndLogicExpression, DynamicLogicExpression,
  EqualLogicExpression, GreaterThanLogicExpression, GreaterThanOrEqualLogicExpression,
  HBaseConnectionCache, IsNullLogicExpression, LessThanLogicExpression,
  LessThanOrEqualLogicExpression, Logging, OrLogicExpression, PassThroughLogicExpression,
  PushdownMappedField, SmartConnection, SparkSQLPushDownFilter, StartsWithLogicExpression}
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow
import org.apache.spark.sql.connector.read.PartitionReader
import org.apache.spark.sql.sources._
import org.apache.spark.sql.types._
import org.apache.spark.unsafe.types.UTF8String
import org.apache.yetus.audience.InterfaceAudience
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

@InterfaceAudience.Private
class HBasePartitionReader(
    partition: HBaseInputPartition,
    requiredSchema: StructType,
    properties: Map[String, String],
    catalog: HBaseTableCatalog,
    pushedFilters: Array[Filter],
    encoderClsName: String,
    usePushDownColumnFilter: Boolean)
    extends PartitionReader[InternalRow]
    with Logging {

  private val conf = HBaseConfiguration.create()
  private val configResources = properties.get(HBaseSparkConf.HBASE_CONFIG_LOCATION)
  configResources.foreach(_.split(",").foreach(r => conf.addResource(r)))

  private val connection: SmartConnection = HBaseConnectionCache.getConnection(conf)
  private val tableName = s"${catalog.namespace}:${catalog.name}"
  private val table = connection.getTable(TableName.valueOf(tableName))

  private val scanner: ResultScanner = {
    val scan = new Scan()

    if (partition.startRow != null && partition.startRow.nonEmpty) {
      scan.withStartRow(partition.startRow)
    }
    if (partition.stopRow != null && partition.stopRow.nonEmpty) {
      scan.withStopRow(partition.stopRow)
    }

    val blockCacheEnable = properties
      .get(HBaseSparkConf.QUERY_CACHEBLOCKS)
      .map(_.toBoolean)
      .getOrElse(HBaseSparkConf.DEFAULT_QUERY_CACHEBLOCKS)
    scan.setCacheBlocks(blockCacheEnable)

    properties.get(HBaseSparkConf.QUERY_CACHEDROWS).map(_.toInt).foreach { rows =>
      if (rows > 0) scan.setCaching(rows)
    }
    properties.get(HBaseSparkConf.QUERY_BATCHSIZE).map(_.toInt).foreach { batch =>
      if (batch > 0) scan.setBatch(batch)
    }

    val requiredFields = requiredSchema.fieldNames.map(catalog.sMap.getField(_))
    val filterFields = extractFilterFields(pushedFilters)
    val scanFields = (requiredFields ++ filterFields).distinct.filterNot(_.isRowKey)

    scanFields.foreach { f =>
      scan.addColumn(f.cfBytes, f.colBytes)
    }

    if (usePushDownColumnFilter && pushedFilters.nonEmpty) {
      val valueArray = buildValueArray()
      val dynamicLogicExpression = buildDynamicLogicExpression()
      if (dynamicLogicExpression != null) {
        val columnMappings = scanFields.map { field =>
          new PushdownMappedField {
            override def colName(): String = field.colName
            override def cfBytes(): Array[Byte] = field.cfBytes
            override def colBytes(): Array[Byte] = field.colBytes
          }
        }
        val pushDownFilter = new SparkSQLPushDownFilter(
          dynamicLogicExpression,
          valueArray,
          columnMappings.toList.asJava,
          encoderClsName)
        scan.setFilter(pushDownFilter)
      }
    }

    table.getScanner(scan)
  }

  private var currentResult: Result = _

  override def next(): Boolean = {
    currentResult = scanner.next()
    currentResult != null
  }

  override def get(): InternalRow = {
    val fields = requiredSchema.fieldNames.map(catalog.sMap.getField(_))
    val rowKey = currentResult.getRow
    catalog.dynSetupRowKey(rowKey)
    val keyFields = catalog.getRowKey

    val keyValues = parseRowKey(rowKey, keyFields)
    val values = new Array[Any](fields.length)

    fields.zipWithIndex.foreach { case (field, idx) =>
      if (field.isRowKey) {
        values(idx) = convertToInternalRow(keyValues.get(field).orNull, field.dt)
      } else {
        val cell = currentResult.getColumnLatestCell(
          Bytes.toBytes(field.cf), Bytes.toBytes(field.col))
        if (cell == null || cell.getValueLength == 0) {
          values(idx) = null
        } else {
          val v = CellUtil.cloneValue(cell)
          val scalaValue = field.dt match {
            case BinaryType => v
            case _ => Utils.hbaseFieldToScalaType(field, v, 0, v.length)
          }
          values(idx) = convertToInternalRow(scalaValue, field.dt)
        }
      }
    }
    new GenericInternalRow(values)
  }

  override def close(): Unit = {
    if (scanner != null) scanner.close()
    if (table != null) table.close()
    if (connection != null) connection.close()
  }

  private def convertToInternalRow(value: Any, dataType: DataType): Any = {
    if (value == null) return null
    dataType match {
      case StringType => UTF8String.fromString(value.asInstanceOf[String])
      case _ => value
    }
  }

  private def parseRowKey(row: Array[Byte], keyFields: Seq[Field]): Map[Field, Any] = {
    keyFields
      .foldLeft((0, Seq[(Field, Any)]())) { (state, field) =>
        val idx = state._1
        val parsed = state._2
        if (field.length != -1) {
          val value = Utils.hbaseFieldToScalaType(field, row, idx, field.length)
          (idx + field.length, parsed :+ (field, value))
        } else {
          field.dt match {
            case StringType =>
              val pos = row.indexOf(HBaseTableCatalog.delimiter, idx)
              if (pos == -1 || pos > row.length) {
                val value = Utils.hbaseFieldToScalaType(field, row, idx, row.length - idx)
                (row.length + 1, parsed :+ (field, value))
              } else {
                val value = Utils.hbaseFieldToScalaType(field, row, idx, pos - idx)
                (pos, parsed :+ (field, value))
              }
            case _ =>
              (
                row.length + 1,
                parsed :+ (field, Utils.hbaseFieldToScalaType(field, row, idx, row.length - idx)))
          }
        }
      }
      ._2
      .toMap
  }

  private def extractFilterFields(filters: Array[Filter]): Array[Field] = {
    val fields = new ListBuffer[Field]()
    def extract(f: Filter): Unit = f match {
      case EqualTo(attr, _) => catalog.sMap.map.get(attr).foreach(fields += _)
      case LessThan(attr, _) => catalog.sMap.map.get(attr).foreach(fields += _)
      case GreaterThan(attr, _) => catalog.sMap.map.get(attr).foreach(fields += _)
      case LessThanOrEqual(attr, _) => catalog.sMap.map.get(attr).foreach(fields += _)
      case GreaterThanOrEqual(attr, _) => catalog.sMap.map.get(attr).foreach(fields += _)
      case StringStartsWith(attr, _) => catalog.sMap.map.get(attr).foreach(fields += _)
      case IsNull(attr) => catalog.sMap.map.get(attr).foreach(fields += _)
      case IsNotNull(attr) => catalog.sMap.map.get(attr).foreach(fields += _)
      case Or(left, right) => extract(left); extract(right)
      case And(left, right) => extract(left); extract(right)
      case _ =>
    }
    filters.foreach(extract)
    fields.toArray
  }

  private def buildValueArray(): Array[Array[Byte]] = {
    val values = new ListBuffer[Array[Byte]]()
    pushedFilters.foreach(f => collectFilterValues(values, f))
    values.toArray
  }

  private def collectFilterValues(values: ListBuffer[Array[Byte]], filter: Filter): Unit = {
    val encoder = JavaBytesEncoder.create(encoderClsName)
    filter match {
      case EqualTo(attr, value) =>
        val field = catalog.sMap.map.get(attr).orNull
        if (field != null) values += Utils.toBytes(value, field)
      case LessThan(attr, value) =>
        val field = catalog.sMap.map.get(attr).orNull
        if (field != null) values += encoder.encode(field.dt, value)
      case GreaterThan(attr, value) =>
        val field = catalog.sMap.map.get(attr).orNull
        if (field != null) values += encoder.encode(field.dt, value)
      case LessThanOrEqual(attr, value) =>
        val field = catalog.sMap.map.get(attr).orNull
        if (field != null) values += encoder.encode(field.dt, value)
      case GreaterThanOrEqual(attr, value) =>
        val field = catalog.sMap.map.get(attr).orNull
        if (field != null) values += encoder.encode(field.dt, value)
      case StringStartsWith(attr, value) =>
        val field = catalog.sMap.map.get(attr).orNull
        if (field != null) values += Utils.toBytes(value, field)
      case Or(left, right) =>
        collectFilterValues(values, left)
        collectFilterValues(values, right)
      case And(left, right) =>
        collectFilterValues(values, left)
        collectFilterValues(values, right)
      case _ =>
    }
  }

  private def buildDynamicLogicExpression(): DynamicLogicExpression = {
    var idx = 0
    def buildExpression(filter: Filter): DynamicLogicExpression = {
      filter match {
        case EqualTo(attr, _) =>
          val field = catalog.sMap.map.get(attr).orNull
          if (field != null) { val i = idx; idx += 1; new EqualLogicExpression(attr, i, false) }
          else new PassThroughLogicExpression
        case LessThan(attr, _) =>
          val field = catalog.sMap.map.get(attr).orNull
          if (field != null) { val i = idx; idx += 1; new LessThanLogicExpression(attr, i) }
          else new PassThroughLogicExpression
        case GreaterThan(attr, _) =>
          val field = catalog.sMap.map.get(attr).orNull
          if (field != null) { val i = idx; idx += 1; new GreaterThanLogicExpression(attr, i) }
          else new PassThroughLogicExpression
        case LessThanOrEqual(attr, _) =>
          val field = catalog.sMap.map.get(attr).orNull
          if (field != null) { val i = idx; idx += 1; new LessThanOrEqualLogicExpression(attr, i) }
          else new PassThroughLogicExpression
        case GreaterThanOrEqual(attr, _) =>
          val field = catalog.sMap.map.get(attr).orNull
          if (field != null) {
            val i = idx; idx += 1; new GreaterThanOrEqualLogicExpression(attr, i)
          }
          else new PassThroughLogicExpression
        case StringStartsWith(attr, _) =>
          val field = catalog.sMap.map.get(attr).orNull
          if (field != null) { val i = idx; idx += 1; new StartsWithLogicExpression(attr, i) }
          else new PassThroughLogicExpression
        case IsNull(attr) => new IsNullLogicExpression(attr, false)
        case IsNotNull(attr) => new IsNullLogicExpression(attr, true)
        case Or(left, right) =>
          new OrLogicExpression(buildExpression(left), buildExpression(right))
        case And(left, right) =>
          new AndLogicExpression(buildExpression(left), buildExpression(right))
        case _ => new PassThroughLogicExpression
      }
    }

    if (pushedFilters.isEmpty) return null

    var result: DynamicLogicExpression = null
    pushedFilters.foreach { f =>
      val expr = buildExpression(f)
      result = if (result == null) expr else new AndLogicExpression(result, expr)
    }
    result
  }
}
