/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.core.services;

import io.minio.*;
import io.minio.messages.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = "schedule.maintenance", havingValue = "true", matchIfMissing = true)
public class StorageConfigurationService {

    private static final String AGE_OFF = "AgeOff";
    public static final String GET_BUCKET_LIFECYCLE_ERROR = "Unable to get bucket lifecycle";

    private final MinioClient minioClient;
    private final DeltaFiPropertiesService deltaFiPropertiesService;

    private Integer expirationDayCache = null;

    @PostConstruct
    public void ensureBucket() throws ObjectStorageException {
        if (!bucketExists(ContentStorageService.CONTENT_BUCKET)) {
            createBucket(ContentStorageService.CONTENT_BUCKET);
            setExpiration(ContentStorageService.CONTENT_BUCKET);
        } else {
            updateAgeOffIfChanged();
        }
    }

    @SneakyThrows
    public void updateAgeOffIfChanged() {
        if (expirationChanged()) {
            setExpiration(ContentStorageService.CONTENT_BUCKET);
        }
    }

    boolean bucketExists(String bucketName) throws ObjectStorageException {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        } catch (Exception e) {
            log.error("Unable to check bucket existence");
            throw new ObjectStorageException("Unable to check bucket existence", e);
        }
    }

    void createBucket(String bucketName) throws ObjectStorageException {
        if (!bucketExists(bucketName)) {
            try {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created the bucket: " + bucketName);
            } catch (Exception e) {
                log.error("Unable to create bucket");
                throw new ObjectStorageException("Unable to create bucket", e);
            }
        }
    }

    boolean expirationChanged() throws ObjectStorageException {
        try {
            Integer expiration = getCurrentExpiration();
            return expiration == null || expiration != getAgeOffDays();
        } catch (Exception e) {
            log.error(GET_BUCKET_LIFECYCLE_ERROR);
            throw new ObjectStorageException(GET_BUCKET_LIFECYCLE_ERROR, e);
        }
    }

    void setExpiration(String bucketName) throws ObjectStorageException {
        try {
            minioClient.setBucketLifecycle(SetBucketLifecycleArgs.builder()
                    .bucket(bucketName)
                    .config(buildLifeCycleConfig()).build());
            expirationDayCache = getAgeOffDays();
            log.info("Set bucket age-off days: " + getAgeOffDays());
        } catch (Exception e) {
            log.error("Unable to set bucket lifecycle");
            throw new ObjectStorageException("Unable to set bucket lifecycle", e);
        }
    }

    private LifecycleConfiguration buildLifeCycleConfig() {
        return new LifecycleConfiguration(List.of(
                new LifecycleRule(
                        Status.ENABLED,
                        null,
                        new Expiration((ZonedDateTime) null, getAgeOffDays(), null),
                        new RuleFilter(""),
                        AGE_OFF,
                        null,
                        null,
                        null)));
    }

    Integer getCurrentExpiration() throws ObjectStorageException {
        if (expirationDayCache == null) {
            expirationDayCache = getExpirationFromMinio();
        }

        return expirationDayCache;
    }

    Integer getExpirationFromMinio() throws ObjectStorageException {
        try {
            return getExpirationFromRule(minioClient.getBucketLifecycle(GetBucketLifecycleArgs.builder().bucket(ContentStorageService.CONTENT_BUCKET).build()));
        } catch (Exception e) {
            log.error(GET_BUCKET_LIFECYCLE_ERROR);
            throw new ObjectStorageException(GET_BUCKET_LIFECYCLE_ERROR, e);
        }
    }

    Integer getExpirationFromRule(LifecycleConfiguration lifecycleConfiguration) {
        if (lifecycleConfiguration == null || lifecycleConfiguration.rules() == null || lifecycleConfiguration.rules().size() != 1) {
            return null;
        }

        LifecycleRule lifecycleRule = lifecycleConfiguration.rules().getFirst();

        if (!Status.ENABLED.equals(lifecycleRule.status()) || !AGE_OFF.equals(lifecycleRule.id())) {
            return null;
        }

        return lifecycleRule.expiration().days();
    }
    
    private int getAgeOffDays() {
        return deltaFiPropertiesService.getDeltaFiProperties().getAgeOffDays();
    }

}
