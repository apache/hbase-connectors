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
package org.apache.hadoop.hbase.spark

import java.nio.ByteBuffer
import org.apache.avro.{Schema, SchemaBuilder}
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.avro.generic.GenericData.Fixed
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow
import org.apache.spark.sql.types._
import org.apache.spark.unsafe.types.UTF8String
import org.scalatest.funsuite.AnyFunSuite

class SchemaConvertersSuite extends AnyFunSuite {

  // --- AvroSerdes.serialize tests ---

  test("serialize nullable union resolves to inner type") {
    val schema = Schema.createUnion(Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.INT))
    val result = AvroSerdes.serialize(42: java.lang.Integer, schema)
    assert(result.sameElements(Bytes.toBytes(42)))
  }

  test("serialize nullable record union") {
    val recordSchema = SchemaBuilder.record("TestRecord").namespace("test")
      .fields()
      .requiredString("name")
      .endRecord()
    val unionSchema = Schema.createUnion(Schema.create(Schema.Type.NULL), recordSchema)

    val record = new GenericData.Record(recordSchema)
    record.put("name", "hello")

    val bytes = AvroSerdes.serialize(record, unionSchema)
    val deserialized = AvroSerdes.deserialize(bytes, recordSchema)
    assert(deserialized.get("name").toString == "hello")
  }

  test("serialize rejects multi-branch union") {
    val unionSchema = Schema.createUnion(
      Schema.create(Schema.Type.INT), Schema.create(Schema.Type.STRING))
    val ex = intercept[UnsupportedOperationException] {
      AvroSerdes.serialize(42: java.lang.Integer, unionSchema)
    }
    assert(ex.getMessage.contains("more than one non-null branch"))
  }

  test("serialize handles ByteBuffer for top-level BYTES") {
    val schema = Schema.create(Schema.Type.BYTES)
    val original = Array[Byte](10, 20, 30)
    val result = AvroSerdes.serialize(ByteBuffer.wrap(original), schema)
    assert(result.sameElements(original))
  }

  // --- createConverterToAvroFromInternalRow tests ---

  test("FIXED produces GenericData.Fixed") {
    val fixedSchema = SchemaBuilder.fixed("hash4").size(4)
    val converter = SchemaConverters.createConverterToAvroFromInternalRow(BinaryType, fixedSchema)
    val input = Array[Byte](1, 2, 3, 4)
    val result = converter(input)
    assert(result.isInstanceOf[Fixed])
    val fixed = result.asInstanceOf[Fixed]
    assert(fixed.bytes().sameElements(input))
    assert(fixed.getSchema == fixedSchema)
  }

  test("FIXED rejects wrong byte length") {
    val fixedSchema = SchemaBuilder.fixed("hash4").size(4)
    val converter = SchemaConverters.createConverterToAvroFromInternalRow(BinaryType, fixedSchema)
    val ex = intercept[SchemaConversionException] {
      converter(Array[Byte](1, 2, 3, 4, 5, 6))
    }
    assert(ex.getMessage.contains("Expected 4 bytes"))
    assert(ex.getMessage.contains("got 6"))
  }

  test("ENUM produces GenericData.EnumSymbol") {
    val enumSchema = SchemaBuilder.enumeration("Color").symbols("RED", "GREEN", "BLUE")
    val converter = SchemaConverters.createConverterToAvroFromInternalRow(StringType, enumSchema)
    val result = converter(UTF8String.fromString("GREEN"))
    assert(result.isInstanceOf[GenericData.EnumSymbol])
    assert(result.toString == "GREEN")
  }

  test("struct field matching by name not position") {
    val avroSchema = SchemaBuilder.record("Rec").namespace("test")
      .fields()
      .requiredInt("b")
      .requiredString("a")
      .endRecord()

    val sparkType = StructType(Seq(
      StructField("a", StringType),
      StructField("b", IntegerType)))

    val converter = SchemaConverters.createConverterToAvroFromInternalRow(sparkType, avroSchema)

    val row = new GenericInternalRow(Array[Any](UTF8String.fromString("hello"), 99))
    val result = converter(row).asInstanceOf[GenericRecord]
    assert(result.get("a").toString == "hello")
    assert(result.get("b") == 99)
  }

  test("struct rejects missing Avro field") {
    val avroSchema = SchemaBuilder.record("Rec").namespace("test")
      .fields()
      .requiredInt("x")
      .endRecord()

    val sparkType = StructType(Seq(StructField("missing", IntegerType)))

    val ex = intercept[SchemaConversionException] {
      SchemaConverters.createConverterToAvroFromInternalRow(sparkType, avroSchema)
    }
    assert(ex.getMessage.contains("not found in Avro schema"))
  }

  test("DateType uses identity conversion") {
    val intSchema = Schema.create(Schema.Type.INT)
    val converter = SchemaConverters.createConverterToAvroFromInternalRow(DateType, intSchema)
    val daysSinceEpoch = 18500
    assert(converter(daysSinceEpoch) == 18500)
  }

  test("unsupported type throws UnsupportedOperationException") {
    val intSchema = Schema.create(Schema.Type.INT)
    val ex = intercept[UnsupportedOperationException] {
      SchemaConverters.createConverterToAvroFromInternalRow(NullType, intSchema)
    }
    assert(ex.getMessage.contains("Cannot convert Spark type"))
  }
}
