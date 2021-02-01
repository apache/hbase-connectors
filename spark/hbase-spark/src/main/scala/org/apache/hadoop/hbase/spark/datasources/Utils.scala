
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hbase.spark.datasources

import java.sql.{Date, Timestamp}

import org.apache.hadoop.hbase.spark.AvroSerdes
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.sql.types._
import org.apache.spark.unsafe.types.UTF8String
import org.apache.yetus.audience.InterfaceAudience;

@InterfaceAudience.Private
object Utils {

  /**
    * Parses the hbase field to it's corresponding
    * scala type which can then be put into a Spark GenericRow
    * which is then automatically converted by Spark.
    */
  def hbaseFieldToScalaType(
      f: Field,
      src: Array[Byte],
      offset: Int,
      length: Int): Any = {
    if (f.exeSchema.isDefined) {
      // If we have avro schema defined, use it to get record, and then convert them to catalyst data type
      val m = AvroSerdes.deserialize(src, f.exeSchema.get)
      val n = f.avroToCatalyst.map(_(m))
      n.get
    } else  {
      // Fall back to atomic type
      f.dt match {
        case BooleanType => src(offset) != 0
        case ByteType => src(offset)
        case ShortType => Bytes.toShort(src, offset)
        case IntegerType => Bytes.toInt(src, offset)
        case LongType => Bytes.toLong(src, offset)
        case FloatType => Bytes.toFloat(src, offset)
        case DoubleType => Bytes.toDouble(src, offset)
        case DateType => new Date(Bytes.toLong(src, offset))
        case TimestampType => new Timestamp(Bytes.toLong(src, offset))
        case StringType => Bytes.toString(src, offset, length)
        case BinaryType =>
          val newArray = new Array[Byte](length)
          System.arraycopy(src, offset, newArray, 0, length)
          newArray
        // TODO: SparkSqlSerializer.deserialize[Any](src)
        case _ => throw new Exception(s"unsupported data type ${f.dt}")
      }
    }
  }

  // convert input to data type
  def toBytes(input: Any, field: Field): Array[Byte] = {
    if (field.schema.isDefined) {
      // Here we assume the top level type is structType
      val record = field.catalystToAvro(input)
      AvroSerdes.serialize(record, field.schema.get)
    } else {
      field.dt match {
        case BooleanType => Bytes.toBytes(input.asInstanceOf[Boolean])
        case ByteType => Array(input.asInstanceOf[Number].byteValue)
        case ShortType => Bytes.toBytes(input.asInstanceOf[Number].shortValue)
        case IntegerType => Bytes.toBytes(input.asInstanceOf[Number].intValue)
        case LongType => Bytes.toBytes(input.asInstanceOf[Number].longValue)
        case FloatType => Bytes.toBytes(input.asInstanceOf[Number].floatValue)
        case DoubleType => Bytes.toBytes(input.asInstanceOf[Number].doubleValue)
        case DateType | TimestampType => Bytes.toBytes(input.asInstanceOf[java.util.Date].getTime)
        case StringType => Bytes.toBytes(input.toString)
        case BinaryType => input.asInstanceOf[Array[Byte]]
        case _ => throw new Exception(s"unsupported data type ${field.dt}")
      }
    }
  }
}
