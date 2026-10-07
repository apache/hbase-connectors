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

import java.util.ArrayList
import org.apache.hadoop.hbase.{CellUtil, TableName}
import org.apache.hadoop.hbase.client.{Get, Query, Result, ResultScanner, Scan, Table}
import org.apache.hadoop.hbase.spark.{AndLogicExpression, DynamicLogicExpression,
  EqualLogicExpression, GreaterThanLogicExpression, GreaterThanOrEqualLogicExpression,
  HBaseConnectionCache, IsNullLogicExpression, LessThanLogicExpression,
  LessThanOrEqualLogicExpression, Logging, OrLogicExpression, PassThroughLogicExpression,
  PushdownMappedField, SmartConnection, SparkSQLPushDownFilter, StartsWithLogicExpression}
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.sql.Row
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow
import org.apache.spark.sql.catalyst.util.DateTimeUtils
import org.apache.spark.sql.types.Decimal
import org.apache.spark.sql.connector.read.PartitionReader
import org.apache.spark.sql.sources._
import org.apache.spark.sql.types._
import org.apache.spark.unsafe.types.UTF8String
import org.apache.yetus.audience.InterfaceAudience
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

/**
 * This is a new class in the spark4 module. Extends PartitionReader[InternalRow] for reading data from HBase regions.
 * The actual execution: opens an HBase scanner on the partition's range, attaches the SparkSQLPushDownFilter,
 * reads Result objects, and converts them to InternalRow. Implements next()/get()/close().
 *
 *
 * In the spark 3 DS V1 model, this logic was inside DefaultSource.buildScan()
 * which returned an RDD[Row] with its own compute() method.
 *
 * Ranges are executed as Scan operations, whilst points are executed as batched Get operations. This mirrors the spark3
 * HBaseTableScanRDD.compute() behavior.
 */
@InterfaceAudience.Private
class HBasePartitionReader(
    partition: HBaseInputPartition,
    requiredSchema: StructType,
    properties: Map[String, String],
    catalog: HBaseTableCatalog,
    pushedFilters: Array[Filter],
    encoderClsName: String,
    usePushDownColumnFilter: Boolean,
    wrappedConf: SerializableConfiguration)
    extends PartitionReader[InternalRow]
    with Logging {

  private val conf = wrappedConf.value

  private val connection: SmartConnection = HBaseConnectionCache.getConnection(conf)
  private val tableName = s"${catalog.namespace}:${catalog.name}"
  private val table: Table = connection.getTable(TableName.valueOf(tableName))

  private val requiredFields = requiredSchema.fieldNames.map(catalog.sMap.getField(_))
  private val filterFields = extractFilterFields(pushedFilters)
  private val scanFields = (requiredFields ++ filterFields).distinct.filterNot(_.isRowKey)
  private val hasNullCheck = pushedFilters.exists(containsNullCheck)
  private val pushDownFilter: Option[SparkSQLPushDownFilter] = buildPushDownFilter()

  private val bulkGetSize = properties
    .get(HBaseSparkConf.BULKGET_SIZE)
    .map(_.toInt)
    .getOrElse(HBaseSparkConf.DEFAULT_BULKGET_SIZE)

  private val blockCacheEnable = properties
    .get(HBaseSparkConf.QUERY_CACHEBLOCKS)
    .map(_.toBoolean)
    .getOrElse(HBaseSparkConf.DEFAULT_QUERY_CACHEBLOCKS)

  private val scanners = new ListBuffer[ResultScanner]()

  private val resultIterator: Iterator[Result] = {
    val scanIterators = partition.scanRanges.map { range =>
      val scanner = buildScanner(range)
      scanners += scanner
      scannerToIterator(scanner)
    }
    val getIterator = if (partition.points.nonEmpty) {
      buildGets(partition.points)
    } else {
      Iterator.empty
    }
    scanIterators.foldLeft(Iterator.empty: Iterator[Result])(_ ++ _) ++ getIterator
  }

  private var currentResult: Result = _

  override def next(): Boolean = {
    if (resultIterator.hasNext) {
      currentResult = resultIterator.next()
      true
    } else {
      false
    }
  }

  private val keyFields = catalog.getRowKey

  override def get(): InternalRow = {
    val rowKey = currentResult.getRow

    val keyValues = parseRowKey(rowKey, keyFields)
    val values = new Array[Any](requiredFields.length)

    requiredFields.zipWithIndex.foreach { case (field, idx) =>
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
    scanners.foreach(s => if (s != null) s.close())
    if (table != null) table.close()
    if (connection != null) connection.close()
  }

  private def setStopRow(scan: Scan, bound: Bound): Scan = {
    if (bound.inc) {
      val newArray = new Array[Byte](bound.b.length + 1)
      System.arraycopy(bound.b, 0, newArray, 0, bound.b.length)
      scan.withStopRow(newArray)
    } else {
      scan.withStopRow(bound.b)
    }
  }

  private def buildScanner(range: Range): ResultScanner = {
    val scan = (range.lower, range.upper) match {
      case (Some(Bound(a, _)), Some(upper)) =>
        setStopRow(new Scan().withStartRow(a), upper)
      case (None, Some(upper)) =>
        setStopRow(new Scan(), upper)
      case (Some(Bound(a, _)), None) =>
        new Scan().withStartRow(a)
      case (None, None) =>
        new Scan()
    }

    scan.setCacheBlocks(blockCacheEnable)
    properties.get(HBaseSparkConf.QUERY_CACHEDROWS).map(_.toInt).foreach { rows =>
      if (rows > 0) scan.setCaching(rows)
    }
    properties.get(HBaseSparkConf.QUERY_BATCHSIZE).map(_.toInt).foreach { batch =>
      if (batch > 0) scan.setBatch(batch)
    }
    handleTimeSemantics(scan)

    if (!hasNullCheck) {
      scanFields.foreach { f =>
        scan.addColumn(f.cfBytes, f.colBytes)
      }
    }
    pushDownFilter.foreach(scan.setFilter(_))

    table.getScanner(scan)
  }

  private def buildGets(points: Seq[Array[Byte]]): Iterator[Result] = {
    points.grouped(bulkGetSize).flatMap { batch =>
      val gets = new ArrayList[Get](batch.size)
      batch.foreach { point =>
        val g = new Get(point)
        handleTimeSemantics(g)
        if (!hasNullCheck) {
          scanFields.foreach { f =>
            g.addColumn(f.cfBytes, f.colBytes)
          }
        }
        pushDownFilter.foreach(g.setFilter(_))
        gets.add(g)
      }
      table.get(gets).toSeq.iterator.filter(r => r != null && !r.isEmpty)
    }
  }

  private def scannerToIterator(scanner: ResultScanner): Iterator[Result] = {
    new Iterator[Result] {
      var cur: Option[Result] = None
      override def hasNext: Boolean = {
        if (cur.isEmpty) {
          val r = scanner.next()
          if (r != null) cur = Some(r)
        }
        cur.isDefined
      }
      override def next(): Result = {
        hasNext
        val ret = cur.get
        cur = None
        ret
      }
    }
  }

  private def handleTimeSemantics(query: Query): Unit = {
    val timestamp = properties.get(HBaseSparkConf.TIMESTAMP).map(_.toLong)
    val minTs = properties.get(HBaseSparkConf.TIMERANGE_START).map(_.toLong)
    val maxTs = properties.get(HBaseSparkConf.TIMERANGE_END).map(_.toLong)
    (query, timestamp, minTs, maxTs) match {
      case (q: Scan, Some(ts), None, None) => q.setTimestamp(ts)
      case (q: Get, Some(ts), None, None) => q.setTimestamp(ts)
      case (q: Scan, None, Some(min), Some(max)) => q.setTimeRange(min, max)
      case (q: Get, None, Some(min), Some(max)) => q.setTimeRange(min, max)
      case (_, None, None, None) =>
      case _ =>
        throw new IllegalArgumentException(
          "Invalid combination of timestamp/time range provided.")
    }
    val maxVersions = properties.get(HBaseSparkConf.MAX_VERSIONS).map(_.toInt)
    maxVersions.foreach { mv =>
      query match {
        case q: Scan => q.readVersions(mv)
        case q: Get => q.readVersions(mv)
        case _ =>
      }
    }
  }

  private def buildPushDownFilter(): Option[SparkSQLPushDownFilter] = {
    if (!usePushDownColumnFilter || pushedFilters.isEmpty) return None
    val valueArray = buildValueArray()
    val dynamicLogicExpression = buildDynamicLogicExpression()
    if (dynamicLogicExpression == null) return None

    val allFilterFields = (requiredFields ++ filterFields).distinct
    val columnMappings = allFilterFields.map { field =>
      new PushdownMappedField {
        override def colName(): String = field.colName
        override def cfBytes(): Array[Byte] = field.cfBytes
        override def colBytes(): Array[Byte] = field.colBytes
      }
    }
    Some(new SparkSQLPushDownFilter(
      dynamicLogicExpression,
      valueArray,
      columnMappings.toList.asJava,
      encoderClsName))
  }

  private def convertToInternalRow(value: Any, dataType: DataType): Any = {
    if (value == null) return null
    dataType match {
      case StringType => UTF8String.fromString(value.asInstanceOf[String])
      case DateType =>
        val d = value.asInstanceOf[java.sql.Date]
        DateTimeUtils.fromJavaDate(d)
      case TimestampType =>
        val t = value.asInstanceOf[java.sql.Timestamp]
        DateTimeUtils.fromJavaTimestamp(t)
      case dt: DecimalType =>
        Decimal(value.asInstanceOf[java.math.BigDecimal], dt.precision, dt.scale)
      case structType: StructType =>
        val row = value.asInstanceOf[Row]
        val values = new Array[Any](structType.length)
        var i = 0
        while (i < structType.length) {
          if (row.isNullAt(i)) {
            values(i) = null
          } else {
            values(i) = convertToInternalRow(row.get(i), structType.fields(i).dataType)
          }
          i += 1
        }
        new GenericInternalRow(values)
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
                (pos + 1, parsed :+ (field, value))
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
    val encoder = JavaBytesEncoder.create(encoderClsName)
    pushedFilters.foreach(f => collectFilterValues(values, f, encoder))
    values.toArray
  }

  private def collectFilterValues(
      values: ListBuffer[Array[Byte]],
      filter: Filter,
      encoder: BytesEncoder): Unit = {
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
        collectFilterValues(values, left, encoder)
        collectFilterValues(values, right, encoder)
      case And(left, right) =>
        collectFilterValues(values, left, encoder)
        collectFilterValues(values, right, encoder)
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

  private def containsNullCheck(f: Filter): Boolean = f match {
    case IsNull(_) => true
    case IsNotNull(_) => true
    case And(left, right) => containsNullCheck(left) || containsNullCheck(right)
    case Or(left, right) => containsNullCheck(left) || containsNullCheck(right)
    case _ => false
  }
}
