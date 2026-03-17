# See https://github.com/fish-shell/fish-shell/issues/7323 for the `string collect` hint
set -x KAFKA_CA_CERT (cat certs/ca.pem | string collect)
set -x KAFKA_ACCESS_CERT (cat certs/service.cert | string collect)
set -x KAFKA_ACCESS_KEY (cat certs/service.key | string collect)
