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

import org.apache.spark.sql.sources._
import org.apache.spark.sql.types._
import org.apache.yetus.audience.InterfaceAudience

@InterfaceAudience.Private
private[spark] object PredicatePushdownSupport {

  // Inspect the original filters, including residual predicates. Restricting a Scan/Get to
  // requested qualifiers can hide rows whose only cells are in other columns.
  def containsNullCheck(filter: Filter): Boolean = filter match {
    case IsNull(_) | IsNotNull(_) | EqualNullSafe(_, _) => true
    case And(left, right) => containsNullCheck(left) || containsNullCheck(right)
    case Or(left, right) => containsNullCheck(left) || containsNullCheck(right)
    case Not(child) => containsNullCheck(child)
    case _ => false
  }

  def isSupported(
      filter: Filter,
      dataTypeOf: String => Option[DataType],
      isNaiveEncoder: Boolean): Boolean = {
    def hasSafeLiteral(attr: String, value: Any): Boolean = {
      (dataTypeOf(attr), value) match {
        case (Some(BooleanType), _: Boolean) => true
        case (Some(ByteType), _: Byte) => true
        case (Some(ShortType), _: Short) => true
        case (Some(IntegerType), _: Int) => true
        case (Some(LongType), _: Long) => true
        case (Some(StringType), _: String) => true
        case (Some(BinaryType), _: Array[Byte]) => true
        // shortcut: temporal, decimal and floating point comparisons stay in Spark until
        // encoding, SQL comparison semantics and row-key pruning are all supported.
        case _ => false
      }
    }

    // Only the exact built-in NaiveEncoder has validated comparison tags and row-key ranges.
    // Subclasses and custom encoders may override either operation, so their ranges stay in Spark.
    // Byte has no comparison tag, and its ranges do not split signed values at zero.
    def supportsRange(attr: String, value: Any): Boolean =
      isNaiveEncoder && hasSafeLiteral(attr, value) && !dataTypeOf(attr).contains(ByteType)

    filter match {
      case EqualTo(attr, value) => hasSafeLiteral(attr, value)
      case LessThan(attr, value) => supportsRange(attr, value)
      case LessThanOrEqual(attr, value) => supportsRange(attr, value)
      case GreaterThan(attr, value) => supportsRange(attr, value)
      case GreaterThanOrEqual(attr, value) => supportsRange(attr, value)
      case StringStartsWith(attr, value) => value != null && dataTypeOf(attr).contains(StringType)
      case IsNull(attr) => dataTypeOf(attr).isDefined
      case IsNotNull(attr) => dataTypeOf(attr).isDefined
      case And(left, right) =>
        isSupported(left, dataTypeOf, isNaiveEncoder) &&
        isSupported(right, dataTypeOf, isNaiveEncoder)
      case Or(left, right) =>
        isSupported(left, dataTypeOf, isNaiveEncoder) &&
        isSupported(right, dataTypeOf, isNaiveEncoder)
      case _ => false
    }
  }
}
