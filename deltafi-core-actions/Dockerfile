FROM deltafi/deltafi-java-jre:21.0.5-alpine-0
VOLUME /tmp
ADD deltafi-*.jar deltafi-app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/deltafi-app.jar"]
