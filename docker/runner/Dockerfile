FROM --platform=$BUILDPLATFORM public.ecr.aws/eks-distro-build-tooling/golang:1.26-yum-al23 AS godotenv

ARG GODOTENV_VERSION=1.5.1
ARG TARGETOS
ARG TARGETARCH

RUN set -eux; \
  GOOS="${TARGETOS}" GOARCH="${TARGETARCH}" CGO_ENABLED=0 \
    go install "github.com/joho/godotenv/cmd/godotenv@v${GODOTENV_VERSION}"; \
  mkdir -p /out; \
  if [ -x "$(go env GOPATH)/bin/godotenv" ]; then \
    install -m 0755 "$(go env GOPATH)/bin/godotenv" /out/godotenv; \
  else \
    install -m 0755 "$(go env GOPATH)/bin/${TARGETOS}_${TARGETARCH}/godotenv" /out/godotenv; \
  fi

FROM public.ecr.aws/amazoncorretto/amazoncorretto:25-al2023-headless

ARG ORCHESTRATOR_VERSION
ARG MYSQL_CONNECTOR_VERSION=9.2.0

WORKDIR /app

RUN dnf install -yq ca-certificates gzip mailcap openssl tar \
  && dnf clean all

RUN mkdir -p /app/lib /go/bin /etc/pki/ca-trust/source/anchors

COPY target/api-test-orchestrator-${ORCHESTRATOR_VERSION}.jar /app/api-test-orchestrator.jar

RUN curl -fsSL \
  "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/${MYSQL_CONNECTOR_VERSION}/mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar" \
  -o "/app/lib/mysql-connector-j-${MYSQL_CONNECTOR_VERSION}.jar"

COPY --from=godotenv /out/godotenv /usr/local/bin/godotenv

COPY --chmod=755 docker/runner/entrypoint.sh /usr/local/bin/api-test-orchestrator-entrypoint

ENV ATO_CLASSPATH="/app/api-test-orchestrator.jar:/app/lib/*:/mocks:/shareutils:/app" \
    ATO_MOCK_SSL_GENERATE=false \
    ATO_MOCK_SSL_SUBJECT="/CN=localhost" \
    ATO_MOCK_SSL_SAN="DNS:localhost" \
    ATO_TRUST_MOCK_SSL_CERT=true

ENTRYPOINT ["api-test-orchestrator-entrypoint"]
CMD ["--config", "/app/api-test-orchestrator.yml"]
