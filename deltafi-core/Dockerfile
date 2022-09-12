FROM deltafi/deltafi-spring-base:0.97.0
VOLUME /tmp
ADD /build/libs/deltafi-core.jar deltafi-core.jar
COPY probe.sh /
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/deltafi-core.jar"]
