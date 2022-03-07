package org.deltafi.actionkit.config;

public interface SslConfig {

    String keyStore();
    String keyStorePassword();
    String keyStoreType();
    String trustStore();
    String trustStorePassword();
    String trustStoreType();
    String protocol();
}
