package org.deltafi.core.domain.configuration;

import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ContentStorageConfiguration {
    @Bean
    public ContentStorageService contentStorageService(ObjectStorageService objectStorageService) {
        return new ContentStorageService(objectStorageService);
    }
}
