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
import org.apache.yetus.audience.InterfaceAudience
import scala.collection.mutable.ListBuffer

/**
 * This is a new class in the spark4 module. Wraps ScanRange and RowKeyFilter.
 *
 * Extracted from DefaultSource.scala in spark3 where it was an inner class. Handles merging scan ranges
 * from row key predicates (union/intersect). Same logic, just in its own file now for clarity.
 *
 * @param upperBound
 * @param isUpperBoundEqualTo
 * @param lowerBound
 * @param isLowerBoundEqualTo
 */

@InterfaceAudience.Private
class ScanRange(
    var upperBound: Array[Byte],
    var isUpperBoundEqualTo: Boolean,
    var lowerBound: Array[Byte],
    var isLowerBoundEqualTo: Boolean)
    extends Serializable {

  def mergeIntersect(other: ScanRange): Unit = {
    val upperBoundCompare = compareRange(upperBound, other.upperBound)
    val lowerBoundCompare = compareRange(lowerBound, other.lowerBound)

    upperBound = if (upperBoundCompare < 0) upperBound else other.upperBound
    lowerBound = if (lowerBoundCompare > 0) lowerBound else other.lowerBound

    isLowerBoundEqualTo =
      if (lowerBoundCompare == 0)
        isLowerBoundEqualTo && other.isLowerBoundEqualTo
      else if (lowerBoundCompare < 0) other.isLowerBoundEqualTo
      else isLowerBoundEqualTo

    isUpperBoundEqualTo =
      if (upperBoundCompare == 0)
        isUpperBoundEqualTo && other.isUpperBoundEqualTo
      else if (upperBoundCompare < 0) isUpperBoundEqualTo
      else other.isUpperBoundEqualTo
  }

  def mergeUnion(other: ScanRange): Unit = {
    val upperBoundCompare = compareRange(upperBound, other.upperBound)
    val lowerBoundCompare = compareRange(lowerBound, other.lowerBound)

    upperBound = if (upperBoundCompare > 0) upperBound else other.upperBound
    lowerBound = if (lowerBoundCompare < 0) lowerBound else other.lowerBound

    isLowerBoundEqualTo =
      if (lowerBoundCompare == 0)
        isLowerBoundEqualTo || other.isLowerBoundEqualTo
      else if (lowerBoundCompare < 0) isLowerBoundEqualTo
      else other.isLowerBoundEqualTo

    isUpperBoundEqualTo =
      if (upperBoundCompare == 0)
        isUpperBoundEqualTo || other.isUpperBoundEqualTo
      else if (upperBoundCompare < 0) other.isUpperBoundEqualTo
      else isUpperBoundEqualTo
  }

  def getOverLapScanRange(other: ScanRange): ScanRange = {
    var leftRange: ScanRange = null
    var rightRange: ScanRange = null

    if (compareRange(lowerBound, other.lowerBound) < 0 ||
      compareRange(upperBound, other.upperBound) < 0) {
      leftRange = this
      rightRange = other
    } else {
      leftRange = other
      rightRange = this
    }

    if (hasOverlap(leftRange, rightRange)) {
      val result = new ScanRange(upperBound, isUpperBoundEqualTo, lowerBound, isLowerBoundEqualTo)
      result.mergeIntersect(other)
      result
    } else {
      null
    }
  }

  def hasOverlap(left: ScanRange, right: ScanRange): Boolean = {
    val cmp = compareRange(left.upperBound, right.lowerBound)
    if (cmp > 0) true
    else if (cmp == 0) left.isUpperBoundEqualTo && right.isLowerBoundEqualTo
    else false
  }

  def compareRange(left: Array[Byte], right: Array[Byte]): Int = {
    if (left == null && right == null) 0
    else if (left == null && right != null) 1
    else if (left != null && right == null) -1
    else Bytes.compareTo(left, right)
  }

  def containsPoint(point: Array[Byte]): Boolean = {
    val lowerCompare = compareRange(point, lowerBound)
    val upperCompare = compareRange(point, upperBound)

    ((isLowerBoundEqualTo && lowerCompare >= 0) ||
      (!isLowerBoundEqualTo && lowerCompare > 0)) &&
    ((isUpperBoundEqualTo && upperCompare <= 0) ||
      (!isUpperBoundEqualTo && upperCompare < 0))
  }

  override def toString: String = {
    "ScanRange:(upperBound:" + Bytes.toString(upperBound) +
      ",isUpperBoundEqualTo:" + isUpperBoundEqualTo + ",lowerBound:" +
      Bytes.toString(lowerBound) + ",isLowerBoundEqualTo:" + isLowerBoundEqualTo + ")"
  }
}

@InterfaceAudience.Private
class RowKeyFilter(
    currentPoint: Array[Byte] = null,
    currentRange: ScanRange = new ScanRange(null, true, new Array[Byte](0), true),
    var points: ListBuffer[Array[Byte]] = new ListBuffer[Array[Byte]](),
    var ranges: ListBuffer[ScanRange] = new ListBuffer[ScanRange]())
    extends Serializable {

  if (currentRange != null) ranges += currentRange
  if (currentPoint != null) points += currentPoint

  // Normalize after the complete filter tree is built, before region pruning and Get batching.
  def normalize(): RowKeyFilter = {
    val normalizedRanges = new ListBuffer[ScanRange]()
    ranges
      .sortWith((left, right) => Bytes.compareTo(left.lowerBound, right.lowerBound) < 0)
      .foreach {
        range =>
          normalizedRanges.lastOption match {
            case Some(previous) =>
              val cmp = previous.compareRange(previous.upperBound, range.lowerBound)
              if (cmp > 0 ||
                (cmp == 0 && (previous.isUpperBoundEqualTo || range.isLowerBoundEqualTo))) {
                previous.mergeUnion(range)
              } else {
                normalizedRanges += range
              }
            case None => normalizedRanges += range
          }
      }
    ranges = normalizedRanges
    val sortedRanges = ranges.toArray
    def coveredByRange(point: Array[Byte]): Boolean = {
      var low = 0
      var high = sortedRanges.length
      // Find the last range whose lower bound is at or before the point.
      while (low < high) {
        val mid = (low + high) >>> 1
        if (Bytes.compareTo(sortedRanges(mid).lowerBound, point) <= 0) {
          low = mid + 1
        } else {
          high = mid
        }
      }
      low > 0 && sortedRanges(low - 1).containsPoint(point)
    }
    val seen = new java.util.TreeSet[Array[Byte]](Bytes.BYTES_COMPARATOR)
    points = points.filter(p => seen.add(p) && !coveredByRange(p))
    this
  }

  def mergeUnion(other: RowKeyFilter): RowKeyFilter = {
    other.points.foreach(p => points += p)

    other.ranges.foreach { otherR =>
      var doesOverLap = false
      ranges.foreach { r =>
        if (r.getOverLapScanRange(otherR) != null) {
          r.mergeUnion(otherR)
          doesOverLap = true
        }
      }
      if (!doesOverLap) ranges += otherR
    }
    this
  }

  def mergeIntersect(other: RowKeyFilter): RowKeyFilter = {
    val survivingPoints = new ListBuffer[Array[Byte]]()
    val didntSurviveFirstPassPoints = new ListBuffer[Array[Byte]]()
    if (points == null || points.isEmpty) {
      other.points.foreach(otherP => didntSurviveFirstPassPoints += otherP)
    } else {
      points.foreach { p =>
        if (other.points.exists(Bytes.equals(p, _))) {
          survivingPoints += p
        } else {
          didntSurviveFirstPassPoints += p
        }
      }
    }

    val survivingRanges = new ListBuffer[ScanRange]()

    if (ranges.isEmpty) {
      didntSurviveFirstPassPoints.foreach(p => survivingPoints += p)
    } else {
      ranges.foreach { r =>
        other.ranges.foreach { otherR =>
          val overLapScanRange = r.getOverLapScanRange(otherR)
          if (overLapScanRange != null) {
            survivingRanges += overLapScanRange
          }
        }
        didntSurviveFirstPassPoints.foreach { p =>
          if (r.containsPoint(p)) {
            survivingPoints += p
          }
        }
      }
    }
    points = survivingPoints
    ranges = survivingRanges
    this
  }

  override def toString: String = {
    val strBuilder = new StringBuilder
    strBuilder.append("(points:(")
    var isFirst = true
    points.foreach { p =>
      if (isFirst) isFirst = false
      else strBuilder.append(",")
      strBuilder.append(Bytes.toString(p))
    }
    strBuilder.append("),ranges:")
    isFirst = true
    ranges.foreach { r =>
      if (isFirst) isFirst = false
      else strBuilder.append(",")
      strBuilder.append(r)
    }
    strBuilder.append("))")
    strBuilder.toString()
  }
}
