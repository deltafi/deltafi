# Scab kubectl from this bitnami image...
FROM bitnami/kubectl:1.29.1 as kubectl

FROM ubuntu:jammy
RUN apt-get -y update && apt-get -y install bash netcat && apt-get clean && rm -rf /var/lib/apt/lists/*
COPY --from=kubectl /opt/bitnami/kubectl/bin/kubectl /usr/local/bin
# USER 1001
WORKDIR /app
COPY . /app
CMD [ "bash", "-c", "/app/doit.sh" ]
