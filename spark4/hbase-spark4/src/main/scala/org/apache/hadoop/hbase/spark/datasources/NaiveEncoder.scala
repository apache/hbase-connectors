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
import org.apache.hadoop.hbase.spark.datasources.JavaBytesEncoder.JavaBytesEncoder
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.sql.types._
import org.apache.spark.unsafe.types.UTF8String
import org.apache.yetus.audience.InterfaceAudience

@InterfaceAudience.Private
class NaiveEncoder extends BytesEncoder with Logging {
  var code = 0
  def nextCode: Byte = {
    code += 1
    (code - 1).asInstanceOf[Byte]
  }
  val BooleanEnc: Byte = nextCode
  val ShortEnc: Byte = nextCode
  val IntEnc: Byte = nextCode
  val LongEnc: Byte = nextCode
  val FloatEnc: Byte = nextCode
  val DoubleEnc: Byte = nextCode
  val StringEnc: Byte = nextCode
  val BinaryEnc: Byte = nextCode
  val TimestampEnc: Byte = nextCode
  val UnknownEnc: Byte = nextCode

  override def ranges(in: Any): Option[BoundRanges] = in match {
    case a: Integer =>
      val b = Bytes.toBytes(a)
      if (a >= 0) {
        Some(
          BoundRanges(
            Array(
              BoundRange(Bytes.toBytes(0: Int), b),
              BoundRange(Bytes.toBytes(Integer.MIN_VALUE), Bytes.toBytes(-1: Int))),
            Array(BoundRange(b, Bytes.toBytes(Integer.MAX_VALUE))),
            b))
      } else {
        Some(
          BoundRanges(
            Array(BoundRange(Bytes.toBytes(Integer.MIN_VALUE), b)),
            Array(
              BoundRange(b, Bytes.toBytes(-1: Integer)),
              BoundRange(Bytes.toBytes(0: Int), Bytes.toBytes(Integer.MAX_VALUE))),
            b))
      }
    case a: Long =>
      val b = Bytes.toBytes(a)
      if (a >= 0) {
        Some(
          BoundRanges(
            Array(
              BoundRange(Bytes.toBytes(0: Long), b),
              BoundRange(Bytes.toBytes(Long.MinValue), Bytes.toBytes(-1: Long))),
            Array(BoundRange(b, Bytes.toBytes(Long.MaxValue))),
            b))
      } else {
        Some(
          BoundRanges(
            Array(BoundRange(Bytes.toBytes(Long.MinValue), b)),
            Array(
              BoundRange(b, Bytes.toBytes(-1: Long)),
              BoundRange(Bytes.toBytes(0: Long), Bytes.toBytes(Long.MaxValue))),
            b))
      }
    case a: Short =>
      val b = Bytes.toBytes(a)
      if (a >= 0) {
        Some(
          BoundRanges(
            Array(
              BoundRange(Bytes.toBytes(0: Short), b),
              BoundRange(Bytes.toBytes(Short.MinValue), Bytes.toBytes(-1: Short))),
            Array(BoundRange(b, Bytes.toBytes(Short.MaxValue))),
            b))
      } else {
        Some(
          BoundRanges(
            Array(BoundRange(Bytes.toBytes(Short.MinValue), b)),
            Array(
              BoundRange(b, Bytes.toBytes(-1: Short)),
              BoundRange(Bytes.toBytes(0: Short), Bytes.toBytes(Short.MaxValue))),
            b))
      }
    case a: Double =>
      val b = Bytes.toBytes(a)
      if (a >= 0.0f) {
        Some(
          BoundRanges(
            Array(
              BoundRange(Bytes.toBytes(0.0d), b),
              BoundRange(Bytes.toBytes(-0.0d), Bytes.toBytes(Double.MinValue))),
            Array(BoundRange(b, Bytes.toBytes(Double.MaxValue))),
            b))
      } else {
        Some(
          BoundRanges(
            Array(BoundRange(b, Bytes.toBytes(Double.MinValue))),
            Array(
              BoundRange(Bytes.toBytes(-0.0d), b),
              BoundRange(Bytes.toBytes(0.0d), Bytes.toBytes(Double.MaxValue))),
            b))
      }
    case a: Float =>
      val b = Bytes.toBytes(a)
      if (a >= 0.0f) {
        Some(
          BoundRanges(
            Array(
              BoundRange(Bytes.toBytes(0.0f), b),
              BoundRange(Bytes.toBytes(-0.0f), Bytes.toBytes(Float.MinValue))),
            Array(BoundRange(b, Bytes.toBytes(Float.MaxValue))),
            b))
      } else {
        Some(
          BoundRanges(
            Array(BoundRange(b, Bytes.toBytes(Float.MinValue))),
            Array(
              BoundRange(Bytes.toBytes(-0.0f), b),
              BoundRange(Bytes.toBytes(0.0f), Bytes.toBytes(Float.MaxValue))),
            b))
      }
    case a: Array[Byte] =>
      Some(BoundRanges(Array(BoundRange(bytesMin, a)), Array(BoundRange(a, bytesMax)), a))
    case a: Byte =>
      val b = Array(a)
      Some(BoundRanges(Array(BoundRange(bytesMin, b)), Array(BoundRange(b, bytesMax)), b))
    case a: String =>
      val b = Bytes.toBytes(a)
      Some(BoundRanges(Array(BoundRange(bytesMin, b)), Array(BoundRange(b, bytesMax)), b))
    case a: UTF8String =>
      val b = a.getBytes
      Some(BoundRanges(Array(BoundRange(bytesMin, b)), Array(BoundRange(b, bytesMax)), b))
    case _ => None
  }

  def compare(c: Int, ops: JavaBytesEncoder): Boolean = {
    ops match {
      case JavaBytesEncoder.Greater => c > 0
      case JavaBytesEncoder.GreaterEqual => c >= 0
      case JavaBytesEncoder.Less => c < 0
      case JavaBytesEncoder.LessEqual => c <= 0
    }
  }

  override def encode(dt: DataType, value: Any): Array[Byte] = {
    dt match {
      case BooleanType =>
        val result = new Array[Byte](Bytes.SIZEOF_BOOLEAN + 1)
        result(0) = BooleanEnc
        value.asInstanceOf[Boolean] match {
          case true => result(1) = (-1: Byte)
          case false => result(1) = (0: Byte)
        }
        result
      case ShortType =>
        val result = new Array[Byte](Bytes.SIZEOF_SHORT + 1)
        result(0) = ShortEnc
        Bytes.putShort(result, 1, value.asInstanceOf[Short])
        result
      case IntegerType =>
        val result = new Array[Byte](Bytes.SIZEOF_INT + 1)
        result(0) = IntEnc
        Bytes.putInt(result, 1, value.asInstanceOf[Int])
        result
      case LongType | TimestampType =>
        val result = new Array[Byte](Bytes.SIZEOF_LONG + 1)
        result(0) = LongEnc
        Bytes.putLong(result, 1, value.asInstanceOf[Long])
        result
      case FloatType =>
        val result = new Array[Byte](Bytes.SIZEOF_FLOAT + 1)
        result(0) = FloatEnc
        Bytes.putFloat(result, 1, value.asInstanceOf[Float])
        result
      case DoubleType =>
        val result = new Array[Byte](Bytes.SIZEOF_DOUBLE + 1)
        result(0) = DoubleEnc
        Bytes.putDouble(result, 1, value.asInstanceOf[Double])
        result
      case BinaryType =>
        val v = value.asInstanceOf[Array[Byte]]
        val result = new Array[Byte](v.length + 1)
        result(0) = BinaryEnc
        System.arraycopy(v, 0, result, 1, v.length)
        result
      case StringType =>
        val bytes = Bytes.toBytes(value.asInstanceOf[String])
        val result = new Array[Byte](bytes.length + 1)
        result(0) = StringEnc
        System.arraycopy(bytes, 0, result, 1, bytes.length)
        result
      case _ =>
        val bytes = Bytes.toBytes(value.toString)
        val result = new Array[Byte](bytes.length + 1)
        result(0) = UnknownEnc
        System.arraycopy(bytes, 0, result, 1, bytes.length)
        result
    }
  }

  override def filter(
      input: Array[Byte],
      offset1: Int,
      length1: Int,
      filterBytes: Array[Byte],
      offset2: Int,
      length2: Int,
      ops: JavaBytesEncoder): Boolean = {
    filterBytes(offset2) match {
      case ShortEnc =>
        val in = Bytes.toShort(input, offset1)
        val value = Bytes.toShort(filterBytes, offset2 + 1)
        compare(in.compareTo(value), ops)
      case IntEnc =>
        val in = Bytes.toInt(input, offset1)
        val value = Bytes.toInt(filterBytes, offset2 + 1)
        compare(in.compareTo(value), ops)
      case LongEnc | TimestampEnc =>
        val in = Bytes.toLong(input, offset1)
        val value = Bytes.toLong(filterBytes, offset2 + 1)
        compare(in.compareTo(value), ops)
      case FloatEnc =>
        val in = Bytes.toFloat(input, offset1)
        val value = Bytes.toFloat(filterBytes, offset2 + 1)
        compare(in.compareTo(value), ops)
      case DoubleEnc =>
        val in = Bytes.toDouble(input, offset1)
        val value = Bytes.toDouble(filterBytes, offset2 + 1)
        compare(in.compareTo(value), ops)
      case _ =>
        compare(
          Bytes.compareTo(input, offset1, length1, filterBytes, offset2 + 1, length2 - 1),
          ops)
    }
  }
}
