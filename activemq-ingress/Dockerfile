FROM deltafi/deltafi-java-jre:21.0.11-alpine-2 AS final
VOLUME /tmp
COPY probe.sh .

RUN echo '#!/bin/sh' > /home/deltafi/entrypoint.sh && \
  echo 'if [ -z "$APP_NAME" ]; then export APP_NAME=$(hostname); fi' >> /home/deltafi/entrypoint.sh && \
  echo 'if [ -z "$UNIQUE_ID" ]; then export UNIQUE_ID=$(hostname); fi' >> /home/deltafi/entrypoint.sh && \
  echo 'echo "APP_NAME: $APP_NAME"' >> /home/deltafi/entrypoint.sh && \
  echo 'echo "UNIQUE_ID: $UNIQUE_ID"' >> /home/deltafi/entrypoint.sh && \
  echo 'exec java -Djava.security.egd=file:/dev/./urandom -jar /deltafi-app.jar "$@"' >> /home/deltafi/entrypoint.sh && \
  chmod a+rx /home/deltafi/entrypoint.sh

COPY activemq-ingress-*.jar /deltafi-app.jar

ENTRYPOINT ["/home/deltafi/entrypoint.sh"]
