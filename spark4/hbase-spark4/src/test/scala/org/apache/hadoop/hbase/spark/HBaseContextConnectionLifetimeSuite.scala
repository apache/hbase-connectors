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

import java.util.UUID
import java.util.concurrent.{Callable, CountDownLatch, ExecutionException, Executors, TimeUnit}
import org.apache.hadoop.hbase.{HBaseConfiguration, HConstants}
import org.apache.hadoop.hbase.client.Connection
import org.apache.hadoop.hbase.spark.datasources.HBaseSparkConf
import org.apache.spark.{SparkConf, SparkContext, SparkException, TaskContext}
import org.apache.spark.scheduler.{SparkListener, SparkListenerTaskEnd}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.time.{Millis, Seconds, Span}

// Local Spark tasks access this state without serializing the test suite or its SparkContext.
private object ConnectionLifetimeTestState {
  @volatile var current: ConnectionLifetimeFixture = _
}

private class ConnectionLifetimeFixture(val smartConn: SmartConnection) {
  val started = new CountDownLatch(2)
  val finishFirst = new CountDownLatch(1)
  val finishSecond = new CountDownLatch(1)
  @volatile var taskAttemptId: Long = -1L
  private var seedReleased = false

  def releaseSeed(): Unit = HBaseConnectionCache.connectionMap.synchronized {
    if (!seedReleased) {
      smartConn.close()
      seedReleased = true
    }
  }

  def assertRefCount(expected: Int): Unit = HBaseConnectionCache.connectionMap.synchronized {
    assert(smartConn.refCount == expected)
  }

  def expire(): Unit = HBaseConnectionCache.connectionMap.synchronized {
    smartConn.timestamp = System.currentTimeMillis() -
      HBaseSparkConf.DEFAULT_CONNECTION_CLOSE_DELAY - 1
    HBaseConnectionCache.performHousekeeping(false)
  }

  def assertRetained(connection: Connection): Unit =
    HBaseConnectionCache.connectionMap.synchronized {
      expire()
      assert(!connection.isClosed, "connection closed while consuming a lazy iterator")
      assert(smartConn.refCount > 0)
    }
}

class HBaseContextConnectionLifetimeSuite
    extends AnyFunSuite
    with BeforeAndAfterAll
    with Eventually {
  private var sc: SparkContext = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    sc = new SparkContext(
      new SparkConf()
        .setMaster("local[2]")
        .setAppName(getClass.getSimpleName)
        .set("spark.ui.enabled", "false"))
  }

  override def afterAll(): Unit = {
    try {
      if (sc != null) sc.stop()
    } finally {
      HBaseConnectionCache.setTimeout(HBaseSparkConf.DEFAULT_CONNECTION_CLOSE_DELAY)
      super.afterAll()
    }
  }

  private def withConnection(test: HBaseContext => Unit): Unit = {
    val conf = HBaseConfiguration.create()
    conf.set(HConstants.HBASE_CLIENT_INSTANCE_ID, UUID.randomUUID().toString)
    val key = new HBaseConnectionKey(conf)
    var closeCount = 0
    val connection = new ConnectionMocker {
      override def close(): Unit = {
        closeCount += 1
        super.close()
      }
    }
    HBaseConnectionCache.setTimeout(HBaseSparkConf.DEFAULT_CONNECTION_CLOSE_DELAY)
    val fixture = new ConnectionLifetimeFixture(HBaseConnectionCache.getConnection(key, connection))
    ConnectionLifetimeTestState.current = fixture
    try {
      test(new HBaseContext(sc, conf))
      eventually(timeout(Span(10, Seconds)), interval(Span(10, Millis))) {
        fixture.assertRefCount(0)
      }
      HBaseConnectionCache.connectionMap.synchronized {
        assert(!connection.isClosed)
        fixture.expire()
        assert(connection.isClosed)
        assert(closeCount == 1)
        assert(!HBaseConnectionCache.connectionMap.contains(key))
      }
    } finally {
      fixture.releaseSeed()
      HBaseConnectionCache.setTimeout(HBaseSparkConf.DEFAULT_CONNECTION_CLOSE_DELAY)
      HBaseConnectionCache.connectionMap.synchronized {
        HBaseConnectionCache.connectionMap.remove(key)
        if (!connection.isClosed) connection.close()
      }
      ConnectionLifetimeTestState.current = null
    }
  }

  test("retain the connection through lazy hasNext and next across cache expiry") {
    withConnection {
      hc =>
        val result = hc.mapPartitions[Int, Int](
          sc.parallelize(Seq(1, 2), 1),
          (it, conn) => {
            ConnectionLifetimeTestState.current.releaseSeed()
            new Iterator[Int] {
              override def hasNext: Boolean = {
                ConnectionLifetimeTestState.current.assertRetained(conn)
                it.hasNext
              }
              override def next(): Int = {
                ConnectionLifetimeTestState.current.assertRetained(conn)
                it.next()
              }
            }
          })
        assert(result.collect().toSeq == Seq(1, 2))
    }
  }

  test("retain the connection through lazy iteration beyond the cache timeout") {
    withConnection {
      hc =>
        HBaseConnectionCache.setTimeout(20)
        val result = hc.mapPartitions[Int, Boolean](
          sc.parallelize(Seq(1), 1),
          (it, conn) => {
            ConnectionLifetimeTestState.current.releaseSeed()
            it.map {
              _ =>
                try {
                  Thread.sleep(100)
                  HBaseConnectionCache.performHousekeeping(false)
                  !conn.isClosed
                } finally {
                  HBaseConnectionCache.setTimeout(HBaseSparkConf.DEFAULT_CONNECTION_CLOSE_DELAY)
                }
            }
          })
        assert(result.collect().toSeq == Seq(true))
        HBaseConnectionCache.setTimeout(HBaseSparkConf.DEFAULT_CONNECTION_CLOSE_DELAY)
    }
  }

  test("release the connection when the callback throws") {
    withConnection {
      hc =>
        val result = hc.mapPartitions[Int, Int](
          sc.parallelize(Seq(1), 1),
          (_, _) => {
            ConnectionLifetimeTestState.current.releaseSeed()
            throw new IllegalStateException("callback failure")
          })
        val error = intercept[SparkException](result.collect())
        assert(error.getMessage.contains("callback failure"))
    }
  }

  for (failInHasNext <- Seq(true, false)) {
    test(s"release the connection when lazy ${if (failInHasNext) "hasNext" else "next"} throws") {
      withConnection {
        hc =>
          val result = hc.mapPartitions[Int, Int](
            sc.parallelize(Seq(1), 1),
            (it, conn) => {
              ConnectionLifetimeTestState.current.releaseSeed()
              new Iterator[Int] {
                override def hasNext: Boolean = {
                  ConnectionLifetimeTestState.current.assertRetained(conn)
                  if (failInHasNext) throw new IllegalStateException("iterator failure")
                  it.hasNext
                }
                override def next(): Int = {
                  ConnectionLifetimeTestState.current.assertRetained(conn)
                  throw new IllegalStateException("iterator failure")
                }
              }
            })
          val error = intercept[SparkException](result.collect())
          assert(error.getMessage.contains("iterator failure"))
      }
    }
  }

  test("release the connection even when a later completion listener throws") {
    withConnection {
      hc =>
        val result = hc.mapPartitions[Int, Int](
          sc.parallelize(Seq(1), 1),
          (it, conn) => {
            ConnectionLifetimeTestState.current.releaseSeed()
            TaskContext.get().addTaskCompletionListener[Unit] {
              _ => throw new IllegalStateException("completion listener failure")
            }
            it.map {
              value =>
                ConnectionLifetimeTestState.current.assertRetained(conn)
                value
            }
          })
        val error = intercept[SparkException](result.collect())
        assert(error.getMessage.contains("completion listener failure"))
    }
  }

  for (failInCallback <- Seq(false, true)) {
    test(s"release both borrows in one task when the second callback fails: $failInCallback") {
      withConnection {
        hc =>
          val first = hc.mapPartitions[Int, Int](
            sc.parallelize(Seq(1), 1),
            (it, _) => {
              ConnectionLifetimeTestState.current.releaseSeed()
              it
            })
          val second = hc.mapPartitions[Int, Int](
            first,
            (it, conn) => {
              ConnectionLifetimeTestState.current.assertRefCount(2)
              if (failInCallback) throw new IllegalStateException("second callback failure")
              it.map {
                value =>
                  ConnectionLifetimeTestState.current.assertRefCount(2)
                  ConnectionLifetimeTestState.current.assertRetained(conn)
                  value
              }
            })
          if (failInCallback) {
            val error = intercept[SparkException](second.collect())
            assert(error.getMessage.contains("second callback failure"))
          } else {
            assert(second.collect().toSeq == Seq(1))
          }
      }
    }
  }

  test("release the connection after partial consumption without evaluating the tail") {
    withConnection {
      hc =>
        val result = hc.mapPartitions[Int, Int](
          sc.parallelize(Seq(1, 2, 3), 1),
          (it, conn) => {
            ConnectionLifetimeTestState.current.releaseSeed()
            it.map {
              value =>
                ConnectionLifetimeTestState.current.assertRetained(conn)
                Predef.assert(value == 1, "the unused tail must remain lazy")
                value
            }
          })
        assert(result.mapPartitions(_.take(1)).collect().toSeq == Seq(1))
    }
  }

  test("release the connection for an empty iterator") {
    withConnection {
      hc =>
        val result = hc.mapPartitions[Int, Int](
          sc.parallelize(Seq(1), 1),
          (_, _) => {
            ConnectionLifetimeTestState.current.releaseSeed()
            Iterator.empty
          })
        assert(result.collect().isEmpty)
    }
  }

  test("release the connection when a consuming task is cancelled") {
    withConnection {
      hc =>
        val fixture = ConnectionLifetimeTestState.current
        val group = UUID.randomUUID().toString
        val result = hc.mapPartitions[Int, Int](
          sc.parallelize(Seq(1), 1),
          (it, _) => {
            ConnectionLifetimeTestState.current.releaseSeed()
            it.map {
              value =>
                val state = ConnectionLifetimeTestState.current
                state.taskAttemptId = TaskContext.get().taskAttemptId()
                state.started.countDown()
                Predef.assert(state.finishFirst.await(30, TimeUnit.SECONDS))
                value
            }
          })
        val taskEnded = new CountDownLatch(1)
        val listener = new SparkListener {
          override def onTaskEnd(event: SparkListenerTaskEnd): Unit = {
            if (event.taskInfo.taskId == fixture.taskAttemptId) taskEnded.countDown()
          }
        }
        sc.addSparkListener(listener)
        val executor = Executors.newSingleThreadExecutor()
        val action = executor.submit(new Callable[Array[Int]] {
          override def call(): Array[Int] = {
            sc.setJobGroup(group, "connection lifetime cancellation", interruptOnCancel = true)
            try result.collect()
            finally sc.clearJobGroup()
          }
        })
        try {
          // This test starts one task; the other count is reserved for the concurrency test.
          fixture.started.countDown()
          assert(fixture.started.await(10, TimeUnit.SECONDS))
          fixture.assertRefCount(1)
          sc.cancelJobGroup(group)
          val error = intercept[ExecutionException](action.get(10, TimeUnit.SECONDS))
          assert(error.getCause.isInstanceOf[SparkException])
          // Job cancellation can reach the driver before the executor finishes its listeners.
          assert(taskEnded.await(10, TimeUnit.SECONDS))
          fixture.assertRefCount(0)
          assert(fixture.finishFirst.getCount == 1)
        } finally {
          fixture.finishFirst.countDown()
          executor.shutdown()
          try {
            assert(executor.awaitTermination(15, TimeUnit.SECONDS))
            if (fixture.taskAttemptId >= 0) assert(taskEnded.await(10, TimeUnit.SECONDS))
          } finally {
            sc.removeSparkListener(listener)
          }
        }
    }
  }

  for (failFirst <- Seq(false, true)) {
    test(s"keep a shared connection when the first borrowing task fails: $failFirst") {
      withConnection {
        hc =>
          val fixture = ConnectionLifetimeTestState.current
          val executor = Executors.newFixedThreadPool(2)
          def submit(value: Int) = {
            val result = hc.mapPartitions[Int, Int](
              sc.parallelize(Seq(value), 1),
              (it, conn) => {
                ConnectionLifetimeTestState.current.releaseSeed()
                it.map {
                  value =>
                    val state = ConnectionLifetimeTestState.current
                    state.started.countDown()
                    val finish = if (value == 1) state.finishFirst else state.finishSecond
                    Predef.assert(finish.await(30, TimeUnit.SECONDS))
                    state.assertRetained(conn)
                    if (value == 1 && failFirst)
                      throw new IllegalStateException("shared task failure")
                    value
                }
              })
            executor.submit(new Callable[Array[Int]] {
              override def call(): Array[Int] = result.collect()
            })
          }
          val first = submit(1)
          val second = submit(2)
          try {
            assert(fixture.started.await(10, TimeUnit.SECONDS))
            fixture.assertRefCount(2)
            fixture.finishFirst.countDown()
            if (failFirst) {
              val error = intercept[ExecutionException](first.get(10, TimeUnit.SECONDS))
              assert(error.getCause.getMessage.contains("shared task failure"))
            } else {
              assert(first.get(10, TimeUnit.SECONDS).toSeq == Seq(1))
            }
            eventually(timeout(Span(10, Seconds)), interval(Span(10, Millis))) {
              fixture.assertRefCount(1)
            }
            fixture.assertRetained(fixture.smartConn.connection)
            assert(!second.isDone)
            fixture.finishSecond.countDown()
            assert(second.get(10, TimeUnit.SECONDS).toSeq == Seq(2))
          } finally {
            fixture.finishFirst.countDown()
            fixture.finishSecond.countDown()
            executor.shutdown()
            assert(executor.awaitTermination(15, TimeUnit.SECONDS))
          }
      }
    }
  }
}
