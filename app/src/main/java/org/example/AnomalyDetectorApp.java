package org.example;

import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.LogicalType;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * This app is a very minimalist example of using Kafka Streams to detect anomalous messages.
 *
 * - Messages are Avro, as produced by the Aiven for Apache Kafka sample stream generator for ???
 */
public class AnomalyDetectorApp {
    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectorApp.class);

    /** Is the name field in this value outside the bounds given?
     *
     * For the moment, the min and max bounds are always int values, but the field can be an int or a long
     *
     * We also decide that if the field is `null` then it counts as out of bounds.
     */
    private static boolean outsideBounds(GenericRecord value, String fieldName, int minBound, int maxBound) {

        // USING INTROSPECTION TO SHOW THE TYPE OF A FIELD
        Schema  schema = value.getSchema();
        Schema.Field  field = schema.getField(fieldName);
        Schema fieldSchema = field.schema();
        Schema.Type fieldType = fieldSchema.getType();
        LogicalType fieldLogicalType = fieldSchema.getLogicalType();
        log.info("Field {} is type {}, logical type {}", fieldName, fieldType, fieldLogicalType);
        // DONE

        var val = value.get(fieldName);
        if (val == null) {
            log.info("The value for field {} is null, so is not comparable", fieldName);
            return true;
        }

        if (fieldType == Schema.Type.INT) {
            if (val instanceof Integer num) {
                if (num < minBound) {
                    log.info("Field {} value {} < {}", fieldName, num, minBound);
                    return true;
                } else if (num > maxBound) {
                    log.info("Field {} value {} > {}", fieldName, num, maxBound);
                    return true;
                }
            } else {
                log.info("The value for field {} is not an Integer, so is not comparable", fieldName);
                return true;
            }
        } else if (fieldType == Schema.Type.LONG){
            if (val instanceof Long num) {
                if (num < Long.valueOf(minBound)) {
                    log.info("Field {} value {} < {}", fieldName, num, minBound);
                    return true;
                } else if (num > Long.valueOf(maxBound)) {
                    log.info("Field {} value {} > {}", fieldName, num, maxBound);
                    return true;
                }
            } else {
                log.info("The value for field {} is not a Long, so is not comparable", fieldName);
                return true;
            }
        } else {
            log.info("Field {} is not an Avro INT or LONG, so is not comparable", fieldName);
            return true;
        }
        return false;
    }

    public static Topology buildTopology(Properties config, Map<String, String> serdeConfig)
    {
        // We're using a generic Serde for the input message values, and the library code
        // will download the schema from the schema repository at runtime, using the schema ID
        // at the start of each (Confluent-style) Avro message.
        // NOTE that I'm assuming that this is cached, and it doesn't retrieve the schema for
        // each and every message.
        // Since we're copying (anomalous) messages, we can use the same Serde to write to the output topic.
        final Serde<GenericRecord> avroMessageSerde = new GenericAvroSerde();
        avroMessageSerde.configure(serdeConfig, false); // `false` means it's a value Serde

        final StreamsBuilder builder = new StreamsBuilder();

        // Our source stream is read from the input topic using the (input) generic Avro serde
        KStream<String, GenericRecord> sourceStream = builder.stream(
                config.get("input.topic.name").toString(),
                Consumed.with(Serdes.String(), avroMessageSerde)
        );

        // Read from the input stream, and pass on any messages where the specified value is outside
        // the nominated range
        // Use `peek` to output log messages at various points.
        //
        // 1. I don't really need to put the type information into each lambda, but I feel it makes it more obvious
        //    what the control flow is.
        // 2. In a real production app, we don't need the `peek` calls - but in an example and during development
        //    they're quite nice for explicitly logging what is going on
        sourceStream
                .peek( (String key, GenericRecord inputValue) -> log.info("LOOKING AT: Value='{}'", inputValue) )
                .filter( (String key, GenericRecord inputValue) -> outsideBounds(inputValue,
                        config.get("field.name").toString(), (int) config.get("min.bound"), (int) config.get("max.bound")) )
                .peek( ( String key, GenericRecord inputValue) -> log.info("OUT OF BOUNDS: Value='{}' not {} <= {} <= {}",
                        inputValue, config.get("min.bound"), config.get("field.name"), config.get("max.bound")) )
                .to(
                        config.get("output.topic.name").toString(),
                        Produced.with(Serdes.String(), avroMessageSerde)
                );

        // Create and return the Topology for that transformation
        return builder.build();
    }

    public static void main(String[] args) {
        Properties config = Config.getConfig();
        final Map<String, String> serdeConfig = Config.getSerdeConfig(config);

        Topology topology = buildTopology(config, serdeConfig);
        System.out.println(topology.describe());

        final KafkaStreams streams = new KafkaStreams(topology, config);

        // Add a shutdown hook to close the Streams application gracefully
        final CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {
            streams.cleanUp();  // Clean up local state stores (useful for development/testing)
            streams.start();
            latch.await();
        } catch (Throwable e) {
            log.error("Error starting or running Kafka Streams application", e);
            System.exit(1);
        }
        log.info("Kafka Streams Application Shut Down.");
        System.exit(0);
    }
}
