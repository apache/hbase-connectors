/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.hadoop.hbase.kafka;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.test.MockSerializer;

/**
 * Mocks Kafka producer for testing
 */
public class ProducerForTesting extends MockProducer<byte[], byte[]> {
  Map<String, List<HBaseKafkaEvent>> messages = new HashMap<>();
  SpecificDatumReader<HBaseKafkaEvent> dreader = new SpecificDatumReader<>(HBaseKafkaEvent.SCHEMA$);

  public ProducerForTesting() {
    super(true, new MockSerializer(), new MockSerializer());
  }

  public Map<String, List<HBaseKafkaEvent>> getMessages() {
    return messages;
  }

  @Override
  public Future<RecordMetadata> send(ProducerRecord<byte[], byte[]> producerRecord) {
    try {
      BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(producerRecord.value(), null);
      HBaseKafkaEvent event = dreader.read(null, decoder);
      if (!messages.containsKey(producerRecord.topic())) {
        messages.put(producerRecord.topic(), new ArrayList<>());
      }
      messages.get(producerRecord.topic()).add(event);
      return super.send(producerRecord);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
