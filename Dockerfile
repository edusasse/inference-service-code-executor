FROM docker:latest

# Install Bash
RUN apk add --no-cache bash

# Rename the docker cli to docker.orig
ARG DOCKER_PATH="/usr/local/bin/docker"
RUN mv -f "${DOCKER_PATH}" "${DOCKER_PATH}.orig"

# Install dond-shim at the same path as the original docker cli
ARG DOND_SHIM_VERSION="0.6.0"
ADD "https://github.com/felipecrs/docker-on-docker-shim/raw/v${DOND_SHIM_VERSION}/dond" "${DOCKER_PATH}"
RUN chmod +x "${DOCKER_PATH}"

RUN apk add --no-cache openjdk11

WORKDIR /app

COPY target/app.jar app.jar

CMD ["java", "-jar", "app.jar"]
