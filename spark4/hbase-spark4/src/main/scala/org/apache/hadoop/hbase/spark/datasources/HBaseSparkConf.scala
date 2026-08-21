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

import org.apache.yetus.audience.InterfaceAudience

@InterfaceAudience.Public
object HBaseSparkConf {

  val QUERY_CACHEBLOCKS = "hbase.spark.query.cacheblocks"
  val DEFAULT_QUERY_CACHEBLOCKS = false

  val QUERY_CACHEDROWS = "hbase.spark.query.cachedrows"

  val QUERY_BATCHSIZE = "hbase.spark.query.batchsize"

  val BULKGET_SIZE = "hbase.spark.bulkget.size"
  val DEFAULT_BULKGET_SIZE = 1000

  val HBASE_CONFIG_LOCATION = "hbase.spark.config.location"

  val USE_HBASECONTEXT = "hbase.spark.use.hbasecontext"
  val DEFAULT_USE_HBASECONTEXT = true

  val PUSHDOWN_COLUMN_FILTER = "hbase.spark.pushdown.columnfilter"
  val DEFAULT_PUSHDOWN_COLUMN_FILTER = true

  val QUERY_ENCODER = "hbase.spark.query.encoder"
  val DEFAULT_QUERY_ENCODER = classOf[NaiveEncoder].getCanonicalName

  val TIMESTAMP = "hbase.spark.query.timestamp"

  val TIMERANGE_START = "hbase.spark.query.timerange.start"

  val TIMERANGE_END = "hbase.spark.query.timerange.end"

  val MAX_VERSIONS = "hbase.spark.query.maxVersions"

  val DEFAULT_CONNECTION_CLOSE_DELAY = 10 * 60 * 1000
}
