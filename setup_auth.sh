#!/bin/sh

# We're not sure if the certificat environment variables will have newlines
# seperating lines (which is what we assume the user will have given us) or
# if the newlines will have been replaced with spaces. So let's normalise the
# strings so we can cope with either.
# We take care not so "normalise" the spaces in the first and last lines :)
#
# Remember to `source` this, as the we're mutating environment values
# that the caller will want to use.

# We're going to need the following environment variables as input
#
# - KAFKA_CA_CERT - the contents of the ca.pem file
# - KAFKA_ACCESS_CERT - the contents of the service.cert file
# - KAFKA_ACCESS_KEY - the contents of the service.key file

echo "NORMALISE THE CERTIFICATE VALUES"

# Arguments to the function are
# * $1 the certificate string to normalise
# * $2 the phrase after BEGIN and END in the first and last lines
normalise_cert_to_file () {
  new_string=$(echo "$1" | sed "
      s/-----BEGIN $2-----/-----BEGIN-----/g
      s/-----END $2-----/-----END-----/g
      s/ /\n/g
      s/-----BEGIN-----/-----BEGIN $2-----/g
      s/-----END-----/-----END $2-----/g
  ")
  echo "$new_string"
}

KAFKA_CA_CERT=$(normalise_cert_to_file "$KAFKA_CA_CERT" "CERTIFICATE")
KAFKA_ACCESS_CERT=$(normalise_cert_to_file "$KAFKA_ACCESS_CERT" "CERTIFICATE")
SERVICE_KEY_CONTENS=$(normalise_cert_to_file "$KAFKA_ACCESS_KEY" "PRIVATE KEY")
