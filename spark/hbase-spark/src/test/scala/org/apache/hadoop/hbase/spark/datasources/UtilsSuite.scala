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

import org.apache.hadoop.hbase.spark.AvroSerdes
import org.apache.spark.sql.Row
import org.scalatest.FunSuite

class UtilsSuite extends FunSuite {

  private val primitiveValues = Seq[(String, Seq[Any])](
    "boolean" -> Seq(false, true),
    "int" -> Seq(Int.MinValue, -2, 0, 100, Int.MaxValue),
    "long" -> Seq(Long.MinValue, -2L, 0L, 100L, Long.MaxValue),
    "float" -> Seq(Float.NegativeInfinity, -1.0f, -0.0f, 0.0f, Float.PositiveInfinity, Float.NaN),
    "double" -> Seq(
      Double.NegativeInfinity,
      -1.0d,
      -0.0d,
      0.0d,
      Double.PositiveInfinity,
      Double.NaN),
    "string" -> Seq("", "HBase", "中文"))

  for ((name, values) <- primitiveValues) {
    test(s"primitive Avro $name uses the raw HBase encoding") {
      val field = Field("value", "cf", "value", avroSchema = Some("\"" + name + "\""))
      for (value <- values) {
        val bytes = AvroSerdes.serialize(value, field.exeSchema.get)
        val padded = Array(99.toByte) ++ bytes ++ Array(99.toByte)
        val actual = Utils.hbaseFieldToScalaType(field, padded, 1, bytes.length)
        value match {
          case f: Float =>
            assert(
              java.lang.Float.floatToIntBits(actual.asInstanceOf[Float]) ==
                java.lang.Float.floatToIntBits(f))
          case d: Double =>
            assert(
              java.lang.Double.doubleToLongBits(actual.asInstanceOf[Double]) ==
                java.lang.Double.doubleToLongBits(d))
          case _ => assert(actual == value)
        }
      }
    }
  }

  for (schema <- Seq("\"bytes\"", """{"type":"fixed","name":"Value","size":3}""")) {
    test(s"primitive Avro $schema copies the raw byte slice") {
      val field = Field("value", "cf", "value", avroSchema = Some(schema))
      val bytes = AvroSerdes.serialize(Array[Byte](-1, 0, 2), field.exeSchema.get)
      val padded = Array(99.toByte) ++ bytes ++ Array(99.toByte)
      val actual = Utils
        .hbaseFieldToScalaType(field, padded, 1, bytes.length)
        .asInstanceOf[Array[Byte]]
      assert(actual.sameElements(bytes))
      assert(!(actual eq padded))
    }
  }

  test("Avro records retain Avro binary decoding") {
    val field = Field(
      "value",
      "cf",
      "value",
      avroSchema =
        Some("""{"type":"record","name":"Value","fields":[{"name":"number","type":"int"}]}"""))
    val bytes = Utils.toBytes(Row(100), field)
    assert(Utils.hbaseFieldToScalaType(field, bytes, 0, bytes.length) == Row(100))
  }

}
