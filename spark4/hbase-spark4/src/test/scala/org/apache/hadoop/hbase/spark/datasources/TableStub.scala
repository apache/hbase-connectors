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

import java.io.IOException
import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.util.UUID
import org.apache.hadoop.hbase.{HBaseConfiguration, HConstants, TableName}
import org.apache.hadoop.hbase.client.{ResultScanner, Table}
import org.apache.hadoop.hbase.spark.{ConnectionMocker, HBaseConnectionCache, HBaseConnectionKey}
import org.apache.hadoop.hbase.spark.SmartConnection

/**
 * Records puts and closes on a Table and the empty scanners it opens, failing getTable, put,
 * close, the Nth getScanner or scanner close on request.
 */
class TableStub(
    failGetTable: Boolean = false,
    failPut: Boolean = false,
    failClose: Boolean = false,
    failScannerAt: Int = -1,
    failScannerClose: Boolean = false) {
  var puts = 0
  var closes = 0
  var scannersOpened = 0
  var scannerCloses = 0

  val table: Table = TableStub.proxy(classOf[Table]) {
    case ("put", args) =>
      if (failPut) throw new IOException("put failed")
      puts += args(0).asInstanceOf[java.util.List[_]].size
      null
    case ("close", _) =>
      closes += 1
      if (failClose) throw new IOException("close failed")
      null
    case ("getScanner", _) =>
      scannersOpened += 1
      if (scannersOpened == failScannerAt) throw new IOException("getScanner failed")
      newScanner()
  }

  private def newScanner(): ResultScanner = TableStub.proxy(classOf[ResultScanner]) {
    case ("next", null) => null
    case ("close", _) =>
      scannerCloses += 1
      if (failScannerClose) throw new IllegalStateException("scanner close failed")
      null
  }

  /**
   * Seeds the connection cache for a fresh configuration whose connection serves this table. The
   * cache entry holds one reference of its own, so a reader or writer under test raises the
   * reference count to 2 and must bring it back to 1.
   */
  def withCachedConnection(test: (SerializableConfiguration, SmartConnection) => Unit): Unit = {
    val conf = HBaseConfiguration.create()
    conf.set(HConstants.HBASE_CLIENT_INSTANCE_ID, UUID.randomUUID().toString)
    val key = new HBaseConnectionKey(conf)
    val connection = new ConnectionMocker {
      override def getTable(tableName: TableName): Table = {
        if (failGetTable) throw new IOException("getTable failed")
        table
      }
    }
    val smartConn = HBaseConnectionCache.getConnection(key, connection)
    try {
      test(new SerializableConfiguration(conf), smartConn)
    } finally {
      HBaseConnectionCache.connectionMap.synchronized {
        HBaseConnectionCache.connectionMap.remove(key)
      }
    }
  }
}

object TableStub {
  def refCount(smartConn: SmartConnection): Int =
    HBaseConnectionCache.connectionMap.synchronized(smartConn.refCount)

  private def proxy[T](iface: Class[T])(
      handler: PartialFunction[(String, Array[AnyRef]), AnyRef]): T =
    Proxy
      .newProxyInstance(
        iface.getClassLoader,
        Array[Class[_]](iface),
        new InvocationHandler {
          override def invoke(self: AnyRef, method: Method, args: Array[AnyRef]): AnyRef =
            (method.getName, args) match {
              case ("hashCode", _) => Int.box(System.identityHashCode(self))
              case ("equals", Array(other)) => Boolean.box(self eq other)
              case ("toString", _) => s"${iface.getSimpleName}Stub"
              case call if handler.isDefinedAt(call) => handler(call)
              case (name, _) => throw new UnsupportedOperationException(name)
            }
        })
      .asInstanceOf[T]
}
