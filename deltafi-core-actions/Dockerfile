FROM deltafi/deltafi-java-jre:21.0.7-alpine-1
VOLUME /tmp
ADD deltafi-*.jar deltafi-app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/deltafi-app.jar"]
