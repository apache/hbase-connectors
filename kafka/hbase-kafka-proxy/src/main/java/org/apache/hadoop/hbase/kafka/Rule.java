/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.kafka;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.yetus.audience.InterfaceAudience;


/**
 * Implements the matching logic for a rule
 */
@InterfaceAudience.Private
public abstract class Rule {
  TableName tableName;
  private byte [] columnFamily;
  private byte [] qualifier;

  boolean qualifierStartsWith = false;
  boolean qualifierEndsWith = false;

  byte []ast = Bytes.toBytes("*");

  /**
   * Indicates if the table,column family, and qualifier match the rule
   * @param tryTable table name to test
   * @param tryColumFamily column family to test
   * @param tryQualifier qualifier to test
   * @return true if values match the rule
   */
  public boolean match(TableName tryTable, byte [] tryColumFamily, byte [] tryQualifier) {
    boolean tableMatch = tableMatch(tryTable);
    boolean columnFamilyMatch = columnFamilyMatch(tryColumFamily);
    boolean qualfierMatch = qualifierMatch(tryQualifier);

    return tableMatch && columnFamilyMatch && qualfierMatch;
  }

  /**
   * Test if the qualifier matches
   * @param tryQualifier qualifier to test
   * @return true if the qualifier matches
   */
  public boolean qualifierMatch(byte [] tryQualifier) {

    if (qualifier != null) {
      if (qualifierStartsWith && qualifierEndsWith) {
        return (startsWith(tryQualifier, this.qualifier) || endsWith(tryQualifier, this.qualifier));
      } else if (qualifierStartsWith) {
        return startsWith(tryQualifier, this.qualifier);
      } else if (qualifierEndsWith) {
        return endsWith(tryQualifier, this.qualifier);
      } else {
        return Bytes.equals(this.qualifier, tryQualifier);
      }
    }
    return true;
  }

  /**
   * Test if the column family matches the rule
   * @param tryColumFamily column family to test
   * @return true if the column family matches the rule
   */
  public boolean columnFamilyMatch(byte [] tryColumFamily) {
    if (columnFamily != null) {
      return Bytes.equals(this.columnFamily, tryColumFamily);
    }
    return true;
  }

  /**
   * Test if the table matches the table in the rule
   * @param tryTable table name to test
   * @return true if the table matches the rule
   */
  public boolean tableMatch(TableName tryTable) {
    if (tableName == null) {
      return true;
    }
    return (tryTable.equals(this.tableName));
  }

  /**
   * set the column family for the rule
   * @param columnFamily column family to set
   */
  public void setColumnFamily(byte [] columnFamily) {
    this.columnFamily = columnFamily;
  }

  /**
   * set the qualifier value for the rule
   * @param qualifier qualifier to set
   */
  public void setQualifier(byte []qualifier) {
    this.qualifier = qualifier;
    if (startsWith(qualifier, ast)) {
      qualifierEndsWith = true;
      this.qualifier = ArrayUtils.subarray(this.qualifier, ast.length, this.qualifier.length);
    }
    if (endsWith(qualifier, ast)) {
      qualifierStartsWith = true;
      this.qualifier = ArrayUtils.subarray(this.qualifier, 0, this.qualifier.length - ast.length);
    }
    if ((qualifierStartsWith) || (qualifierEndsWith)) {
      if (this.qualifier.length == 0) {
        this.qualifier = null;
      }
    }

  }

  /**
   * Tests if data starts with startsWith
   * @param data byte array to test
   * @param startsWith array that we want to see if data starts with
   * @return true if data starts with startsWith
   */
  public static boolean startsWith(byte [] data, byte [] startsWith) {
    if (startsWith.length > data.length) {
      return false;
    }

    if (startsWith.length == data.length) {
      return Bytes.equals(data, startsWith);
    }

    for (int i = 0; i < startsWith.length; i++) {
      if (startsWith[i] != data[i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Tests if data ends with endsWith
   * @param data byte array to test
   * @param endsWith array that we want to see if data ends with
   * @return true if data ends with endsWith
   */
  public static boolean endsWith(byte [] data, byte [] endsWith) {
    if (endsWith.length > data.length) {
      return false;
    }

    if (endsWith.length == data.length) {
      return Bytes.equals(data, endsWith);
    }

    int endStart = data.length - endsWith.length;

    for (int i = 0; i < endsWith.length; i++) {
      //if (endsWith[i]!=data[(data.length-1)-(endsWith.length+i)]){
      if (endsWith[i] != data[endStart + i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * get the table for the rule
   * @return tablename for ule
   */
  public TableName getTableName() {
    return tableName;
  }

  /**
   * set the table for the rule
   * @param tableName to set
   */
  public void setTableName(TableName tableName) {
    this.tableName = tableName;
  }

  /**
   * get the column family for the rule
   * @return column family
   */
  public byte[] getColumnFamily() {
    return columnFamily;
  }

  /**
   * get the qualifier for the rule
   * @return qualfier
   */
  public byte[] getQualifier() {
    return qualifier;
  }


  /**
   * indicates if the qualfier is a wildcard like *foo
   * @return true if rule is like *foo
   */
  public boolean isQualifierEndsWith() {
    return qualifierEndsWith;
  }

  /**
   * indicates if the qualfier is a wildcard like foo*
   * @return true if rule is like foo*
   */
  public boolean isQualifierStartsWith() {
    return qualifierStartsWith;

  }
}
