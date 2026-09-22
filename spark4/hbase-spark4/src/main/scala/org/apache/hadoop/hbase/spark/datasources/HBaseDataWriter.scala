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
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.{Put, Table}
import org.apache.hadoop.hbase.spark.{HBaseConnectionCache, Logging, SmartConnection}
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.connector.write.{DataWriter, WriterCommitMessage}
import org.apache.spark.sql.types._
import org.apache.yetus.audience.InterfaceAudience

/**
 * This is a new class in the spark4 module. Implements DataWriter[InternalRow] for the DS V2 write path.
 * Each instance handles one Spark partition on an executor. Converts InternalRow to HBase Put operations
 * and writes them via Table.put(List[Put]) with client-side batching.
 *
 * In the spark 3 DS V1 model, this logic was inside DefaultSource.insert() which used
 * rdd.map(convertToPut).saveAsHadoopDataset() with the old mapred TableOutputFormat.
 *
 * @param schema
 * @param properties
 * @param catalog
 * @param wrappedConf
 */
@InterfaceAudience.Private
class HBaseDataWriter(
    schema: StructType,
    properties: Map[String, String],
    catalog: HBaseTableCatalog,
    wrappedConf: SerializableConfiguration)
    extends DataWriter[InternalRow]
    with Logging {

  private val conf = wrappedConf.value
  private val connection: SmartConnection = HBaseConnectionCache.getConnection(conf)
  private val tableName = TableName.valueOf(s"${catalog.namespace}:${catalog.name}")
  private val table: Table = connection.getTable(tableName)

  private val timestamp = properties.get(HBaseSparkConf.TIMESTAMP).map(_.toLong)

  private val rkFields = catalog.getRowKey
  private val rkIdxedFields = rkFields.map { f =>
    (schema.fieldIndex(f.colName), f)
  }
  private val colIdxedFields = schema.fieldNames
    .filter(name => !rkFields.map(_.colName).contains(name))
    .map(name => (schema.fieldIndex(name), catalog.sMap.getField(name)))

  private val batchSize = properties
    .get(HBaseSparkConf.BULKGET_SIZE)
    .map(_.toInt)
    .getOrElse(HBaseSparkConf.DEFAULT_BULKGET_SIZE)

  private val putBuffer = new ArrayList[Put](batchSize)

  override def write(record: InternalRow): Unit = {
    val rowKeyBytes = buildRowKey(record)
    val put = timestamp.fold(new Put(rowKeyBytes))(new Put(rowKeyBytes, _))

    colIdxedFields.foreach { case (idx, field) =>
      if (!record.isNullAt(idx)) {
        val valueBytes = getValueBytes(record, idx, field)
        put.addColumn(field.cfBytes, field.colBytes, valueBytes)
      }
    }

    putBuffer.add(put)
    if (putBuffer.size() >= batchSize) {
      flushPuts()
    }
  }

  override def commit(): WriterCommitMessage = {
    flushPuts()
    table.close()
    connection.close()
    HBaseWriterCommitMessage()
  }

  override def abort(): Unit = {
    table.close()
    connection.close()
  }

  override def close(): Unit = {}

  private def flushPuts(): Unit = {
    if (!putBuffer.isEmpty) {
      table.put(putBuffer)
      putBuffer.clear()
    }
  }

  private def buildRowKey(record: InternalRow): Array[Byte] = {
    val rowBytes = rkIdxedFields.map { case (idx, field) =>
      require(!record.isNullAt(idx),
        s"Row key column '${field.colName}' must not be null")
      getValueBytes(record, idx, field)
    }
    val totalLen = rowBytes.foldLeft(0)(_ + _.length)
    val result = new Array[Byte](totalLen)
    var offset = 0
    rowBytes.foreach { bytes =>
      System.arraycopy(bytes, 0, result, offset, bytes.length)
      offset += bytes.length
    }
    result
  }

  private val MILLIS_PER_DAY = 86400000L

  private def getValueBytes(row: InternalRow, idx: Int, field: Field): Array[Byte] = {
    field.dt match {
      case BooleanType => Bytes.toBytes(row.getBoolean(idx))
      case ByteType => Array(row.getByte(idx))
      case ShortType => Bytes.toBytes(row.getShort(idx))
      case IntegerType => Bytes.toBytes(row.getInt(idx))
      case LongType => Bytes.toBytes(row.getLong(idx))
      case FloatType => Bytes.toBytes(row.getFloat(idx))
      case DoubleType => Bytes.toBytes(row.getDouble(idx))
      case StringType => Bytes.toBytes(row.getUTF8String(idx).toString)
      case BinaryType => row.getBinary(idx)
      case DateType => Bytes.toBytes(row.getInt(idx).toLong * MILLIS_PER_DAY)
      case TimestampType => Bytes.toBytes(row.getLong(idx) / 1000)
      case dt: DecimalType =>
        Bytes.toBytes(row.getDecimal(idx, dt.precision, dt.scale).toJavaBigDecimal)
      case _ => throw new UnsupportedOperationException(s"unsupported data type ${field.dt}")
    }
  }
}
