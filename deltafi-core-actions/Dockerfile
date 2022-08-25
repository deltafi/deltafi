FROM deltafi/deltafi-action-base:0.96.2

RUN echo "securerandom.source=file:/dev/urandom" >> /etc/alternatives/jre/lib/security/java.security

USER 1001

COPY --chown=1001 build/libs/*.jar /deployments/

ENTRYPOINT [ "/deployments/run-java.sh" ]
