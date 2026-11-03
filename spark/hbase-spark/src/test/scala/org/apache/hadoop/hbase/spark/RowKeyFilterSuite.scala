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

import org.apache.hadoop.hbase.spark.datasources.{Bound, HBaseRegion, Points, Range, Ranges}
import org.apache.hadoop.hbase.util.Bytes
import org.scalatest.FunSuite

class RowKeyFilterSuite extends FunSuite {

  private def bytes(s: String): Array[Byte] = Bytes.toBytes(s)

  private def range(
      lower: String,
      upper: String,
      lowerInclusive: Boolean = true,
      upperInclusive: Boolean = true): ScanRange =
    new ScanRange(
      if (upper == null) null else bytes(upper),
      upperInclusive,
      bytes(lower),
      lowerInclusive)

  private def filter(
      ranges: Seq[ScanRange] = Seq.empty,
      points: Seq[Array[Byte]] = Seq.empty): RowKeyFilter = {
    val result = new RowKeyFilter(null, null)
    result.ranges ++= ranges
    result.points ++= points
    result
  }

  test("normalize deduplicates point contents, including binary keys") {
    val first = Array[Byte](0, 127)
    val second = Array[Byte](0, -128)
    val result = filter(points = Seq(first, first.clone(), second, second.clone())).normalize()
    assert(result.points.size == 2)
    assert(result.points.head.sameElements(first))
    assert(result.points.last.sameElements(second))
  }

  test("normalize removes covered points and respects inclusive and exclusive bounds") {
    for (lowerInclusive <- Seq(false, true); upperInclusive <- Seq(false, true)) {
      val result = filter(
        ranges = Seq(range("a", "m", lowerInclusive, upperInclusive)),
        points = Seq("a", "b", "m", "z", "z").map(bytes)).normalize()
      val expected = (if (lowerInclusive) Seq.empty else Seq("a")) ++
        (if (upperInclusive) Seq.empty else Seq("m")) ++ Seq("z")
      assert(result.points.map(Bytes.toString(_)).toSeq == expected)
      assert(result.ranges.size == 1)
    }
  }

  test("normalize preserves point order across disjoint ranges and gaps") {
    for (lowerInclusive <- Seq(false, true); upperInclusive <- Seq(false, true)) {
      val scans = Seq(
        range("m", null, lowerInclusive, true),
        range("a", "c", lowerInclusive, upperInclusive),
        range("g", "i", lowerInclusive, upperInclusive))
      val keys = Seq("z", "i", "0", "h", "m", "d", "b", "g", "c", "a", "l", "d", "0")
      val expected = keys.distinct.filterNot(key => scans.exists(_.containsPoint(bytes(key))))
      val result = filter(ranges = scans, points = keys.map(bytes)).normalize()
      assert(result.points.map(Bytes.toString(_)).toSeq == expected)
      val normalized = result.toString
      assert(result.normalize().toString == normalized)
    }
  }

  test("normalize checks at most one range per unique point") {
    val count = 1024
    var checks = 0
    val scans = (0 until count).map {
      i =>
        new ScanRange(Bytes.toBytes(4 * i + 1), true, Bytes.toBytes(4 * i), true) {
          override def containsPoint(point: Array[Byte]): Boolean = {
            checks += 1
            super.containsPoint(point)
          }
        }
    }
    val keys = (0 until count).reverse.map(i => Bytes.toBytes(4 * i + 2))
    val result = filter(ranges = scans.reverse, points = keys ++ keys.map(_.clone())).normalize()
    assert(result.ranges.size == count)
    assert(result.points.size == count)
    assert(
      result.points.zip(keys).forall { case (actual, expected) => actual.sameElements(expected) })
    assert(checks <= count, s"$checks range checks for $count unique points")
  }

  test("normalize coalesces bridged ranges in every input order") {
    val bounds = Seq(("a", "c"), ("g", "i"), ("b", "h"))
    bounds.permutations.foreach {
      permutation =>
        val result = filter()
        permutation.foreach {
          case (lower, upper) =>
            result.mergeUnion(filter(ranges = Seq(range(lower, upper))))
        }
        result.normalize()
        assert(result.ranges.size == 1)
        assert(Bytes.toString(result.ranges.head.lowerBound) == "a")
        assert(Bytes.toString(result.ranges.head.upperBound) == "i")
        assert(result.ranges.head.isLowerBoundEqualTo)
        assert(result.ranges.head.isUpperBoundEqualTo)
    }
  }

  test("normalize coalesces touching ranges only when their shared endpoint is included") {
    for (leftInclusive <- Seq(false, true); rightInclusive <- Seq(false, true)) {
      val result = filter(ranges =
        Seq(range("m", "z", rightInclusive, true), range("a", "m", true, leftInclusive)))
        .normalize()
      assert(result.ranges.size == (if (leftInclusive || rightInclusive) 1 else 2))
      assert(
        result.ranges.exists(_.containsPoint(bytes("m"))) ==
          (leftInclusive || rightInclusive))
    }
  }

  test("normalize handles nested, equal and unbounded ranges without narrowing them") {
    val result = filter(
      ranges = Seq(range("b", "c"), range("a", null), range("a", "z"), range("", "b")),
      points = Seq(bytes("a"), bytes("z"))).normalize()
    assert(result.ranges.size == 1)
    assert(result.ranges.head.lowerBound.isEmpty)
    assert(result.ranges.head.upperBound == null)
    assert(result.points.isEmpty)
    val normalized = result.toString
    assert(result.normalize().toString == normalized)
  }

  test("normalize preserves empty filters, full scans and inclusive singleton ranges") {
    val empty = filter().normalize()
    assert(empty.ranges.isEmpty && empty.points.isEmpty)
    val full = new RowKeyFilter().normalize()
    assert(full.ranges.size == 1)
    assert(full.ranges.head.lowerBound.isEmpty)
    assert(full.ranges.head.upperBound == null)
    val singleton =
      filter(ranges = Seq(range("m", "m")), points = Seq(bytes("m"), bytes("z"))).normalize()
    assert(singleton.ranges.size == 1)
    assert(singleton.points.map(Bytes.toString(_)).toSeq == Seq("z"))
  }

  test("normalize removes duplicate points produced by intersections") {
    val result = filter(points = Seq("a", "b").map(bytes))
      .mergeIntersect(filter(points = Seq("a", "b").map(bytes)))
      .normalize()
    assert(result.points.map(Bytes.toString(_)).toSeq == Seq("a", "b"))
  }

  test("normalize preserves candidate membership and gives each binary key one read") {
    val keys = Seq(
      Array.empty[Byte],
      Array[Byte](0),
      Array[Byte](0, 0),
      Array[Byte](127),
      Array[Byte](-128),
      Array[Byte](-1))
    for (lowerInclusive <- Seq(false, true); upperInclusive <- Seq(false, true)) {
      val result = filter(
        ranges = Seq(
          new ScanRange(keys(2), upperInclusive, keys(1), lowerInclusive),
          new ScanRange(keys(5), false, keys(4), true)),
        points = Seq(keys(3), keys(3).clone()))
      val expected = keys.map {
        key =>
          result.ranges.exists(_.containsPoint(key)) || result.points.exists(Bytes.equals(_, key))
      }
      result.normalize()
      keys.zip(expected).foreach {
        case (key, included) =>
          val reads = result.ranges.count(_.containsPoint(key)) +
            result.points.count(Bytes.equals(_, key))
          assert(reads == (if (included) 1 else 0))
      }
    }
  }

  test("normalized reads stay disjoint across first, prefix-adjacent and last regions") {
    val middle = bytes("m")
    val successor = Bytes.add(middle, Array[Byte](0))
    val regions = Seq(
      HBaseRegion(0, Some(Array.empty[Byte]), Some(middle)),
      HBaseRegion(1, Some(middle), Some(successor)),
      HBaseRegion(2, Some(successor), Some(Array.empty[Byte]))).map(region => Range(region))
    for (includePoint <- Seq(false, true)) {
      val plan = filter(
        ranges = Seq(range("", "m", true, false), range("m", null, false, true)),
        points = if (includePoint) Seq(middle, middle.clone()) else Seq.empty).normalize()
      val scans = plan.ranges.toSeq.map {
        scan =>
          Range(
            Option(scan.lowerBound).filter(_.nonEmpty).map(Bound(_, scan.isLowerBoundEqualTo)),
            Option(scan.upperBound).map(Bound(_, scan.isUpperBoundEqualTo)))
      }
      val partitions = regions.map {
        region => (Ranges.and(region, scans), Points.and(region, plan.points.toSeq))
      }
      Seq(Array[Byte](0), middle, successor, bytes("ma"), Array[Byte](-1)).foreach {
        key =>
          val reads = partitions.map {
            case (ranges, points) =>
              ranges.count {
                scan =>
                  scan.lower.forall {
                    lower =>
                      val cmp = Bytes.compareTo(key, lower.b)
                      cmp > 0 || (cmp == 0 && lower.inc)
                  } && scan.upper.forall {
                    upper =>
                      val cmp = Bytes.compareTo(key, upper.b)
                      cmp < 0 || (cmp == 0 && upper.inc)
                  }
              } + points.count(Bytes.equals(_, key))
          }.sum
          assert(reads == (if (Bytes.equals(key, middle) && !includePoint) 0 else 1))
      }
    }
  }
}
