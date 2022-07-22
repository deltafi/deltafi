####
# This Dockerfile is used in order to build a container that runs the Quarkus application in JVM mode
#
# Before building the container image run:
#
# ./gradlew build
#
# Then, build the image with:
#
# docker build -f src/main/docker/Dockerfile.jvm -t quarkus/deltafi-core-actions-jvm .
#
# Then run the container using:
#
# docker run -i --rm -p 8080:8080 quarkus/deltafi-core-actions-jvm
#
# If you want to include the debug port into your docker image
# you will have to expose the debug port (default 5005) like this :  EXPOSE 8080 5005
#
# Then run the container using :
#
# docker run -i --rm -p 8080:8080 -p 5005:5005 -e JAVA_ENABLE_DEBUG="true" quarkus/deltafi-core-actions-jvm
#
###
FROM deltafi/deltafi-action-base:0.96.2

RUN echo "securerandom.source=file:/dev/urandom" >> /etc/alternatives/jre/lib/security/java.security

# Configure the JAVA_OPTIONS, you can add -XshowSettings:vm to also display the heap size.
ENV JAVA_OPTIONS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
# We make four distinct layers so if there are application changes the library layers can be re-used
COPY --chown=1001 build/quarkus-app/lib/ /deployments/lib/
COPY --chown=1001 build/quarkus-app/*.jar /deployments/
COPY --chown=1001 build/quarkus-app/app/ /deployments/app/
COPY --chown=1001 build/quarkus-app/quarkus/ /deployments/quarkus/
COPY probe.sh /

EXPOSE 8080
USER 1001

ENTRYPOINT [ "/deployments/run-java.sh" ]
