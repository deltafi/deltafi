FROM deltafi/deltafi-java-jre:21.0.4-alpine-0
VOLUME /tmp
ADD /build/libs/deltafi-*.jar deltafi-app.jar
COPY probe.sh /
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-noverify","-jar","/deltafi-app.jar"]
