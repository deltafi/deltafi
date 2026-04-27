# activemq-ingress

DeltaFi ActiveMQ Classic Ingress Bridge

## Description

Provides a container which will connect to an ActiveMQ broker and subscribe to a JMS destination (topic or queue). When a message is received
on the destination, the message content will be sent to DeltaFi to ingress on the configured REST data source.

## Set up listen on a topic

To set up a topic, edit your `site/compose.yaml` and add to the services section a configuration like the following:

```
services:
  activemq-ingress:
    image: ${DELTAFI_ACTIVEMQ_INGRESS}
    container_name: deltafi-activemq-ingress
    depends_on:
      core-scheduler:
        condition: service_healthy
    environment:
      JMS_BROKER_URL: tcp://activemq:61616
      JMS_BROKER_USER: admin
      JMS_BROKER_PASSWORD: admin
      LISTEN_DEST_NAME: passthrough-amq-topic
      LISTEN_DEST_ISATOPIC: true
      JMS_REST_DATA_SOURCE: passthrough-rest-data-source
      JMS_MEDIA_TYPE: application/octet-stream
    env_file:
      - ${CONFIG_DIR}/common.env
    labels:
      deltafi-group: deltafi-core
    networks:
      deltafi:
        aliases:
          - "deltafi-activemq-ingress-service"
          - "activemq-ingress"
    restart: unless-stopped
    volumes:
      - ${DATA_DIR}/certs/${DELTAFI_SECRET_PLUGINS_SSL:-ssl-secret}:/certs
    user: "${USER_ID}:${GROUP_ID}"
```

## Set up a second listener on a queue this time

Edit `site/compose.yaml`, and add another service with a different name, container name, destination, and data source. Do not include `LISTEN_DEST_ISATOPIC` in the environment section this time, which will default to "false" and use a queue instead of a pub-sub topic:

```
services:
  activemq-ingress2:
    image: ${DELTAFI_ACTIVEMQ_INGRESS}
    container_name: deltafi-activemq-ingress2
    depends_on:
      core-scheduler:
        condition: service_healthy
    environment:
      JMS_BROKER_URL: tcp://activemq:61616
      JMS_BROKER_USER: admin
      JMS_BROKER_PASSWORD: admin
      LISTEN_DEST_NAME: smoke-amq-queue
      JMS_REST_DATA_SOURCE: smoke-rest-data-source
      JMS_MEDIA_TYPE: application/octet-stream
    env_file:
      - ${CONFIG_DIR}/common.env
    labels:
      deltafi-group: deltafi-core
    networks:
      deltafi:
        aliases:
          - "deltafi-activemq-ingress-service2"
          - "activemq-ingress"
    restart: unless-stopped
    volumes:
      - ${DATA_DIR}/certs/${DELTAFI_SECRET_PLUGINS_SSL:-ssl-secret}:/certs
    user: "${USER_ID}:${GROUP_ID}"
```

## Testing

To start an ActiveMQ Classic broker for testing, add the following to the services section of `site/compose.yaml`:

```
services:
  activemq:
    image: apache/activemq-classic:latest
    ports:
      - "61616:61616"
      - "8161:8161"
    networks:
      - deltafi
```
Default user and password are "admin" and "admin".

Note: `activemq-ingress` is also compatible with apache/activemq-artemis.

1. deltafi up
2. Make sure the passthrough and smoke data sources are running.
3. Navigate to `http://deltafi.localhost:8161/admin` in a browser.
4. Login with admin/admin.
5. Select the Topics tab.
6. Use the 'Send To' option from the right-most column menu to send a test message to a topic.
