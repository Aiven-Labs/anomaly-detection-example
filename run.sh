#!/bin/sh

# We're going to need the following environment variables as input
#
# - KAFKA_SERVICE_URI - the URI of the Kafka service we're using
# - CA_PEM_CONTENTS - the contents of the ca.pem file
# - SERVICE_CERT_CONTENTS - the contents of the service.cert file
# - SERVICE_KEY_CONTENTS - the contents of the service.key file
# - SCHEMA_REGISTRY_URL - the URL for the Karapace schema
# - SCHEMA_REGISTRY_PASSWORD - the password for the schema registry
#
# If you give a value for SCHEMA_REGISTRY_USERNAME we'll use it, otherwise
# we'll use the default value, which is "avnadmin"
export SCHEMA_REGISTRY_USERNAME=${SCHEMA_REGISTRY_USERNAME:-"avnadmin"}
#
# You can also give values for INPUT_TOPIC and OUTPUT_TOPIC, but if
# they're unset, then the Java application will use an appropriate
# default
export INPUT_TOPIC=${INPUT_TOPIC:-""}
export OUTPUT_TOPIC=${OUTPUT_TOPIC:-""}
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
    -DKAFKA_SERVICE_URI="$KAFKA_SERVICE_URI"                   \
    -DCA_PEM_CONTENTS="$CA_PEM_CONTENTS"		       \
    -DSERVICE_CERT_CONTENTS="$SERVICE_CERT_CONTENTS"           \
    -DSERVICE_KEY_CONTENTS="$SERVICE_KEY_CONTENTS"             \
    -DSCHEMA_REGISTRY_URL="$SCHEMA_REGISTRY_URL"               \
    -DSCHEMA_REGISTRY_USERNAME="$SCHEMA_REGISTRY_USERNAME"     \
    -DSCHEMA_REGISTRY_PASSWORD="$SCHEMA_REGISTRY_PASSWORD"     \
    -DINPUT_TOPIC="$INPUT_TOPIC"                               \
    -DOUTPUT_TOPIC="$OUTPUT_TOPIC"                             \
    -DEXACTLY_ONCE="$EXACTLY_ONCE"                             \
    -jar ./${APP_NAME}-uber.jar \
    com.example.${APP_NAME} "$@"
