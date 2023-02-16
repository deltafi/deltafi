FROM deltafi/deltafi-spring-base:0.101.6
VOLUME /tmp
ADD /build/libs/deltafi-*.jar deltafi-app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/deltafi-app.jar"]
