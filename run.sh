#!/bin/sh

# We're going to need the following environment variables as input
#
# - KAFKA_BOOTSTRAP_SERVERS - the URL of the Kafka service we're using
# - KAFKA_CA_CERT - the contents of the ca.pem file
# - KAFKA_ACCESS_CERT - the contents of the service.cert file
# - KAFKA_ACCESS_KEY - the contents of the service.key file
# - SCHEMA_REGISTRY_URL - the URL for the Karapace schema
# - SCHEMA_REGISTRY_PASSWORD - the password for the schema registry
#
# We also need the names of the input and output topics
# - INPUT_TOPIC - the input topic name
# - OUTPUT_TOPIC - the output topic name
#
# We also need the details of the anomaly we're trying to detect
# - FIELD_NAME is the name of the field in the Avro message
# - MIN_BOUND is the integer value that is the lowest "OK" value
# - MAX_BOUND is the integer value that is the highest "OK" value
#
# If you give a value for SCHEMA_REGISTRY_USERNAME we'll use it, otherwise
# we'll use the default value, which is "avnadmin"
export SCHEMA_REGISTRY_USERNAME=${SCHEMA_REGISTRY_USERNAME:-"avnadmin"}
#
# You can also request exactly once semantics by specifying true.
# The case of the value does not matter. The default is false.
export EXACTLY_ONCE=${EXACTLY_ONCE:-"false"}
#
# We need the name of the app (its class name), but we've got a default
export APP_NAME=${APP_NAME:-"AnomalyDetectorApp"}

echo "APP_NAME is $APP_NAME"

. ./setup_auth.sh

echo "RUN THE PROGRAM"
exec java \
    -cp '$JAVA_HOME/lib/*' \
    -DKAFKA_BOOTSTRAP_SERVERS="$KAFKA_BOOTSTRAP_SERVERS"                   \
    -DKAFKA_CA_CERT="$KAFKA_CA_CERT"		       \
    -DKAFKA_ACCESS_CERT="$KAFKA_ACCESS_CERT"           \
    -DKAFKA_ACCESS_KEY="$KAFKA_ACCESS_KEY"             \
    -DSCHEMA_REGISTRY_URL="$SCHEMA_REGISTRY_URL"               \
    -DSCHEMA_REGISTRY_USERNAME="$SCHEMA_REGISTRY_USERNAME"     \
    -DSCHEMA_REGISTRY_PASSWORD="$SCHEMA_REGISTRY_PASSWORD"     \
    -DINPUT_TOPIC="$INPUT_TOPIC"                               \
    -DOUTPUT_TOPIC="$OUTPUT_TOPIC"                             \
    -DFIELD_NAME="$FIELD_NAME"				       \
    -DMIN_BOUND="$MIN_BOUND"				       \
    -DMAX_BOUND="$MAX_BOUND"				       \
    -DEXACTLY_ONCE="$EXACTLY_ONCE"                             \
    -jar ./${APP_NAME}-uber.jar \
    com.example.${APP_NAME} "$@"
