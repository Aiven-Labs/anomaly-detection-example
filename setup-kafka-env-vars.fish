# Source this file to set the environment variables we need, and also to
# download the certs files
#
# If the `-c` switch is given, it will also *create* the Kafka service.
#
# You need to have logged in using `avn login`, and to have set the project
# using `avn project switch`,  before running this script.
#
# As input it takes the Kofka service name

# Hmm - there doesn't seem to be an *easy* way to work this out
set PROG_NAME setup-kafka-env-vars.fish

# Make a minimal test that the user has rememebered to 'source' us
# (not perfect, but probably good enough)
if test "$_" != "source" -a "$_" != "."
    echo "Please use 'source' to run this script"
    echo "   source $PROG_NAME [--create] <KAFKA_SERVICE_NAME>"
    return
end

argparse --min-args 1 --max-args 2 'h/help' 'c/create' -- $argv

if set -ql _flag_h
    echo "Usage: $PROG_NAME [-h | --help] [-c | --create] <KAFKA_SERVICE_NAME>" >&2
    return 1
end

if test (count $argv) -ne 1
    echo "Please give the name of the Kafka service"
    return 1
end

if ! command -q avn
    echo "Please install aiven-client (the 'avn' command)"
    return 1
end

set -x KAFKA_SERVICE_NAME $argv[1]
echo "Kafka service name: $KAFKA_SERVICE_NAME"

set -x INPUT_TOPIC metric_data
set -x OUTPUT_TOPIC anomaly_data

echo "Using topics INPUT_TOPIC = $INPUT_TOPIC_NAME and OUTPUT_TOPIC = $ANOMALY_TOPIC_NAME"

if set -ql _flag_c
    # Since we can't tell if the user already *has* a free Kafka service
    # running, we'll create a non-free service
    echo "Creating service $KAFKA_SERVICE_NAME"
    avn service create $KAFKA_SERVICE_NAME      \
    	--service-type kafka                    \
    	--cloud aws-eu-west-1                   \
    	--plan startup-4                        \
    	--no-project-vpc                        \
    	-c schema_registry=true                 \
    	-c kafka.auto_create_topics_enable=true
    
    echo "Waiting for service $KAFKA_SERVICE_NAME to start"
    avn service wait $KAFKA_SERVICE_NAME
    
    echo "Creating topic $INPUT_TOPIC"
    avn service topic-create    \
        --partitions 1          \
        --replication 2         \
        $KAFKA_SERVICE_NAME $INPUT_TOPIC
    
    echo "Creating topic $OUTPUT_TOPIC"
    avn service topic-create    \
        --partitions 1          \
        --replication 2         \
        $KAFKA_SERVICE_NAME $OUTPUT_TOPIC
end

echo "Getting Kafka and Karapace details"
set -x KAFKA_SERVICE_URL (avn service get $KAFKA_SERVICE_NAME --format '{service_uri}')
set -x SCHEMA_REGISTRY_URL (avn service get $KAFKA_SERVICE_NAME --json | jq -r '.connection_info.schema_registry_uri')
set -x SCHEMA_REGISTRY_PASSWORD (avn service get $KAFKA_SERVICE_NAME --json | jq -r '.users[0].password')
set -x SCHEMA_REGISTRY_USERNAME (avn service get $KAFKA_SERVICE_NAME --json | jq -r '.users[0].username')

echo "Downloading the credentials"
# Get the credentials into cred/, and then set up environment variables for
# their contents
avn service user-creds-download $KAFKA_SERVICE_NAME --username avnadmin -d certs

echo "And setting the credential environment variables"
# See https://github.com/fish-shell/fish-shell/issues/7323 for the `string collect` hint
set -x CA_PEM_CONTENTS (cat certs/ca.pem | string collect)
set -x SERVICE_CERT_CONTENTS (cat certs/service.cert | string collect)
set -x SERVICE_KEY_CONTENTS (cat certs/service.key | string collect)
