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
import org.apache.yetus.audience.InterfaceAudience

@InterfaceAudience.Private
case class HBaseReadOptions(cacheBlocks: Boolean, cachedRows: Int, bulkGetSize: Int)
    extends Serializable

@InterfaceAudience.Private
object HBaseReadOptions {
  def apply(properties: Map[String, String], sparkConf: SparkConf): HBaseReadOptions = {
    HBaseReadOptions(
      properties
        .get(HBaseSparkConf.QUERY_CACHEBLOCKS)
        .map(_.toBoolean)
        .getOrElse(sparkConf
          .getBoolean(HBaseSparkConf.QUERY_CACHEBLOCKS, HBaseSparkConf.DEFAULT_QUERY_CACHEBLOCKS)),
      properties
        .get(HBaseSparkConf.QUERY_CACHEDROWS)
        .map(_.toInt)
        .getOrElse(sparkConf.getInt(HBaseSparkConf.QUERY_CACHEDROWS, -1)),
      properties
        .get(HBaseSparkConf.BULKGET_SIZE)
        .map(_.toInt)
        .getOrElse(
          sparkConf.getInt(HBaseSparkConf.BULKGET_SIZE, HBaseSparkConf.DEFAULT_BULKGET_SIZE)))
  }
}
