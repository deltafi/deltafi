package org.deltafi.actionkit.config;

import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.content.ContentStorageService;

import javax.enterprise.inject.Produces;

public class ContentStorageConfig {
    @Produces
    public ContentStorageService contentStorageService(ObjectStorageService objectStorageService) {
        return new ContentStorageService(objectStorageService);
    }
}
