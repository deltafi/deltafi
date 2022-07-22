FROM deltafi/deltafi-domain-base:0.96.2
VOLUME /tmp
ADD /build/libs/deltafi-core-domain.jar deltafi-core-domain.jar
COPY probe.sh /
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/deltafi-core-domain.jar"]
