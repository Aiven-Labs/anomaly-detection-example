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

    static TopologyTestDriver testDriver;

    @Nested
    @DisplayName("When field to check is an int")
    class InputIsPartialData {

        static TestInputTopic<String, GenericRecord> inputTopic;
        static TestOutputTopic<String, GenericRecord> outputTopic;

        static final String inputTopicName = "input_topic";
        static final String outputTopicName = "output_topic";

        static AvroSchema avroSchema;

        static Path outputSchemaPath = Paths.get("src/main/avro/logistics_delivered.avsc");

        static void registerSchema() {
            Schema.Parser parser = new Schema.Parser();
            avroSchema = new AvroSchema("""
                    {
                      "namespace": "data.gen.avro",
                      "name": "withIntField",
                      "type": "record",
                      "fields": [ { "name": "intField", "type": { "type": "int" } } ]
                    }
                    """);
            // AvroSchema implements ParsedSchema
            SchemaRegistryClient client = MockSchemaRegistry.getClientForScope("test_schema_registry");
            try {
                client.register("test_schema_registry", avroSchema);
            } catch (IOException | RestClientException e) {
                System.out.println("Unable to register input schema " + e);
            }
        }

        @BeforeAll
        static void setup() {
            SetupProperties.setProperties(inputTopicName, outputTopicName, "intField", 10, 20);

            Properties config = Config.getConfig();
            Map<String, String> serdeConfig = Config.getSerdeConfig(config);

            registerSchema();

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
            inputValue.put("intField", 5);
            inputTopic.pipeInput("key", inputValue);

            KeyValue outputRecord = outputTopic.readKeyValue();

            assertNotNull(outputRecord);
            assertEquals(inputValue, outputRecord.value);
        }

        @Test
        @DisplayName("Test a message with value too big is copied")
        void testMessageWithValueTooBigIsCopied() {
            GenericRecord inputValue = new GenericData.Record(avroSchema.rawSchema());
            inputValue.put("intField", 25);
            inputTopic.pipeInput("key", inputValue);

            KeyValue outputRecord = outputTopic.readKeyValue();

            assertNotNull(outputRecord);
            assertEquals(inputValue, outputRecord.value);
        }

        @Test
        @DisplayName("Test a message with value in range is not copied")
        void testMessageWithValueInRangeIsNotCopied() {
            GenericRecord inputValue = new GenericData.Record(avroSchema.rawSchema());
            inputValue.put("intField", 15);
            inputTopic.pipeInput("key", inputValue);

            assertTrue(outputTopic.isEmpty(), "In-range message does not come through");
        }
    }
}