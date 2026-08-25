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

import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.sql.types._
import org.scalatest.funsuite.AnyFunSuite

class NaiveEncoderSuite extends AnyFunSuite {

  val encoder = new NaiveEncoder

  test("encode BinaryType should preserve bytes") {
    val input = Array[Byte](1, 2, 3, 4, 5)
    val encoded = encoder.encode(BinaryType, input)
    assert(encoded.length == input.length + 1)
    assert(encoded(0) == encoder.BinaryEnc)
    assert(encoded.slice(1, encoded.length).sameElements(input))
  }

  test("encode IntegerType") {
    val encoded = encoder.encode(IntegerType, 42: Int)
    assert(encoded.length == Bytes.SIZEOF_INT + 1)
    assert(encoded(0) == encoder.IntEnc)
    assert(Bytes.toInt(encoded, 1) == 42)
  }

  test("encode LongType") {
    val encoded = encoder.encode(LongType, 123456789L)
    assert(encoded.length == Bytes.SIZEOF_LONG + 1)
    assert(encoded(0) == encoder.LongEnc)
    assert(Bytes.toLong(encoded, 1) == 123456789L)
  }

  test("encode DoubleType") {
    val encoded = encoder.encode(DoubleType, 3.14d)
    assert(encoded.length == Bytes.SIZEOF_DOUBLE + 1)
    assert(encoded(0) == encoder.DoubleEnc)
    assert(Bytes.toDouble(encoded, 1) == 3.14d)
  }

  test("encode FloatType") {
    val encoded = encoder.encode(FloatType, 2.5f)
    assert(encoded.length == Bytes.SIZEOF_FLOAT + 1)
    assert(encoded(0) == encoder.FloatEnc)
    assert(Bytes.toFloat(encoded, 1) == 2.5f)
  }

  test("encode ShortType") {
    val encoded = encoder.encode(ShortType, 7: Short)
    assert(encoded.length == Bytes.SIZEOF_SHORT + 1)
    assert(encoded(0) == encoder.ShortEnc)
    assert(Bytes.toShort(encoded, 1) == 7)
  }

  test("encode StringType") {
    val encoded = encoder.encode(StringType, "hello")
    val expected = Bytes.toBytes("hello")
    assert(encoded.length == expected.length + 1)
    assert(encoded(0) == encoder.StringEnc)
    assert(encoded.slice(1, encoded.length).sameElements(expected))
  }

  test("encode BooleanType true") {
    val encoded = encoder.encode(BooleanType, true)
    assert(encoded.length == Bytes.SIZEOF_BOOLEAN + 1)
    assert(encoded(0) == encoder.BooleanEnc)
    assert(encoded(1) == (-1: Byte))
  }

  test("encode BooleanType false") {
    val encoded = encoder.encode(BooleanType, false)
    assert(encoded.length == Bytes.SIZEOF_BOOLEAN + 1)
    assert(encoded(0) == encoder.BooleanEnc)
    assert(encoded(1) == (0: Byte))
  }

  test("filter with IntegerType GreaterThan") {
    val encoded = encoder.encode(IntegerType, 10: Int)
    val input = Bytes.toBytes(20: Int)
    assert(
      encoder.filter(
        input, 0, input.length,
        encoded, 0, encoded.length,
        JavaBytesEncoder.Greater))
  }

  test("filter with IntegerType LessThan") {
    val encoded = encoder.encode(IntegerType, 10: Int)
    val input = Bytes.toBytes(5: Int)
    assert(
      encoder.filter(
        input, 0, input.length,
        encoded, 0, encoded.length,
        JavaBytesEncoder.Less))
  }
}
