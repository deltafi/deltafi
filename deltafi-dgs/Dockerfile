FROM deltafi/deltafi-domain-base:0.7.0
VOLUME /tmp
ADD /build/libs/deltafi-dgs.jar deltafi-dgs.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/deltafi-dgs.jar"]
