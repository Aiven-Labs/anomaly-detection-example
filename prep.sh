export KAFKA_CA_CERT "$(cat certs/ca.pem)"
export KAFKA_ACCESS_CERT "$(cat certs/service.cert)"
export KAFKA_ACCESS_KEY "$(cat certs/service.key)"
