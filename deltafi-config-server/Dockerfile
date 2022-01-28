FROM deltafi/deltafi-domain-base:0.14.0
VOLUME /tmp
ADD /build/libs/deltafi-config-server.jar deltafi-config-server.jar
COPY probe.sh /
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/deltafi-config-server.jar"]
