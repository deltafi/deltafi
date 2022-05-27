FROM deltafi/deltafi-domain-base:0.95.3
VOLUME /tmp
ADD /build/libs/deltafi-config-server.jar deltafi-config-server.jar
COPY probe.sh /
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/deltafi-config-server.jar"]
