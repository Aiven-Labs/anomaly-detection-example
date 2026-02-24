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
# - CA_PEM_CONTENTS - the contents of the ca.pem file
# - SERVICE_CERT_CONTENTS - the contents of the service.cert file
# - SERVICE_KEY_CONTENTS - the contents of the service.key file

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

CA_PEM_CONTENTS=$(normalise_cert_to_file "$CA_PEM_CONTENTS" "CERTIFICATE")
SERVICE_CERT_CONTENTS=$(normalise_cert_to_file "$SERVICE_CERT_CONTENTS" "CERTIFICATE")
SERVICE_KEY_CONTENS=$(normalise_cert_to_file "$SERVICE_KEY_CONTENTS" "PRIVATE KEY")
