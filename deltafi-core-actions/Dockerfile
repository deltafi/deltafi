FROM deltafi/deltafi-java-jre:21.0.8-alpine-0
VOLUME /tmp
ADD deltafi-*.jar deltafi-app.jar

# Create a wrapper script to set APP_NAME if it's not already set
RUN echo '#!/bin/sh' > /home/deltafi/entrypoint.sh && \
    echo 'if [ -z "$APP_NAME" ]; then export APP_NAME=$(hostname); fi' >> /home/deltafi/entrypoint.sh && \
    echo 'echo "APP_NAME: $APP_NAME"' >> /home/deltafi/entrypoint.sh && \
    echo 'exec java -Djava.security.egd=file:/dev/./urandom -jar /deltafi-app.jar "$@"' >> /home/deltafi/entrypoint.sh && \
    chmod a+rx /home/deltafi/entrypoint.sh

ENTRYPOINT ["/home/deltafi/entrypoint.sh"]
