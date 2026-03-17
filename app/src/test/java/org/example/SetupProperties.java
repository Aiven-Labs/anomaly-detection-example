package org.example;

public class SetupProperties {
    /**
     * Set some standard test values as system properties
     */
    static void setProperties(String inputTopicName, String outputTopicName,
                              String fieldName, int minBound, int maxBound) {
        System.setProperty("KAFKA_BOOTSTRAP_SERVERS", "dummy:1234");
        System.setProperty("KAFKA_CA_CERT", "not used");
        System.setProperty("KAFKA_ACCESS_CERT", "not used");
        System.setProperty("KAFKA_ACCESS_KEY", "not used");
        System.setProperty("PASSWORD_FOR_STORE", "not used");
        System.setProperty("SCHEMA_REGISTRY_URL", "mock://test_schema_registry");
        System.setProperty("SCHEMA_REGISTRY_PASSWORD", "not used");
        System.setProperty("SCHEMA_REGISTRY_USERNAME", "not used");
        System.setProperty("INPUT_TOPIC", inputTopicName);
        System.setProperty("OUTPUT_TOPIC", outputTopicName);
        System.setProperty("FIELD_NAME", fieldName);
        System.setProperty("MIN_BOUND", String.valueOf(minBound));
        System.setProperty("MAX_BOUND", String.valueOf(maxBound));
    }
}
