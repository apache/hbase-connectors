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


import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.yetus.audience.InterfaceAudience;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * The topic routing/drop rules.
 *
 *  &lt;rules&gt;
 *      &lt;rule .... /&gt;
 *
 *  &lt;/rules&gt;
 *
 *
 *
 * A wildcard can only be at the beginning or at the end (can be at both sides).
 *
 * drop rules are always evaluated first.
 *
 * drop examples:
 * &lt;rule action="drop" table="default:MyTable" /&gt;
 * Do not send replication events for table MyTable
 *
 * &lt;rule action="drop" table="default:MyTable" columnFamily="data"/&gt;
 * Do not send replication events for table MyTable's column family data
 *
 * &lt;rule action="drop" table="default:MyTable" columnFamily="data" qualfier="dhold:*"/&gt;
 * Do not send replication events for any qualiifier on table MyTable with column family data
 *
 * routeRules examples:
 *
 * &lt;rule action="routeRules" table="default:MyTable" topic="mytopic"/&gt;
 * routeRules all replication events for table default:Mytable to topic mytopic
 *
 * &lt;rule action="routeRules" table="default:MyTable" columnFamily="data" topic="mytopic"/&gt;
 * routeRules all replication events for table default:Mytable column family data to topic mytopic
 *
 * &lt;rule action="routeRules" table="default:MyTable" columnFamily="data" topic="mytopic"
 *  qualifier="hold:*"/&gt;
 * routeRules all replication events for qualifiers that start with hold: for table
 *  default:Mytable column family data to topic mytopic
 */
@InterfaceAudience.Private
public class TopicRoutingRules {

  private List<DropRule> dropRules = new ArrayList<>();
  private List<TopicRule> routeRules = new ArrayList<>();

  private File sourceFile;

  /**
   * used for testing
   */
  public TopicRoutingRules() {

  }

  /**
   * construct rule set from file
   * @param source file that countains the rule set
   * @throws Exception if load fails
   */
  public TopicRoutingRules(File source) throws Exception {
    this.sourceFile = source;
    this.reloadIfFile();
  }

  /**
   * Reload the ruleset if it was parsed from a file
   * @throws Exception error loading rule set
   */
  public void reloadIfFile() throws Exception {
    if (this.sourceFile!=null){
      List<DropRule> dropRulesSave = this.dropRules;
      List<TopicRule> routeRulesSave = this.routeRules;

      try (FileInputStream fin = new FileInputStream(this.sourceFile)) {
        List<DropRule> dropRulesNew = new ArrayList<>();
        List<TopicRule> routeRulesNew = new ArrayList<>();

        parseRules(fin,dropRulesNew,routeRulesNew);

        this.dropRules = dropRulesNew;
        this.routeRules = routeRulesNew;

      } catch (Exception e){
        // roll back
        this.dropRules=dropRulesSave;
        this.routeRules=routeRulesSave;
        // re-throw
        throw e;
      }
    }
  }

  /**
   * parse rules manually from an input stream
   * @param input InputStream that contains rule text
   */
  public void parseRules(InputStream input) {
    List<DropRule> dropRulesNew = new ArrayList<>();
    List<TopicRule> routeRulesNew = new ArrayList<>();
    parseRules(input,dropRulesNew,routeRulesNew);
    this.dropRules = dropRulesNew;
    this.routeRules = routeRulesNew;
  }

  /**
   * Parse the XML in the InputStream into route/drop rules and store them in the passed in Lists
   * @param input inputstream the contains the ruleset
   * @param dropRules list to accumulate drop rules
   * @param routeRules list to accumulate route rules
   */
  public void parseRules(InputStream input,List<DropRule> dropRules, List<TopicRule> routeRules) {
    try {
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc = dBuilder.parse(input);
      NodeList nodList = doc.getElementsByTagName("rule");
      for (int i = 0; i < nodList.getLength(); i++) {
        if (nodList.item(i) instanceof Element) {
          parseRule((Element) nodList.item(i),dropRules,routeRules);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Parse a individual rule from a Element.
   * @param n the element
   * @param dropRules list to accumulate drop rules
   * @param routeRules list to accumulate route rules
   */
  public void parseRule(Element n, List<DropRule> dropRules, List<TopicRule> routeRules) {
    Rule r = null;
    if (n.getAttribute("action").equals("drop")) {
      r = new DropRule();
      dropRules.add((DropRule) r);
    } else {
      r = new TopicRule(n.getAttribute("topic"));
      routeRules.add((TopicRule) r);
    }
    if (n.hasAttribute("table")) {
      r.setTableName(TableName.valueOf(n.getAttribute("table")));
    }
    if (n.hasAttribute("columnFamily")) {
      r.setColumnFamily(Bytes.toBytes(n.getAttribute("columnFamily")));
    }
    if (n.hasAttribute("qualifier")) {
      String qual = n.getAttribute("qualifier");
      r.setQualifier(Bytes.toBytes(qual));
    }
  }

  /**
   * Indicates if a cell mutation should be dropped instead of routed to kafka.
   * @param table table name to check
   * @param columnFamily column family to check
   * @param qualifer qualifier name to check
   * @return if the mutation should be dropped instead of routed to Kafka
   */
  public boolean isExclude(final TableName table, final byte []columnFamily,
                           final byte[] qualifer) {
    for (DropRule r : getDropRules()) {
      if (r.match(table, columnFamily, qualifer)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Get topics for the table/column family/qualifier combination
   * @param table table name to check
   * @param columnFamily column family to check
   * @param qualifer qualifier name to check
   * @return list of topics that match the passed in values (or empty for none).
   */
  public List<String> getTopics(TableName table, byte []columnFamily, byte []qualifer) {
    List<String> ret = new ArrayList<>();
    for (TopicRule r : getRouteRules()) {
      if (r.match(table, columnFamily, qualifer)) {
        ret.addAll(r.getTopics());
      }
    }

    return ret;
  }

  /**
   * returns all the drop rules (used for testing)
   * @return drop rules
   */
  public List<DropRule> getDropRules() {
    return dropRules;
  }

  /**
   * returns all the route rules (used for testing)
   * @return route rules
   */
  public List<TopicRule> getRouteRules() {
    return routeRules;
  }
}
