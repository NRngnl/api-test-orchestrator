FROM public.ecr.aws/amazoncorretto/amazoncorretto:25-al2023-headless

ARG ORCHESTRATOR_VERSION
ARG MYSQL_CONNECTOR_VERSION=9.7.0

WORKDIR /app

RUN dnf install -yq ca-certificates gzip mailcap openssl tar \
  && dnf clean all

RUN mkdir -p /app/lib /go/bin /etc/pki/ca-trust/source/anchors

COPY target/api-test-orchestrator-${ORCHESTRATOR_VERSION}.jar /app/api-test-orchestrator.jar

RUN curl -fsSL \
  "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/${MYSQL_CONNECTOR_VERSION}/mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar" \
  -o "/app/lib/mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar"

COPY --chmod=755 docker/runner/entrypoint.sh /usr/local/bin/api-test-orchestrator-entrypoint

ENV PATH="/usr/local/bin:${PATH}" \
    ATO_CLASSPATH="/app/api-test-orchestrator.jar:/app/lib/*:/mocks:/shareutils:/app" \
    ATO_MOCK_SSL_GENERATE=false \
    ATO_MOCK_SSL_SUBJECT="/CN=localhost" \
    ATO_MOCK_SSL_SAN="DNS:localhost" \
    ATO_TRUST_MOCK_SSL_CERT=true

ENTRYPOINT ["api-test-orchestrator-entrypoint"]
CMD ["--config", "/app/api-test-orchestrator.yml"]
