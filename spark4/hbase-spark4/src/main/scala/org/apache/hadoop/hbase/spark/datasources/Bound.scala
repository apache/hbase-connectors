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

/**
 * The Bound represent the boudary for the scan
 *
 * @param b The byte array of the bound
 * @param inc inclusive or not.
 */
@InterfaceAudience.Private
case class Bound(b: Array[Byte], inc: Boolean)

@InterfaceAudience.Private
case class Range(lower: Option[Bound], upper: Option[Bound])

@InterfaceAudience.Private
object Range {
  def apply(region: HBaseRegion): Range = {
    Range(
      region.start.map(Bound(_, true)),
      if (region.end.get.length == 0) {
        None
      } else {
        region.end.map(Bound(_, false))
      })
  }
}

@InterfaceAudience.Private
object Ranges {
  def and(r: Range, rs: Seq[Range]): Seq[Range] = {
    rs.flatMap { s =>
      val lower = s.lower
        .map { x =>
          r.lower
            .map { y =>
              if (ord.compare(x.b, y.b) < 0) {
                Some(y)
              } else {
                Some(x)
              }
            }
            .getOrElse(Some(x))
        }
        .getOrElse(r.lower)

      val upper = s.upper
        .map { x =>
          r.upper
            .map { y =>
              if (ord.compare(x.b, y.b) >= 0) {
                Some(y)
              } else {
                Some(x)
              }
            }
            .getOrElse(Some(x))
        }
        .getOrElse(r.upper)

      val c = lower
        .map { x =>
          upper
            .map { y =>
              ord.compare(x.b, y.b)
            }
            .getOrElse(-1)
        }
        .getOrElse(-1)
      if (c < 0) {
        Some(Range(lower, upper))
      } else {
        None
      }
    }
  }
}

@InterfaceAudience.Private
object Points {
  def and(r: Range, ps: Seq[Array[Byte]]): Seq[Array[Byte]] = {
    ps.flatMap { p =>
      if (ord.compare(r.lower.get.b, p) <= 0) {
        if (r.upper.isDefined) {
          if (ord.compare(r.upper.get.b, p) > 0) {
            Some(p)
          } else {
            None
          }
        } else {
          Some(p)
        }
      } else {
        None
      }
    }
  }
}
