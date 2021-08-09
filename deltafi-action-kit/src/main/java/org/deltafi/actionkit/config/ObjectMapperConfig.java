package org.deltafi.actionkit.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.quarkus.jackson.ObjectMapperCustomizer;

import javax.inject.Singleton;

@Singleton
public class ObjectMapperConfig implements ObjectMapperCustomizer {
    // FIX: This should be injected into the DomainGatewayService, but that doesn't seem to be working.  duplicating the code there
    public void customize(ObjectMapper mapper) {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        SimpleModule module = new SimpleModule();
        mapper.registerModule(module);
    }

}