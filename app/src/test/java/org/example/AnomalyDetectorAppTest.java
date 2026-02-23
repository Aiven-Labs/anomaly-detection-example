package org.example;

import static org.junit.jupiter.api.Assertions.*;

import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry;
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.errors.StreamsException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

@ExtendWith(SystemStubsExtension.class)
class AnomalyDetectorAppTests {

    // After we've specified this, each test should unset any System property changes when it ends
    @SystemStub
    private SystemProperties systemProperties;

    static final String inputTopicName = "input_topic";
    static final String outputTopicName = "output_topic";

    static TopologyTestDriver testDriver;

    static TestInputTopic<String, GenericRecord> inputTopic;
    static TestOutputTopic<String, GenericRecord> outputTopic;

    static AvroSchema avroSchema;

    static AvroSchema registerSchema(String fieldName, String fieldType) {
        AvroSchema schema = new AvroSchema("""
                    {
                      "namespace": "data.gen.avro",
                      "name": "with_%s",
                      "type": "record",
                      "fields": [ { "name": "%s", "type": { "type": "%s" } } ]
                    }
                    """.formatted(fieldName, fieldName, fieldType));
        // AvroSchema implements ParsedSchema
        SchemaRegistryClient client = MockSchemaRegistry.getClientForScope("test_schema_registry");
        try {
            int schemaId = client.register("test_schema_registry", schema);
            System.out.println("Schema registered as " + schemaId);
        } catch (IOException | RestClientException e) {
            System.out.println("Unable to register input schema " + e);
        }
        return schema;
    }

    // ==================================================================
    @Nested
    @DisplayName("When field to check is an int")
    class FieldIsAnInt {

        static String fieldName = "intField";

        @BeforeAll
        static void setup() {
            SetupProperties.setProperties(inputTopicName, outputTopicName, fieldName, 10, 20);

            Properties config = Config.getConfig();
            Map<String, String> serdeConfig = Config.getSerdeConfig(config);

            avroSchema = registerSchema(fieldName, "int");

            Topology topology = AnomalyDetectorApp.buildTopology(config, serdeConfig);
            testDriver = new TopologyTestDriver(topology, config);

            GenericAvroSerde avroSerde = new GenericAvroSerde();
            avroSerde.configure(serdeConfig, false); // just for the value

            inputTopic = testDriver.createInputTopic(inputTopicName, Serdes.String().serializer(), avroSerde.serializer());
            outputTopic = testDriver.createOutputTopic(outputTopicName, Serdes.String().deserializer(), avroSerde.deserializer());
        }

        @AfterAll
        static void tearDown() {
            testDriver.close();
        }

        @Test
        @DisplayName("Test a message with value too small is copied")
        void testMessageWithValueTooSmallIsCopied() {
            GenericRecord inputValue = new GenericData.Record(avroSchema.rawSchema());
            inputValue.put(fieldName, (int) 5);
            inputTopic.pipeInput("key", inputValue);

            KeyValue outputRecord = outputTopic.readKeyValue();

            assertNotNull(outputRecord);

            GenericRecord outputValue = (GenericRecord) outputRecord.value;
            assertEquals(inputValue, outputValue);
        }

        @Test
        @DisplayName("Test a message with value too big is copied")
        void testMessageWithValueTooBigIsCopied() {
            GenericRecord inputValue = new GenericData.Record(avroSchema.rawSchema());
            inputValue.put(fieldName, (int) 25);
            inputTopic.pipeInput("key", inputValue);

            KeyValue outputRecord = outputTopic.readKeyValue();

            assertNotNull(outputRecord);

            GenericRecord outputValue = (GenericRecord) outputRecord.value;
            assertEquals(inputValue, outputValue);
        }

        @Test
        @DisplayName("Test a message with value in range is not copied")
        void testMessageWithValueInRangeIsNotCopied() {
            GenericRecord inputValue = new GenericData.Record(avroSchema.rawSchema());
            inputValue.put(fieldName, (int) 15);
            inputTopic.pipeInput("key", inputValue);

            assertTrue(outputTopic.isEmpty(), "In-range message does not come through");
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("When field to check is a long")
    class FieldIsALong {

        static String fieldName = "longField";

        @BeforeAll
        static void setup() {
            SetupProperties.setProperties(inputTopicName, outputTopicName, fieldName, 10, 20);

            Properties config = Config.getConfig();
            Map<String, String> serdeConfig = Config.getSerdeConfig(config);

            avroSchema = registerSchema(fieldName, "long");

            Topology topology = AnomalyDetectorApp.buildTopology(config, serdeConfig);
            testDriver = new TopologyTestDriver(topology, config);

            GenericAvroSerde avroSerde = new GenericAvroSerde();
            avroSerde.configure(serdeConfig, false); // just for the value

            inputTopic = testDriver.createInputTopic(inputTopicName, Serdes.String().serializer(), avroSerde.serializer());
            outputTopic = testDriver.createOutputTopic(outputTopicName, Serdes.String().deserializer(), avroSerde.deserializer());
        }

        @AfterAll
        static void tearDown() {
            testDriver.close();
        }

        @Test
        @DisplayName("Test a message with value too small is copied")
        void testMessageWithValueTooSmallIsCopied() {
            GenericRecord inputValue = new GenericData.Record(avroSchema.rawSchema());
            inputValue.put(fieldName, (long) 5);
            inputTopic.pipeInput("key", inputValue);

            KeyValue outputRecord = outputTopic.readKeyValue();

            assertNotNull(outputRecord);

            GenericRecord outputValue = (GenericRecord) outputRecord.value;
            assertEquals(inputValue, outputValue);
        }

        @Test
        @DisplayName("Test a message with value too big is copied")
        void testMessageWithValueTooBigIsCopied() {
            GenericRecord inputValue = new GenericData.Record(avroSchema.rawSchema());
            inputValue.put(fieldName, (long) 25);
            inputTopic.pipeInput("key", inputValue);

            KeyValue outputRecord = outputTopic.readKeyValue();

            assertNotNull(outputRecord);

            GenericRecord outputValue = (GenericRecord) outputRecord.value;
            assertEquals(inputValue, outputValue);
        }

        @Test
        @DisplayName("Test a message with value in range is not copied")
        void testMessageWithValueInRangeIsNotCopied() {
            GenericRecord inputValue = new GenericData.Record(avroSchema.rawSchema());
            inputValue.put(fieldName, (long) 15);
            inputTopic.pipeInput("key", inputValue);

            assertTrue(outputTopic.isEmpty(), "In-range message does not come through");
        }
    }
}