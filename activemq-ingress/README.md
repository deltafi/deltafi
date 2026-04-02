# activemq-ingress

DeltaFi ActiveMQ Ingress Bridge

## Description

Provides a container which will connect to an ActiveMQ broker and subscribe to a JMS topic. When a message is received
on the topic, the message content will be sent to DeltaFi to ingress on the configured REST data source.

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
      JMS_BROKER_URL: tcp://artemis:61616
      JMS_BROKER_USER: admin
      JMS_BROKER_PASSWORD: admin123
      JMS_LISTEN_TOPIC: passthrough-amq-topic
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

## Set up listen on a second topic

Edit `site/compose.yaml`, and add another service with a different name, container name, topic, and data source:

```
services:
  activemq-ingress2:
    image: ${DELTAFI_ACTIVEMQ_INGRESS}
    container_name: deltafi-activemq-ingress2
    depends_on:
      core-scheduler:
        condition: service_healthy
    environment:
      JMS_BROKER_URL: tcp://artemis:61616
      JMS_BROKER_USER: admin
      JMS_BROKER_PASSWORD: admin123
      JMS_LISTEN_TOPIC: smoke-amq-topic
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

To start an ActiveMQ broker for testing, add the following to the services section of `site/compose.yaml`:

```
services:
  artemis:
    image: apache/activemq-artemis:latest
    ports:
      - "61616:61616"
      - "8161:8161"
    environment:
      - ARTEMIS_USER=admin
      - ARTEMIS_PASSWORD=admin123
    networks:
      - deltafi

```

1. deltafi up
2. Make sure the passthrough and smoke data sources are running.
3. Navigate to `http://deltafi.localhost:8161/console/artemis` in a browser.
4. Login with admin/admin123.
5. Select the Queues tab.
6. Use the Send Message option from the right-most column menu to send a test message to a queue.
