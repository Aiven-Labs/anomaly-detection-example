package org.example;

public class SetupProperties {
    /**
     * Set some standard test values as system properties
     */
    static void setProperties(String inputTopicName, String outputTopicName,
                              String fieldName, int minBound, int maxBound) {
        System.setProperty("KAFKA_SERVICE_URL", "dummy:1234");
        System.setProperty("CA_PEM_CONTENTS", "not used");
        System.setProperty("SERVICE_CERT_CONTENTS", "not used");
        System.setProperty("SERVICE_KEY_CONTENTS", "not used");
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
