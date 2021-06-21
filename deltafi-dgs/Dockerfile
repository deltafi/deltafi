FROM adoptopenjdk/openjdk11:jdk-11.0.9_11.1-alpine
VOLUME /tmp
ADD /build/libs/deltafi-dgs-0.0.1-SNAPSHOT.jar deltafi-dgs.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/deltafi-dgs.jar"]
