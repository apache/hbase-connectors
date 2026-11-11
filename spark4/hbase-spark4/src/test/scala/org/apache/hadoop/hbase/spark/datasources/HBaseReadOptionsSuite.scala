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

import org.apache.spark.SparkConf
import org.scalatest.funsuite.AnyFunSuite

class HBaseReadOptionsSuite extends AnyFunSuite {
  private val globals = Map(
    HBaseSparkConf.QUERY_CACHEBLOCKS -> "true",
    HBaseSparkConf.QUERY_CACHEDROWS -> "256",
    HBaseSparkConf.BULKGET_SIZE -> "2")

  test("read options use defaults when options and SparkConf are absent") {
    assert(HBaseReadOptions(Map.empty, new SparkConf(false)) == HBaseReadOptions(false, -1, 1000))
  }

  test("read options fall back to SparkConf") {
    val conf = new SparkConf(false).setAll(globals)
    assert(HBaseReadOptions(Map.empty, conf) == HBaseReadOptions(true, 256, 2))
  }

  test("read options override SparkConf including explicit false") {
    val conf = new SparkConf(false).setAll(globals)
    val options = Map(
      HBaseSparkConf.QUERY_CACHEBLOCKS -> "false",
      HBaseSparkConf.QUERY_CACHEDROWS -> "64",
      HBaseSparkConf.BULKGET_SIZE -> "3")
    assert(HBaseReadOptions(options, conf) == HBaseReadOptions(false, 64, 3))
    assert(HBaseReadOptions(options, new SparkConf(false)) == HBaseReadOptions(false, 64, 3))
  }

  test("each missing read option falls back independently") {
    val conf = new SparkConf(false).setAll(globals)
    val options = Map(HBaseSparkConf.QUERY_CACHEBLOCKS -> "false")
    assert(HBaseReadOptions(options, conf) == HBaseReadOptions(false, 256, 2))
  }

  test("nonpositive cached rows override a positive global setting") {
    val conf = new SparkConf(false).setAll(globals)
    for (rows <- Seq(-1, 0)) {
      val options = Map(HBaseSparkConf.QUERY_CACHEDROWS -> rows.toString)
      assert(HBaseReadOptions(options, conf) == HBaseReadOptions(true, rows, 2))
    }
  }

  test("overridden global values are not parsed") {
    val conf = new SparkConf(false).setAll(globals.keys.map(_ -> "invalid"))
    assert(HBaseReadOptions(globals, conf) == HBaseReadOptions(true, 256, 2))
  }

  test("invalid effective read options fail instead of falling back") {
    for (key <- globals.keys) {
      intercept[IllegalArgumentException] {
        HBaseReadOptions(Map(key -> "invalid"), new SparkConf(false).setAll(globals))
      }
      intercept[IllegalArgumentException] {
        HBaseReadOptions(Map.empty, new SparkConf(false).set(key, "invalid"))
      }
    }
  }

  test("cell batching remains ignored for options and SparkConf") {
    val options = Map(HBaseSparkConf.QUERY_BATCHSIZE -> "invalid")
    val conf = new SparkConf(false).set(HBaseSparkConf.QUERY_BATCHSIZE, "invalid")
    assert(HBaseReadOptions(options, conf) == HBaseReadOptions(false, -1, 1000))
  }
}
