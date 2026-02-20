# See https://github.com/fish-shell/fish-shell/issues/7323 for the `string collect` hint
set -x CA_PEM_CONTENTS (cat certs/ca.pem | string collect)
set -x SERVICE_CERT_CONTENTS (cat certs/service.cert | string collect)
set -x SERVICE_KEY_CONTENTS (cat certs/service.key | string collect)
