FROM deltafi/deltafi-domain-base:0.7.0
VOLUME /tmp
ADD /build/libs/deltafi-core-domain.jar deltafi-core-domain.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/deltafi-core-domain.jar"]