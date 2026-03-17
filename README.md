# Anomaly detection MVP / PoC using Apache Kafka® Streams

## What is here

This is a very simple PoC (Proof of Concept) of anomaly detection using 
Apache Kafka® Streams.

The aims are:

* Be minimalistic - don't try for anything other than a minimal concept of 
  "anomaly". Real anomaly detection can be quite complex, including use of 
  windowing techniques, cross comparison between different fields, and many 
  other things which are determined by the particular requirements.
* Use Apache Kafka Streams® to read messages from one topic, and write 
  anomalous messages to another.
* Message values are encoded using Avro and the Confluent convention on 
  binding a schema id to the start of each message
  (see the Confluent [Wire format](https://docs.confluent.io/platform/current/schema-registry/fundamentals/serdes-develop/index.html#wire-format)
  documentation for details of how this works).
* A compatible schema registry is assumed.
* A single field in the message value (specified by 
  name) is compared to maximum and minimum bounds.
* Only `int` (32 bit signed) and `long` (64 bit signed) fields are
  checkable, and the bounds are specified as `int`s.
* Messages where the value is out of range (less than the lower
  bound or more than the higher bound) will be sent to the output topic.
* Messages where the field is missing will be regarded as anomalies, and 
  sent to the output topic.
* The application is designed to be run in a container. A `Dockerfile` and
  associated run scripts (`run.sh` and `setup_auth`) are provided.
* A script is provided to generate sample data.
* Instructions are given for using the app with an Aiven for Kafka 
  service and the Karapace schema registry.

The main code is [AnomalyDetectorApp.java]
(app/src/main/java/org/example/AnomalyDetectorApp.java)

The project uses Gradle and Groovy for configuration and building.

## Running the container

Download the URL and the certificates for the Kafka service.

Set an environment variable for the Kafka service URL - something like
```shell
export KAFKA_BOOTSTRAP_SERVERS=<service uri>
```
or in the Fish shell
```shell
set -x KAFKA_BOOTSTRAP_SERVERS <service uri>
```

Do the same for the schema registry URL and password (the program
defaults to the standard schema Karapace username of `avnadmin`, so we don't
need to specify that).
```shell
export SCHEMA_REGISTRY_URL=<schema registry url>
```
```shell
export SCHEMA_REGISTRY_PASSWORD=<schema registry password>
```
(the Fish shell equivalents are left as an exercise for Fish shell users:).)

Set an environment variable to the content of each certificate file.

> Typically,
> 1. Download the certificate files for the Kafka service (`ca.pem`,
>    `service. cert` and `service.key`).
>    For an Aiven for Kafka service you can do this from the **Connection
>    information** in the service Overview.
> 2. Put the files into a directory called `certs` and use one of the
>    convenience shell scripts to read the content of
>    those files and set the environment variables:
>    ```shell
>    source prep.sh
>    ```
>    or for Fish
>    ```shell
>    source prep.fish
>    ```

Build the container image:

```shell
docker build -t appimage .
```


Run the container image - for instance
```shell
docker run -d --name kafka-streams-container -p 3000:3000 \
              -e KAFKA_BOOTSTRAP_SERVERS=$KAFKA_BOOTSTRAP_SERVERS \
              -e KAFKA_CA_CERT="$KAFKA_CA_CERT" \
              -e KAFKA_ACCESS_CERT="$KAFKA_ACCESS_CERT" \
              -e KAFKA_ACCESS_KEY="$KAFKA_ACCESS_KEY" \
              -e SCHEMA_REGISTRY_URL=$SCHEMA_REGISTRY_URL \
              -e SCHEMA_REGISTRY_USERNAME=$SCHEMA_REGISTRY_USERNAME \
              -e SCHEMA_REGISTRY_PASSWORD=$SCHEMA_REGISTRY_PASSWORD \
              -e INPUT_TOPIC=metric_data \
              -e OUTPUT_TOPIC=anomaly_daya \
              -e FIELD_NAME=temperature \
              -e MIN_BOUND=-50 \
              -e MAX_BOUND=-30 \
              -e EXACTLY_ONCE=false \
              appimage
        appimage
```

...or if you have environment variables for the topics names, the field name 
and the bounds:
```shell
docker run -d --name kafka-streams-container -p 3000:3000 \
              -e KAFKA_BOOTSTRAP_SERVERS=$KAFKA_BOOTSTRAP_SERVERS \
              -e KAFKA_CA_CERT="$KAFKA_CA_CERT" \
              -e KAFKA_ACCESS_CERT="$KAFKA_ACCESS_CERT" \
              -e KAFKA_ACCESS_KEY="$KAFKA_ACCESS_KEY" \
              -e SCHEMA_REGISTRY_URL=$SCHEMA_REGISTRY_URL \
              -e SCHEMA_REGISTRY_USERNAME=$SCHEMA_REGISTRY_USERNAME \
              -e SCHEMA_REGISTRY_PASSWORD=$SCHEMA_REGISTRY_PASSWORD \
              -e INPUT_TOPIC=$INPUT_TOPIC \
              -e OUTPUT_TOPIC=$OUTPUT_TOPIC \
              -e FIELD_NAME=$FIELD_NAME \
              -e MIN_BOUND=$MIN_BOUND \
              -e MAX_BOUND=-$MAX_BOUND \
              -e EXACTLY_ONCE=false \
              appimage
```

We don't actually use the port for anything at the moment.

Some of those environment variable arguments have defaults, so you can 
leave them off if you're happy with the default:
* `SCHEMA_REGISTRY_USERNAME` - both `run.sh` and the Java code default this to 
  `avnadmin`
* `EXACTLY_ONCE` - `run.sh` defaults this to `false`.

## Command line arguments for the Java app

All variants of the Java app take the following arguments (of course 
`OUTPUT_TOPIC` is not used by the `Log` app). Common code to handle these is in
[Config.java](app/src/main/java/org/example/Config.java). The names chosen 
match the environment variables used by the container file and `run.sh`.

* `-DKAFKA_BOOTSTRAP_SERVERS` - the URL for the Kafka service.
* `-DKAFKA_CA_CERT` - the contents of the `ca.pem` file
* `-DKAFKA_ACCESS_CERT` - the contents of the `service.cert` file
* `-DKAFKA_ACCESS_KEY` - the contents of the `service.key` file
* `-DSCHEMA_REGISTRY_URL` - the URL for the schema registry.
* `-DSCHEMA_REGISTRY_USERNAME` - the user name for accessing the schema
  registry. This defaults to `avnadmin`, which is the default user name for
  Karapace.
* `-DSCHEMA_REGISTRY_PASSWORD` - the password for accessing the schema registry
* `-DINPUT_TOPIC` - the input topic name.
* `-DOUTPUT_TOPIC` - the output topic name, for anomalous messages.
* `-DFIELD_NAME` - the name of the field to check in the message value.
* `-DMIN_BOUND` - the minimum `int` value for that field - any messages with 
  a value below this are anomalous.
* `-DMAX_BOUND` - the maximum `int` value for that field - any messages with
  a value above this are anomalous.
* `-DEXACTLY_ONCE` - request exactly once semantics. A value of `true`
  requests exactly once semantics, a value of `false`, an empty string or the
  absence of this property does not. The value is case insensitive. Any other
  value is an error.

It is arguable that the topic names, field names, bounds and exactly once 
flag should be "proper" command line (switch) arguments, instead of being 
set by environment variables, but since this app is only intended to be run 
via a container file, the distinction doesn't seem worth pursuing.

## The container file and how it works

This is a two stage container file.

The `APP_NAME` variable determines which app is being built and run. It 
is set to `AnomalyDetectorApp`. Using this saves repeating the app name 
throughout the container file.

The first stage builds a fat (uber) JAR for the program. This minimises the
size of the executable to be passed to the second stage.

It uses `jdeps` and `jlink` to work out the dependencies that are not in the
JAR file, and extract a minimum JRE from the larger JRE in provided by the
operating system used in that first stage.

The second stage then downloads `rocksdb` (used by Kafka Streams).

It then copies over the minimal JRE prepared in the first stage, and the fat
JAR itself, as well as the `run.sh` and `setup_auth.sh` files, and
finally runs the `run.sh` script.

## The `run.sh` and `setup_auth.sh` files

The `run.sh` file expects the following environment variables as input 
you'll recognise all but `APP_NAME` from the instructions on running the 
container and the Java app itself):

- `KAFKA_BOOTSTRAP_SERVERS` - the URL of the Kafka service we're using
- `KAFKA_CA_CERT` - the contents of the `ca.pem` file
- `KAFKA_ACCESS_CERT` - the contents of the `service.cert` file
- `KAFKA_ACCESS_KEY` - the contents of the `service.key` file
- `SCHEMA_REGISTRY_URL` - the URL for the schema registry
- `SCHEMA_REGISTRY_USERNAME` - the user name for accessing the schema
  registry. **This is optional** and if it is not given, a value of `avnadmin`
  will be assumed
- `SCHEMA_REGISTRY_PASSWORD` - the password for accessing the schema registry
- `INPUT_TOPIC` - the input topic name.
  has a sensible default, `metric_data`.
- `OUTPUT_TOPIC` - the output topic name, for anomalous messages.
* `FIELD_NAME` - the name of the field to check in the message value.
* `MIN_BOUND` - the minimum `int` value for that field - any messages with
  a value below this are anomalous.
* `MAX_BOUND` - the maximum `int` value for that field - any messages with
  a value above this are anomalous.
- `EXACTLY_ONCE` - whether exactly once semantics is wanted. **This is 
  optional** and if it is not given, defaults to `false`. Request `true` if
  you want exactly once semantics.
- `APP_NAME` - the name of the application to run. This will be set to
  `AnomalyDetectorApp` by the container file.

It sources the `setup_auth.sh` script which makes sure that the
`KAFKA_CA_CERT`, `KAFKA_ACCESS_CERT` and `KAFKA_ACCESS_KEY`
environment variables contain data that is correctly split into lines.

Finally the `run.sh` script  runs the fat Java JAR with the necessary
arguments.

## Building the program

We use a fat (uber) JAR in the container, so that all of the programs
non-standard dependencies (the ones not provided by the JRE) are frozen into the
final executable.

You can build that fat JAR file with
```shell
gradle AnomalyDetectorAppUberJar
```

(See `app/build.gradle` for the definition of the `UberJar` task.)

If you want to run the app using the provided `run.sh` script, then you'll
also need to copy the result to the top-level directory
```shell
cp app/build/libs/AnomalyDetectorApp-uber.jar .
```

### Exactly once semantics

With a normal Kafka Streams application, it is possible that a message might 
be processed once, more than once, or never at all (networks are unreliable, 
services can crash, and so on).

Exactly once semantics (EOS) in Kafka Streams guarantees that each message 
will be processed once, no more and no less. It's been available since 2017.

There is quite a lot of good documentation about exactly once semantics for 
Kafka Streams - the following is by no means an exhaustive list.

See Confluent's
[Exactly-Once Semantics Are Possible: Here’s How Kafka Does It](https://www.confluent.io/blog/exactly-once-semantics-are-possible-heres-how-apache-kafka-does-it/)
(2017/2025) for a good introduction to how this works.

The Kafka Streams
[Core Concepts](https://kafka.apache.org/41/streams/core-concepts/)
document (this link is for Apache Kafka 4.1.x which
is current at time of writing) is also pretty good. It talks about
exactly once in the
[Processing Guarantees](https://kafka.apache.org/41/streams/core-concepts/#processing-guarantees)
section.

Zeinab Dashti's
[Developer Guide to Achieve Transactional Processing in Kafka Streams](https://medium.com/@zdb.dashti/developer-guide-to-achieve-transactional-processing-in-kafka-streams-3178e642a570)
(2025) is good at the pragmatics, and notes that:

> When `processing.guarantee=exactly_once_v2` is set, Kafka Streams 
> automatically enforces the required producer and consumer configurations:
> ```
> enable.idempotence=true (on the Kafka producer)
> isolation.level=read_committed (on the Kafka consumer)
> ```
> You don’t need to set these manually — doing so isn’t harmful if you match 
> the required values, but Kafka Streams will log a warning or ignore conflicting settings.

and

> Any external consumer reading from Kafka output topics that are written 
> transactionally must configure:
> ```
> isolation.level=read_committed
> ```
> Kafka Streams enforces this by default within its topology, but it must be
> set explicitly for standalone consumers (e.g., Kafka Connect, other
> microservices). Without it, consumers could read uncommitted or aborted records,
> which may result in data duplication or inconsistency.


### Running the unit tests

There are minimal unit tests for the application.

Run them with, for instance:
```shell
gradle clean cleanTest test
```

## Running `run.sh` locally

It's possible to run the `run.sh` script locally, and indeed this is useful
for testing. It's important to remember to

1. Copy the built app into the same directory as the `run.sh` script
2. Set the required various environment variables first - these are also 
   documented at the top of the `run.sh` file.

For instance
```shell
./run.sh
```
or, to override the `OUTPUT_TOPIC` environment variable for this run
```shell
OUTPUT_TOPIC=bad_messages ./run.sh
```

<!--
## Visualising the messages

In the `reporting` directory there is a command line program
`report_messages.py` which reads messages from both the input and output topics
and shows them using a text UI.

If all the environment variables discussed before are set up, then you can 
run it with
```shell
reporting/report_messages.py
```

Get help on what it does with
```shell
reporting/report_messages.py -h
```

In that same directory there is an experimental wrapper (`serve.py`) which 
allows it to be run as a web app in a Docker container.

For instance:
```shell
cd reporting
```
```shell
docker build -t report_image .
```

```shell
docker run -d --name report-messages-container -p 3000:3000 \
        -e KAFKA_BOOTSTRAP_SERVERS=$KAFKA_BOOTSTRAP_SERVERS \
        -e KAFKA_CA_CERT=$KAFKA_CA_CERT \
        -e KAFKA_ACCESS_CERT=$KAFKA_ACCESS_CERT \
        -e KAFKA_ACCESS_KEY=$KAFKA_ACCESS_KEY \
        -e SCHEMA_REGISTRY_URL=$SCHEMA_REGISTRY_URL \
        report_image
```

It deliberately uses the same environment variables as are needed to run the 
actual application.

> **Note** It assumes that the `$SCHEMA_REGISTRY_URL` includes the username
> and password in the URL.

-->

## Generating sample data

The program `produce_avro.py` can be used to write sample data to Kafka.

It expects [uv](https://docs.astral.sh/uv/) to be installed.

Assuming the environment variables needed to run the container file / `run.
sh` / Java application are all set up, and the certification files are in 
the `certs` directory, you can generate 500 sample messages with
```shell
./produce_avro.py
```

To find out more about how the program can be used, try
```shell
./produce_avro.py --help
```

## End to end example using Aiven for Kafka

> **Note** For trying out this Kafka Streams app, a
> [free Aiven for Kafka service](https://aiven.io/free-kafka)
> will work just fine. The instructions below show how to use that, as well 
> as how to use a paid service if that's more suitable.

It's possible to do everything in this section using the Aiven [web
console](https://console.aiven.io/), but for documentation purposes here I
shall use the `avn` command line tool.

Since `avn` is a Python tool, make sure you're in a virtual environment and
download it:

```shell
python -m venv venv
source venv/bin/activate    # If you're using fish, activate.fish
pip install aiven-client
```

Retrieve an Aiven session token (see [the
documentation](https://aiven.io/docs/platform/howto/create_authentication_token))
and login, using the email address you logged in to the console with, and
pasting the token when prompted:
```shell
avn user login <your-email> --token
```

For convenience, set the project to your current project - this means you
don't have to specify it on every command:

```shell
avn project switch <project-name>
```

Set an environment variable for the service name - perhaps something like "kafka-streams-example"
```shell
export KAFKA_SERVICE_NAME=<service name>
```
or for Fish shell
```shell
set -x KAFKA_SERVICE_NAME <service name>
```

Create the Aiven for Kafka service. We'll show how to create a free or paid 
service. There are notes about each command after the command.

1. For trying out this app, a
   [free Aiven for Kafka service](https://aiven.io/free-kafka)
   will work just fine. Create the service using the following command:
   ```shell
   avn service create $KAFKA_SERVICE_NAME          \
           --service-type kafka                    \
           --cloud do-ams                          \
           --plan free-0                           \
           -c schema_registry=true                 \
           -c kafka.auto_create_topics_enable=true
   ```

   > **Notes**
   > 1. The details of how the free cloud and plan are specified at the command 
   >    line may change. This is one case where it's actually simpler to do this
   >    in the Aiven web console, as there you just choose the free 
   >    Kafka tier and then what part of the world you want.
   > 2. `-c schema_registry=true` says we want to enable the Karapace schema
   >    registry. This is also free, and we need it to handle Avro messages.
   > 3. `-c kafka.auto_create_topics_enable=true` says we want producers to
   >    be able to create topics. You don't want this in production, but it's
   >    often a good idea in development, and it means the output topics will
   >    get created as we need them.

2. If you prefer (or if you're already using your free Aiven for Kafka service 
   for something else and don't want to add new topics to it), you can instead 
   create a paid service. For that, use a command like the following:
   ```shell
   avn service create $KAFKA_SERVICE_NAME          \
           --service-type kafka                    \
           --cloud aws-eu-west-1                   \
           --plan startup-4                        \
           --no-project-vpc                        \
           -c schema_registry=true                 \
           -c kafka.auto_create_topics_enable=true
   ```

   > **Notes**
   > 1. Choose a cloud and plan that match your needs. There's no need to go 
   >    for anything above the minimum plan (`startup-4` in this case).
   > 2. In the case of this cloud and region, I knew there was a VPC 
   >    (virtual private cloud) available to my organization, so I needed
   >    to tell the command I did not want to use it. It doesn't hurt to
   >    specify th
   > 3. The last two switches are the same as in the free example above.

While that's running, get the service URL for the new service
``` shell
export KAFKA_BOOTSTRAP_SERVERS=$(avn service get $KAFKA_SERVICE_NAME --format '{service_uri}')
```
or for Fish shell
```shell
set -x KAFKA_BOOTSTRAP_SERVERS (avn service get $KAFKA_SERVICE_NAME --format '{service_uri}')
```

Get the schema registry (Karapace) URL
```shell
export SCHEMA_REGISTRY_URL=$(avn service get $KAFKA_SERVICE_NAME --json | jq -r '.connection_info.schema_registry_uri')
```
or for Fish shell
```shell
set -x SCHEMA_REGISTRY_URL (avn service get $KAFKA_SERVICE_NAME --json | jq -r '.connection_info.schema_registry_uri')
```

Get the schema registry password
```shell
export SCHEMA_REGISTRY_PASSWORD=$(avn service get $KAFKA_SERVICE_NAME --json | jq -r '.users[0].password')
```
or for Fish shell
```shell
set -x SCHEMA_REGISTRY_PASSWORD (avn service get $KAFKA_SERVICE_NAME --json | jq -r '.users[0].password')
```

We assume the default username for the schema registry, so don't need to
look that up, but if you do need it then you can get it with
```shell
export SCHEMA_REGISTRY_USERNAME=$(avn service get $KAFKA_SERVICE_NAME --json | jq -r '.users[0].username')
```
or for Fish shell
```shell
set -x SCHEMA_REGISTRY_USERNAME (avn service get $KAFKA_SERVICE_NAME --json | jq -r '.users[0].username')
```

Wait for it to reach Running state
``` shell
avn service wait $KAFKA_SERVICE_NAME
```

Once the Kafka service is running, you can create the two topics.

> **Note** In fact, the `-c kafka.auto_create_topics_enable=true` specified 
> when creating the service means the topics will get created when first 
> written to, so this is not necessarily required.
>
> The `produce_avro.py` script assumes that the "input" topic is called 
>`metric_data`.

```shell
avn service topic-create    \
    --partitions 1          \
    --replication 2         \
    $KAFKA_SERVICE_NAME metric_data
```
```shell
avn service topic-create    \
    --partitions 1          \
    --replication 2         \
    $KAFKA_SERVICE_NAME anomaly_data
```

Download the certification files (it will create the directory if necessary)
``` shell
avn service user-creds-download $KAFKA_SERVICE_NAME --username avnadmin -d certs
```
``` shell
ls certs
```
should report
```
ca.pem  service.cert  service.key
```

Set the environment variables for the certificate file contents
```shell
source prep.sh
```
or for Fish shell
```shell
source prep.fish
```

And now you're ready to run the program, either via `./run.sh`
or via Docker.
