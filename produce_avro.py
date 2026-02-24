#!/usr/bin/env -S uv run --script
# /// script
# dependencies = ["avro", "httpx", "dotenv", "kafka-python"]
# ///
#
# As to that `script` block - see
# https://packaging.python.org/en/latest/specifications/inline-script-metadata/#inline-script-metadata
# for more information.
# The uv program understands it, and so `uv run --script` will run this file
# and create a virtual environment for it on the fly.

"""Generate fake Avro data

Install `uv` and then run as `./produce_avro.py`

Note that we are writing to the *input* topic for the Anomaly Detection app

The Avro writing code in this file is based on that from the Kafka Button App
at https://github.com/Aiven-Labs/kafka-button-app
"""

"""Generate fake data."""

import argparse
import datetime
import io
import json
import logging
import os
import pathlib
import random
import struct
import sys

import avro
import avro.io
import avro.schema
import dotenv
import httpx

from kafka import KafkaProducer

DEFAULT_TOPIC_NAME = 'metric_data'

logging.basicConfig(level=logging.INFO)
# kafka-python itself likes to provide informative INFO log messages,
# but I'd rather not have them
logging.getLogger('kafka').setLevel(logging.WARNING)

# Command line default values
DEFAULT_NUM_MESSAGES = 500
DEFAULT_CERTS_FOLDER = "certs"
KAFKA_SERVICE_URI = os.getenv("KAFKA_SERVICE_URI")
SCHEMA_REGISTRY_URL = os.getenv("SCHEMA_REGISTRY_URL")
# Because we're writing to the INPUT_TOPIC for the anomaly detector app,
# that's the environment value we look for (yes, that is a bit confusing)
TOPIC_NAME = os.getenv("INPUT_TOPIC", DEFAULT_TOPIC_NAME)
# Allow setting these defaults via a `.env` file as well
dotenv.load_dotenv()


# In "real life" we'd expect a sensor id to be more likely to be a UUID or something,
# but it's reasier to generate a random (small) set of integers, so we'll go with that.
# The field we're calling 'temperature' is actually going to mimic tenths of a degree Celcius,
# so 10C will be 100.
# If this was *good* fake data, we'd attempt to make temperature changes plausible - but that's
# harder to do, and we don't really care if we're just trying to detect anomalies...
AVRO_SCHEMA = {
    'doc': 'Sensor temperature values in 0.1 C',
    'name': TOPIC_NAME,
    'type': 'record',
    'fields': [
        {'name': 'sensor_id', 'type': 'long'},
        {'name': 'temperature', 'type': 'int'},
        {'name': 'timestamp', 'type': 'long', 'logicalType': 'timestamp-millis'},
    ],
}

### ALTERNATIVE IDEA:
### Generate N UUIDs to represent sensors.
### Keep a dictionary of UUID -> current temperature
### Generate a random (within some range) set of initial temperatures
### Either round-robin over the sensors, or choose a sensor at random, and
###    increment/decrement it's temperature by some amount (random within a "reasonable" range)
### Send a message for that new data, with the current time as its timestamp...

# When we're passing the Avro schema around, we need to pass it as a string
AVRO_SCHEMA_AS_STR = json.dumps(AVRO_SCHEMA)


def get_parsed_avro_schema(schema_as_str: str) -> avro.schema.RecordSchema:
    # Parsing the schema both validates it, and also puts it into a form that
    # can be used when envoding/decoding message data
    return avro.schema.parse(schema_as_str)


def register_avro_schema(schema_uri: str, topic_name: str, schema_as_str: str) -> int:
    """Register our schema with Karapace.

    Returns the schema id, which gets embedded into the messages.
    """
    logging.info(f"Registering schema {topic_name}-value")
    r = httpx.post(
        f"{schema_uri}/subjects/{topic_name}-value/versions",
        json={"schema": schema_as_str},
    )
    r.raise_for_status()

    logging.info(f"Registered schema, response is {r} {r.text=} {r.json()=}")
    response_json = r.json()
    return response_json["id"]


def make_avro_payload(
    message_data: dict,
    schema_id: int,
    parsed_schema: avro.schema.RecordSchema,
) -> bytes:
    """Given an appropriate dictionary of data, and a schema id, return an Avro payload.

    We assume the following:

    * Use of `io.aiven.connect.jdbc.JdbcSinkConnector`
      (https://github.com/aiven/jdbc-connector-for-apache-kafka/blob/master/docs/sink-connector.md)
      to consume data from Kafka and write it to PostgreSQL. This is an Apache 2 licensed fork of
      the Confluent `kafka-connect-jdbc` sink connector, from before it changed license
      (the Confluent connector is no longer Open Source).
    * Use of Karapace (https://www.karapace.io/) as our (open source) schema repository
    * [Apache Avro™](https://avro.apache.org/) to serialize the messages. To each message
      we'll also add the schema id.
    * The JDBC sink connector will then "unpick" the message using the
      `io.confluent.connect.avro.AvroConverter` connector
      (https://github.com/confluentinc/schema-registry/blob/master/avro-converter/src/main/java/io/confluent/connect/avro/AvroConverter.java)
      whose source code is Apache License, Version 2.0 licensed.
    """
    # The Avro encoder works by writing to a "file like" object,
    # so we shall use a BytesIO instance.
    writer = avro.io.DatumWriter(parsed_schema)
    byte_data = io.BytesIO()

    # The JDBC Connector needs us to put the schema id on the front of each Avro message.
    # We need to prepend a 0 byte and then the schema id as a 4 byte value.
    # We'll just do this by hand using the Python `struct` library.
    header = struct.pack(">bI", 0, schema_id)
    byte_data.write(header)

    # And then we add the actual data
    encoder = avro.io.BinaryEncoder(byte_data)
    writer.write(message_data, encoder)
    raw_bytes = byte_data.getvalue()

    return raw_bytes


def timestamp():
    """Unix timestamp in milliseconds"""
    now = datetime.datetime.now(datetime.timezone.utc)
    return int(now.timestamp() * 1000)


def new_message_data():
    """Produce new "fake" message data"""
    return {
        'sensor_id': random.randint(0, 100),     # Assuming 101 sensors
        'temperature': random.randint(-60, -20), # We "want" a temperature between -50 and -30
        'timestamp': timestamp(),
    }


def send_messages_to_kafka(
    kafka_uri: str,
    certs_dir: pathlib.Path,
    topic_name: str,
    schema_id: int,
    parsed_schema: avro.schema.RecordSchema,
    num_messages: int,
):
    producer = KafkaProducer(
        bootstrap_servers=kafka_uri,
        security_protocol="SSL",
        ssl_cafile=certs_dir / "ca.pem",
        ssl_certfile=certs_dir / "service.cert",
        ssl_keyfile=certs_dir / "service.key",
    )

    try:
        for count in range(num_messages):
            message_data = new_message_data()
            logging.info(f'Writing {message_data}')
            raw_bytes = make_avro_payload(message_data, schema_id, parsed_schema)
            producer.send(topic_name, raw_bytes)
    finally:
        producer.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        '-k', '--kafka-uri', default=KAFKA_SERVICE_URI,
        help='the URI for the Kafka service, defaulting to $KAFKA_SERVICE_URI'
        ' if that is set',
    )
    parser.add_argument(
        '-d', '--certs-dir', default=DEFAULT_CERTS_FOLDER, type=pathlib.Path,
        help='directory containing the ca.pem, service.cert and service.key'
             f' files, default "{DEFAULT_CERTS_FOLDER}"',
    )
    parser.add_argument(
        '-s', '--schema-registry-url', default=SCHEMA_REGISTRY_URL,
        help='the URL for the schema registry service, defaulting to $SCHEMA_REGISTRY_URL'
             ' if that is set. It is assumed to have the username and password'
             ' embedded in it',
    )
    parser.add_argument(
        '-t', '--topic-name', default=TOPIC_NAME,
        help=f'the topic to write messages to, default "{TOPIC_NAME}"',
    )
    parser.add_argument(
        '-n', '--num-messages', default=DEFAULT_NUM_MESSAGES, type=int,
        help=f'how many messages to send, default {DEFAULT_NUM_MESSAGES}',
    )
    parser.add_argument(
        '--forever', action='store_true',
        help='generate messages \'forever\''
        f' (actually equivalent to `--num-messages {sys.maxsize}`)'
    )

    args = parser.parse_args()

    if not args.kafka_uri:
        logging.error('The URI for the Kafka service is required')
        logging.error('Set KAFKA_SERVICE_URI or use the -k switch')
        return -1

    if not args.schema_registry_url:
        logging.error('The URL for the schema registry service is required')
        logging.error('Set SCHEMA_REGISTRY_URL or use the -s switch')
        return -1

    if args.num_messages <= 0:
        print(f'The `--num-messages` argument must be 1 or more, not {args.num_messages}')
        logging.error(f'The `--num-messages` argument must be 1 or more, not {args.num_messages}')
        return -1

    if args.forever:
        args.num_messages = sys.maxsize

    logging.debug('Reading messages')
    logging.debug(f'Kafka service URI {args.kafka_uri}')
    logging.debug(f'Certificates in {args.certs_dir}')
    logging.debug(f'Schema registry URL {args.schema_registry_url}')
    logging.debug(f'Writing to topic {args.topic_name}')

    schema_id = register_avro_schema(args.schema_registry_url, args.topic_name, AVRO_SCHEMA_AS_STR)
    parsed_schema = get_parsed_avro_schema(AVRO_SCHEMA_AS_STR)

    send_messages_to_kafka(
        args.kafka_uri, args.certs_dir, args.topic_name,
        schema_id, parsed_schema,
        args.num_messages,
    )


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print()
