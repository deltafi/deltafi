/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.artifact;

import org.deltafi.common.artifact.VersionChangedEvent;
import org.deltafi.common.test.storage.s3.InMemoryObjectStorageService;
import org.deltafi.common.test.time.TestClock;
import org.deltafi.core.rest.ArtifactRegistryRest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ArtifactRegistryService}.
 * Uses {@link InMemoryObjectStorageService} as the backing store so assertions can
 * inspect real S3 key paths without spinning up a MinIO server.
 */
class ArtifactRegistryServiceTest {

    private static final String PLUGIN = "my-plugin";
    private static final String ARTIFACT = "my-artifact";
    private static final String V1 = "1.0.0";
    private static final String V2 = "2.0.0";
    private static final String V3 = "3.0.0";
    private static final byte[] BLOB_BYTES = "hello world".getBytes(StandardCharsets.UTF_8);
    private final TestClock clock = new TestClock();
    private ArtifactRegistryStorageService storageService;
    private ArtifactRegistryNotificationService notifications;
    private ArtifactRegistryService service;

    @BeforeEach
    void setup() {
        InMemoryObjectStorageService inMemoryStorage = new InMemoryObjectStorageService();
        storageService = new ArtifactRegistryStorageService(inMemoryStorage, "deltafi-artifacts");
        notifications = Mockito.mock(ArtifactRegistryNotificationService.class);
        service = new ArtifactRegistryService(storageService, notifications, clock);
    }

    private InputStream blob() {
        return new ByteArrayInputStream(BLOB_BYTES);
    }

    private PublishArtifactResponse publishV1(boolean makeActive) {
        return service.publishArtifact(PLUGIN, ARTIFACT, V1, blob(), BLOB_BYTES.length, "application/octet-stream", "first version", makeActive, null, null);
    }

    private PublishArtifactResponse publishV1() {
        return publishV1(true);
    }

    private PublishArtifactResponse publish(String version) {
        return service.publishArtifact(PLUGIN, ARTIFACT, version, blob(), BLOB_BYTES.length, "text/plain", null, true, null, null);
    }

    @Nested
    class Publish {

        @Test
        void happyPath_returnsResponse() {
            PublishArtifactResponse resp = publishV1();

            assertThat(resp.getPluginName()).isEqualTo(PLUGIN);
            assertThat(resp.getArtifactName()).isEqualTo(ARTIFACT);
            assertThat(resp.getVersion()).isEqualTo(V1);
            assertThat(resp.isActive()).isTrue();
            assertThat(resp.getSha256()).isNotBlank();
        }

        @Test
        void blobAndManifestWrittenToStorage() {
            publishV1();

            String blobKey = storageService.blobKey(PLUGIN, ARTIFACT, V1);
            String manifestKey = storageService.manifestKey(PLUGIN, ARTIFACT, V1);

            assertThat(storageService.objectExists(blobKey)).isTrue();
            assertThat(storageService.objectExists(manifestKey)).isTrue();
        }

        @Test
        void manifestContainsCorrectMetadata() {
            publishV1();

            ArtifactManifest manifest = storageService.getManifest(storageService.manifestKey(PLUGIN, ARTIFACT, V1)).orElseThrow();

            assertThat(manifest.getArtifactName()).isEqualTo(ARTIFACT);
            assertThat(manifest.getVersion()).isEqualTo(V1);
            assertThat(manifest.getDescription()).isEqualTo("first version");
            assertThat(manifest.getContentType()).isEqualTo("application/octet-stream");
            assertThat(manifest.getSha256()).isNotBlank();
            assertThat(manifest.getSize()).isEqualTo(BLOB_BYTES.length);
            assertThat(manifest.getCreatedAt()).isNotNull();
        }

        @Test
        void makeActive_setsAliasAndFiresNotification() {
            publishV1(true);

            String aliasKey = storageService.aliasKey(PLUGIN, ARTIFACT, ArtifactRegistryService.ACTIVE_ALIAS);
            assertThat(storageService.getAlias(aliasKey)).contains(V1);

            ArgumentCaptor<VersionChangedEvent> captor = ArgumentCaptor.forClass(VersionChangedEvent.class);
            verify(notifications).publish(captor.capture());
            VersionChangedEvent event = captor.getValue();
            assertThat(event.newVersion()).isEqualTo(V1);
            assertThat(event.previousVersion()).isNull();
            assertThat(event.artifactName()).isEqualTo(ARTIFACT);
        }

        @Test
        void makeActiveFalse_doesNotSetAliasOrNotify() {
            publishV1(false);

            String aliasKey = storageService.aliasKey(PLUGIN, ARTIFACT, ArtifactRegistryService.ACTIVE_ALIAS);
            assertThat(storageService.getAlias(aliasKey)).isEmpty();
            verifyNoInteractions(notifications);
        }

        @Test
        void makeActive_secondPublish_updatesAlias() {
            publishV1(true);
            publish(V2);

            String aliasKey = storageService.aliasKey(PLUGIN, ARTIFACT, ArtifactRegistryService.ACTIVE_ALIAS);
            assertThat(storageService.getAlias(aliasKey)).contains(V2);

            ArgumentCaptor<VersionChangedEvent> captor = ArgumentCaptor.forClass(VersionChangedEvent.class);
            verify(notifications, times(2)).publish(captor.capture());
            VersionChangedEvent second = captor.getAllValues().get(1);
            assertThat(second.previousVersion()).isEqualTo(V1);
            assertThat(second.newVersion()).isEqualTo(V2);
        }

        @Test
        void invalidArtifactName_slash_throwsIllegalArgumentException() {
            // Spring MVC would 404 before reaching the service for a slash in the URL path,
            // but the service validates it directly in case of non-REST callers.
            assertThatThrownBy(() -> service.publishArtifact(PLUGIN, "bad/name", V1, blob(), BLOB_BYTES.length, null, null, true, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void invalidArtifactName_leadingDot_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.publishArtifact(PLUGIN, ".hidden", V1, blob(), BLOB_BYTES.length, null, null, true, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void invalidArtifactName_tooLong_throwsIllegalArgumentException() {
            String tooLong = "a".repeat(ArtifactRegistryService.MAX_ARTIFACT_NAME_LENGTH + 1);
            assertThatThrownBy(() -> service.publishArtifact(PLUGIN, tooLong, V1, blob(), BLOB_BYTES.length, null, null, true, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maximum length");
        }

        @Test
        void validArtifactName_maxLength_succeeds() {
            String maxLength = "a".repeat(ArtifactRegistryService.MAX_ARTIFACT_NAME_LENGTH);
            assertThatCode(() -> service.publishArtifact(PLUGIN, maxLength, V1, blob(), BLOB_BYTES.length, null, null, false, null, null))
                    .doesNotThrowAnyException();
        }

        @Test
        void duplicateVersion_throwsDuplicateVersionException() {
            publishV1();

            assertThatThrownBy(ArtifactRegistryServiceTest.this::publishV1).isInstanceOf(DuplicateVersionException.class).hasMessageContaining(V1);
        }

        @Test
        void nullContentType_defaultsToOctetStream() {
            service.publishArtifact(PLUGIN, ARTIFACT, V1, blob(), BLOB_BYTES.length, null, null, false, null, null);

            ArtifactManifest manifest = storageService.getManifest(storageService.manifestKey(PLUGIN, ARTIFACT, V1)).orElseThrow();
            assertThat(manifest.getContentType()).isEqualTo("application/octet-stream");
        }

        @Test
        void labelsStoredInManifest() {
            Map<String, String> labels = Map.of("env", "prod", "team", "infra");
            service.publishArtifact(PLUGIN, ARTIFACT, V1, blob(), BLOB_BYTES.length, null, null, false, null, labels);

            ArtifactManifest manifest = storageService.getManifest(storageService.manifestKey(PLUGIN, ARTIFACT, V1)).orElseThrow();
            assertThat(manifest.getLabels()).containsAllEntriesOf(labels);
        }

        @Test
        void nullDescription_inheritsFromActiveManifest() {
            service.publishArtifact(PLUGIN, ARTIFACT, V1, blob(), BLOB_BYTES.length, null, "inherited description", true, null, null);

            service.publishArtifact(PLUGIN, ARTIFACT, V2, blob(), BLOB_BYTES.length, null, null, true, null, null);

            ArtifactManifest manifest = storageService.getManifest(storageService.manifestKey(PLUGIN, ARTIFACT, V2)).orElseThrow();
            assertThat(manifest.getDescription()).isEqualTo("inherited description");
        }

        @Test
        void nullDescription_noActiveVersion_remainsNull() {
            service.publishArtifact(PLUGIN, ARTIFACT, V1, blob(), BLOB_BYTES.length, null, null, false, null, null);

            ArtifactManifest manifest = storageService.getManifest(storageService.manifestKey(PLUGIN, ARTIFACT, V1)).orElseThrow();
            assertThat(manifest.getDescription()).isNull();
        }

        @Test
        void explicitDescription_notOverriddenByActiveManifest() {
            service.publishArtifact(PLUGIN, ARTIFACT, V1, blob(), BLOB_BYTES.length, null, "original", true, null, null);

            service.publishArtifact(PLUGIN, ARTIFACT, V2, blob(), BLOB_BYTES.length, null, "explicit", true, null, null);

            ArtifactManifest manifest = storageService.getManifest(storageService.manifestKey(PLUGIN, ARTIFACT, V2)).orElseThrow();
            assertThat(manifest.getDescription()).isEqualTo("explicit");
        }
    }

    @Nested
    class ListArtifacts {

        @Test
        void emptyRegistry_returnsEmptyList() {
            ListArtifactsResponse resp = service.listArtifacts(PLUGIN);
            assertThat(resp.getArtifacts()).isEmpty();
        }

        @Test
        void singleArtifact_returnsSummary() {
            publishV1();

            ListArtifactsResponse resp = service.listArtifacts(PLUGIN);
            assertThat(resp.getPluginName()).isEqualTo(PLUGIN);
            assertThat(resp.getArtifacts()).hasSize(1);

            ArtifactSummary summary = resp.getArtifacts().getFirst();
            assertThat(summary.getArtifactName()).isEqualTo(ARTIFACT);
            assertThat(summary.getActiveVersion()).isEqualTo(V1);
            assertThat(summary.getTotalVersions()).isEqualTo(1);
        }

        @Test
        void multipleVersions_totalVersionsIsCorrect() {
            publish(V1);
            publish(V2);
            publish(V3);

            ListArtifactsResponse resp = service.listArtifacts(PLUGIN);
            assertThat(resp.getArtifacts()).hasSize(1);
            assertThat(resp.getArtifacts().getFirst().getTotalVersions()).isEqualTo(3);
            assertThat(resp.getArtifacts().getFirst().getActiveVersion()).isEqualTo(V3);
        }
    }

    @Nested
    class ListVersions {

        @Test
        void versionsReturnedNewestFirst() {
            publish(V1);
            clock.tickSeconds(1);// ensure distinct timestamps
            publish(V2);
            clock.tickSeconds(1);
            publish(V3);

            ListVersionsResponse resp = service.listArtifactVersions(PLUGIN, ARTIFACT);
            List<String> versions = resp.getVersions().stream().map(ArtifactManifest::getVersion).toList();
            assertThat(versions).containsExactly(V3, V2, V1);
        }

        @Test
        void activeVersionPopulated() {
            publish(V1);
            publish(V2);

            ListVersionsResponse resp = service.listArtifactVersions(PLUGIN, ARTIFACT);
            assertThat(resp.getActiveVersion()).isEqualTo(V2);
        }

        @Test
        void unknownArtifact_throwsArtifactNotFoundException() {
            assertThatThrownBy(() -> service.listArtifactVersions(PLUGIN, "no-such-artifact")).isInstanceOf(ArtifactNotFoundException.class);
        }
    }

    @Nested
    class Download {

        @Test
        void downloadByVersion_returnsManifestAndBlob() throws Exception {
            publishV1();

            ArtifactRegistryService.ArtifactDownload dl = service.downloadArtifact(PLUGIN, ARTIFACT, V1);

            assertThat(dl.manifest().getVersion()).isEqualTo(V1);
            try (InputStream is = dl.blobStream()) {
                assertThat(is.readAllBytes()).isEqualTo(BLOB_BYTES);
            }
        }

        @Test
        void downloadByLatestAlias_resolvesVersion() {
            publish(V1);
            publish(V2);

            ArtifactRegistryService.ArtifactDownload dl = service.downloadArtifact(PLUGIN, ARTIFACT, "active");

            assertThat(dl.manifest().getVersion()).isEqualTo(V2);
        }

        @Test
        void downloadLatestWhenNoAlias_throwsArtifactNotFoundException() {
            assertThatThrownBy(() -> service.downloadArtifact(PLUGIN, ARTIFACT, "active")).isInstanceOf(ArtifactNotFoundException.class);
        }

        @Test
        void downloadNonExistentVersion_throwsArtifactNotFoundException() {
            publishV1();

            assertThatThrownBy(() -> service.downloadArtifact(PLUGIN, ARTIFACT, "99.0.0")).isInstanceOf(ArtifactNotFoundException.class);
        }

        @Test
        void downloadResponse_containsMetadataHeaders() {
            Map<String, String> labels = Map.of("env", "prod");
            service.publishArtifact(PLUGIN, ARTIFACT, V1, blob(), BLOB_BYTES.length, "application/octet-stream", "My artifact", true, null, labels);

            ArtifactRegistryService.ArtifactDownload dl = service.downloadArtifact(PLUGIN, ARTIFACT, V1);
            ResponseEntity<InputStreamResource> response = ArtifactRegistryRest.buildDownloadResponse(dl, PLUGIN);

            assertThat(response.getHeaders().getFirst("X-Artifact-Version")).isEqualTo(V1);
            assertThat(response.getHeaders().getFirst("X-Artifact-Sha256")).isNotBlank();
            assertThat(response.getHeaders().getFirst("X-Artifact-Description")).isEqualTo("My artifact");
            assertThat(response.getHeaders().getFirst("X-Artifact-Labels")).contains("\"env\"");
            assertThat(response.getHeaders().getFirst("X-Artifact-Source-Prefix")).isEqualTo("plugins/" + PLUGIN);
        }

        @Test
        void downloadResponse_sharedOwner_sourcePrefixIsShared() {
            service.publishArtifact(ArtifactRegistryStorageService.SHARED_OWNER, ARTIFACT, V1, blob(), BLOB_BYTES.length, null, null, true, null, null);

            ArtifactRegistryService.ArtifactDownload dl = service.downloadArtifact(ArtifactRegistryStorageService.SHARED_OWNER, ARTIFACT, V1);
            ResponseEntity<InputStreamResource> response = ArtifactRegistryRest.buildDownloadResponse(dl, ArtifactRegistryStorageService.SHARED_OWNER);

            assertThat(response.getHeaders().getFirst("X-Artifact-Source-Prefix")).isEqualTo("shared");
        }
    }

    @Nested
    class Rollback {

        @Test
        void rollback_updatesAliasAndFiresNotification() {
            publish(V1);
            publish(V2);

            RollbackResponse resp = service.rollbackArtifact(PLUGIN, ARTIFACT, V1);

            assertThat(resp.getRolledBackTo()).isEqualTo(V1);
            assertThat(resp.getPluginName()).isEqualTo(PLUGIN);

            String aliasKey = storageService.aliasKey(PLUGIN, ARTIFACT, ArtifactRegistryService.ACTIVE_ALIAS);
            assertThat(storageService.getAlias(aliasKey)).contains(V1);

            ArgumentCaptor<VersionChangedEvent> captor = ArgumentCaptor.forClass(VersionChangedEvent.class);
            // Two publishes + one rollback
            verify(notifications, times(3)).publish(captor.capture());
            VersionChangedEvent rollbackEvent = captor.getAllValues().get(2);
            assertThat(rollbackEvent.previousVersion()).isEqualTo(V2);
            assertThat(rollbackEvent.newVersion()).isEqualTo(V1);
        }

        @Test
        void rollbackToNonExistentVersion_throwsArtifactNotFoundException() {
            publishV1();

            assertThatThrownBy(() -> service.rollbackArtifact(PLUGIN, ARTIFACT, "99.0.0")).isInstanceOf(ArtifactNotFoundException.class);
        }
    }

    @Nested
    class Delete {

        @Test
        void deleteNonLatestVersion_removesBlobAndManifest() {
            publish(V1); // latest = V1
            publish(V2); // latest = V2

            service.deleteArtifactVersion(PLUGIN, ARTIFACT, V1);

            assertThat(storageService.objectExists(storageService.blobKey(PLUGIN, ARTIFACT, V1))).isFalse();
            assertThat(storageService.objectExists(storageService.manifestKey(PLUGIN, ARTIFACT, V1))).isFalse();
            // V2 still present
            assertThat(storageService.objectExists(storageService.blobKey(PLUGIN, ARTIFACT, V2))).isTrue();
        }

        @Test
        void deleteCurrentLatest_throwsActiveAliasRequiredException() {
            publish(V1);

            assertThatThrownBy(() -> service.deleteArtifactVersion(PLUGIN, ARTIFACT, V1))
                    .isInstanceOf(ActiveAliasRequiredException.class).hasMessageContaining("currently 'active'");
        }

        @Test
        void deleteNonExistentVersion_throwsArtifactNotFoundException() {
            assertThatThrownBy(() -> service.deleteArtifactVersion(PLUGIN, ARTIFACT, "0.0.1")).isInstanceOf(ArtifactNotFoundException.class);
        }
    }

    @Nested
    class DeleteArtifact {

        @Test
        void deleteArtifact_removesAllKeys() {
            publish(V1);
            publish(V2);

            service.deleteArtifact(PLUGIN, ARTIFACT);

            assertThat(storageService.objectExists(storageService.blobKey(PLUGIN, ARTIFACT, V1))).isFalse();
            assertThat(storageService.objectExists(storageService.manifestKey(PLUGIN, ARTIFACT, V1))).isFalse();
            assertThat(storageService.objectExists(storageService.blobKey(PLUGIN, ARTIFACT, V2))).isFalse();
            assertThat(storageService.objectExists(storageService.manifestKey(PLUGIN, ARTIFACT, V2))).isFalse();
            // Alias key also removed
            assertThat(storageService.getAlias(storageService.aliasKey(PLUGIN, ARTIFACT, ArtifactRegistryService.ACTIVE_ALIAS))).isEmpty();
        }

        @Test
        void deleteArtifact_unknownArtifact_throwsArtifactNotFoundException() {
            assertThatThrownBy(() -> service.deleteArtifact(PLUGIN, "no-such-artifact")).isInstanceOf(ArtifactNotFoundException.class);
        }
    }

    @Nested
    class SetAlias {

        @Test
        void setAlias_updatesAliasAndPublishesEvent() {
            publish(V1);
            publish(V2);

            SetAliasResponse resp = service.setAlias(PLUGIN, ARTIFACT, ArtifactRegistryService.ACTIVE_ALIAS, V1);

            assertThat(resp.pluginName()).isEqualTo(PLUGIN);
            assertThat(resp.artifactName()).isEqualTo(ARTIFACT);
            assertThat(resp.alias()).isEqualTo(ArtifactRegistryService.ACTIVE_ALIAS);
            assertThat(resp.version()).isEqualTo(V1);

            String aliasKey = storageService.aliasKey(PLUGIN, ARTIFACT, ArtifactRegistryService.ACTIVE_ALIAS);
            assertThat(storageService.getAlias(aliasKey)).contains(V1);

            // Two publishes + one setAlias
            ArgumentCaptor<VersionChangedEvent> captor = ArgumentCaptor.forClass(VersionChangedEvent.class);
            verify(notifications, times(3)).publish(captor.capture());
            VersionChangedEvent setAliasEvent = captor.getAllValues().get(2);
            assertThat(setAliasEvent.alias()).isEqualTo(ArtifactRegistryService.ACTIVE_ALIAS);
            assertThat(setAliasEvent.previousVersion()).isEqualTo(V2);
            assertThat(setAliasEvent.newVersion()).isEqualTo(V1);
        }

        @Test
        void setAlias_nonExistentVersion_throwsArtifactNotFoundException() {
            publishV1();

            assertThatThrownBy(() -> service.setAlias(PLUGIN, ARTIFACT, ArtifactRegistryService.ACTIVE_ALIAS, "99.0.0"))
                    .isInstanceOf(ArtifactNotFoundException.class);
        }

        @Test
        void setAlias_customAlias_storesAlias() {
            publish(V1);
            publish(V2);

            SetAliasResponse resp = service.setAlias(PLUGIN, ARTIFACT, "stable", V1);

            assertThat(resp.alias()).isEqualTo("stable");
            assertThat(resp.version()).isEqualTo(V1);

            String aliasKey = storageService.aliasKey(PLUGIN, ARTIFACT, "stable");
            assertThat(storageService.getAlias(aliasKey)).contains(V1);
        }
    }

    @Nested
    class DeleteAlias {

        @Test
        void deleteCustomAlias_removesAlias() {
            publish(V1);
            service.setAlias(PLUGIN, ARTIFACT, "stable", V1);

            String aliasKey = storageService.aliasKey(PLUGIN, ARTIFACT, "stable");
            assertThat(storageService.getAlias(aliasKey)).contains(V1);

            service.deleteAlias(PLUGIN, ARTIFACT, "stable");

            assertThat(storageService.getAlias(aliasKey)).isEmpty();
        }

        @Test
        void deleteActiveAlias_throwsActiveAliasRequiredException() {
            publish(V1);

            assertThatThrownBy(() -> service.deleteAlias(PLUGIN, ARTIFACT, "active"))
                    .isInstanceOf(ActiveAliasRequiredException.class);
        }

        @Test
        void deleteNonExistentAlias_throwsArtifactNotFoundException() {
            publish(V1);

            assertThatThrownBy(() -> service.deleteAlias(PLUGIN, ARTIFACT, "no-such-alias"))
                    .isInstanceOf(ArtifactNotFoundException.class);
        }
    }

    @Nested
    class ListAllArtifacts {

        @Test
        void emptyRegistry_returnsEmptyList() {
            List<ListArtifactsResponse> result = service.listAllArtifacts();
            assertThat(result).isEmpty();
        }

        @Test
        void multipleOwners_sharedFirst_thenSorted() {
            service.publishArtifact(ArtifactRegistryStorageService.SHARED_OWNER, ARTIFACT, V1,
                    blob(), BLOB_BYTES.length, null, null, true, null, null);
            service.publishArtifact("z-plugin", ARTIFACT, V1,
                    blob(), BLOB_BYTES.length, null, null, true, null, null);
            service.publishArtifact("a-plugin", ARTIFACT, V1,
                    blob(), BLOB_BYTES.length, null, null, true, null, null);

            List<ListArtifactsResponse> result = service.listAllArtifacts();

            assertThat(result).hasSize(3);
            assertThat(result.get(0).getPluginName()).isEqualTo(ArtifactRegistryStorageService.SHARED_OWNER);
            assertThat(result.get(1).getPluginName()).isEqualTo("a-plugin");
            assertThat(result.get(2).getPluginName()).isEqualTo("z-plugin");
        }

        @Test
        void ownersWithNoArtifacts_areOmitted() {
            service.publishArtifact(PLUGIN, ARTIFACT, V1,
                    blob(), BLOB_BYTES.length, null, null, true, null, null);

            List<ListArtifactsResponse> result = service.listAllArtifacts();

            // Only PLUGIN should appear — shared has no artifacts
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getPluginName()).isEqualTo(PLUGIN);
        }
    }

    @Nested
    class NameValidation {

        @Test
        void invalidVersionName_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.publishArtifact(PLUGIN, ARTIFACT, "../bad", blob(),
                    BLOB_BYTES.length, null, null, false, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Version");
        }

        @Test
        void invalidAliasOnPublish_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.publishArtifact(PLUGIN, ARTIFACT, V1, blob(),
                    BLOB_BYTES.length, null, null, false, "bad/alias", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Alias");
        }

        @Test
        void invalidAliasOnSetAlias_throwsIllegalArgumentException() {
            publish(V1);

            assertThatThrownBy(() -> service.setAlias(PLUGIN, ARTIFACT, "bad/alias", V1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Alias");
        }

        @Test
        void validVersionAndAlias_succeeds() {
            assertThatCode(() -> service.publishArtifact(PLUGIN, ARTIFACT, "1.0.0-rc1", blob(),
                    BLOB_BYTES.length, null, null, false, "stable", null))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class PurgeConfigTests {

        @Test
        void getDefault_returnsSystemDefault() {
            PurgeConfig config = service.getPurgeConfig(PLUGIN, ARTIFACT);

            assertThat(config).isEqualTo(ArtifactRegistryService.DEFAULT_PURGE_CONFIG);
        }

        @Test
        void setAndGet_roundTrips() {
            PurgeConfig custom = PurgeConfig.builder().keepLatest(2).maxAgeMinutes(60L).build();
            service.setPurgeConfig(PLUGIN, ARTIFACT, custom);

            assertThat(service.getPurgeConfig(PLUGIN, ARTIFACT)).isEqualTo(custom);
        }
    }

    @Nested
    class Purge {

        @Test
        void purge_keepsLatestVersion() {
            PurgeConfig config = PurgeConfig.builder().keepLatest(1).maxAgeMinutes(0L).build();
            service.setPurgeConfig(PLUGIN, ARTIFACT, config);

            // Publish an older version (needs past timestamp — adjust createdAt via manifest)
            publishOldVersion(V1, 10);
            clock.tickSeconds(1);
            publishOldVersion(V2, 5);
            clock.tickSeconds(1);
            publish(V3); // latest, createdAt = now

            PurgeResponse resp = service.purgeArtifact(PLUGIN, ARTIFACT);

            // V1 should be purged (beyond keepLatest=1 and older than maxAge=0)
            // V2 may also be purged; V3 (current latest) must survive
            assertThat(storageService.objectExists(storageService.manifestKey(PLUGIN, ARTIFACT, V3))).isTrue();
            assertThat(resp.getPurgedVersions()).isNotEmpty().doesNotContain(V3);
        }

        @Test
        void purge_respectsKeepLatest() {
            PurgeConfig config = PurgeConfig.builder().keepLatest(3).maxAgeMinutes(0L).build();
            service.setPurgeConfig(PLUGIN, ARTIFACT, config);

            publishOldVersion(V1, 20);
            clock.tickSeconds(1);
            publishOldVersion(V2, 15);
            clock.tickSeconds(1);
            publishOldVersion(V3, 10);
            clock.tickSeconds(1);
            String v4 = "4.0.0";
            publish(v4); // latest

            PurgeResponse resp = service.purgeArtifact(PLUGIN, ARTIFACT);

            // keepLatest=3 protects V2, V3, V4 (newest 3 including latest)
            // V1 is the 4th newest — should be purged if old enough
            assertThat(resp.getPurgedVersions()).isNotEmpty().doesNotContain(V2, V3, v4);
        }

        @Test
        void purge_neverPurgesCurrentLatest() {
            PurgeConfig config = PurgeConfig.builder().keepLatest(0).maxAgeMinutes(0L).build();
            service.setPurgeConfig(PLUGIN, ARTIFACT, config);

            publishOldVersion(V1, 10);

            // Rollback latest to V1 (V1 is now current latest)
            service.rollbackArtifact(PLUGIN, ARTIFACT, V1);

            PurgeResponse resp = service.purgeArtifact(PLUGIN, ARTIFACT);
            assertThat(resp.getPurgedVersions()).isEmpty();
            assertThat(storageService.objectExists(storageService.manifestKey(PLUGIN, ARTIFACT, V1))).isTrue();
        }

        /**
         * Publishes a version with a manifest backdated by {@code secondsAgo} seconds.
         */
        private void publishOldVersion(String version, long secondsAgo) {
            // Publish normally first, then overwrite the manifest with a backdated timestamp
            service.publishArtifact(PLUGIN, ARTIFACT, version, blob(), BLOB_BYTES.length, null, null, true, null, null);

            String manifestKey = storageService.manifestKey(PLUGIN, ARTIFACT, version);
            storageService.getManifest(manifestKey).ifPresent(m -> {
                ArtifactManifest backdated = ArtifactManifest.builder().artifactName(m.getArtifactName()).version(m.getVersion()).contentType(m.getContentType()).sha256(m.getSha256()).size(m.getSize()).createdAt(java.time.Instant.now().minusSeconds(secondsAgo)).modifiedAt(m.getModifiedAt()).build();
                storageService.putManifest(manifestKey, backdated);
            });
        }
    }

    @Nested
    class SharedArtifacts {

        private static final String SHARED = ArtifactRegistryStorageService.SHARED_OWNER;

        @Test
        void publishShared_writesUnderSharedPrefix() {
            service.publishArtifact(SHARED, ARTIFACT, V1, blob(), BLOB_BYTES.length, null, null, true, null, null);

            String blobKey = storageService.blobKey(SHARED, ARTIFACT, V1);
            assertThat(blobKey).startsWith("shared/");
            assertThat(storageService.objectExists(blobKey)).isTrue();
        }

        @Test
        void sharedAndPluginArtifactsAreIsolated() {
            service.publishArtifact(SHARED, ARTIFACT, V1, blob(), BLOB_BYTES.length, null, null, true, null, null);
            service.publishArtifact(PLUGIN, ARTIFACT, V1, blob(), BLOB_BYTES.length, null, null, true, null, null);

            assertThat(storageService.objectExists(storageService.blobKey(SHARED, ARTIFACT, V1))).isTrue();
            assertThat(storageService.objectExists(storageService.blobKey(PLUGIN, ARTIFACT, V1))).isTrue();
            // Verify they use different key prefixes
            assertThat(storageService.blobKey(SHARED, ARTIFACT, V1)).isNotEqualTo(storageService.blobKey(PLUGIN, ARTIFACT, V1));
        }

        @Test
        void listSharedArtifacts_returnsOnlySharedArtifacts() {
            service.publishArtifact(SHARED, ARTIFACT, V1, blob(), BLOB_BYTES.length, null, null, true, null, null);
            service.publishArtifact(PLUGIN, ARTIFACT, V2, blob(), BLOB_BYTES.length, null, null, true, null, null);

            ListArtifactsResponse sharedResp = service.listArtifacts(SHARED);
            assertThat(sharedResp.getArtifacts()).hasSize(1);
            assertThat(sharedResp.getArtifacts().getFirst().getActiveVersion()).isEqualTo(V1);

            ListArtifactsResponse pluginResp = service.listArtifacts(PLUGIN);
            assertThat(pluginResp.getArtifacts()).hasSize(1);
            assertThat(pluginResp.getArtifacts().getFirst().getActiveVersion()).isEqualTo(V2);
        }

        @Test
        void downloadShared_returnsCorrectBlob() throws Exception {
            service.publishArtifact(SHARED, ARTIFACT, V1, blob(), BLOB_BYTES.length, null, null, true, null, null);

            ArtifactRegistryService.ArtifactDownload dl = service.downloadArtifact(SHARED, ARTIFACT, V1);
            try (InputStream is = dl.blobStream()) {
                assertThat(is.readAllBytes()).isEqualTo(BLOB_BYTES);
            }
        }

        @Test
        void sharedEventPrefix_isShared() {
            service.publishArtifact(SHARED, ARTIFACT, V1, blob(), BLOB_BYTES.length, null, null, true, null, null);

            ArgumentCaptor<VersionChangedEvent> captor = ArgumentCaptor.forClass(VersionChangedEvent.class);
            verify(notifications).publish(captor.capture());
            assertThat(captor.getValue().isShared()).isTrue();
            assertThat(captor.getValue().prefix()).isEqualTo("shared");
        }
    }

    @Nested
    class ScheduledPurge {

        @Test
        void runScheduledPurge_coversPluginsAndShared() {
            // Publish two plugin artifacts and one shared artifact
            service.publishArtifact(PLUGIN, ARTIFACT, V1, blob(), BLOB_BYTES.length, null, null, true, null, null);
            service.publishArtifact(PLUGIN, "other-artifact", V1, blob(), BLOB_BYTES.length, null, null, true, null, null);
            service.publishArtifact(ArtifactRegistryStorageService.SHARED_OWNER, ARTIFACT, V1, blob(), BLOB_BYTES.length, null, null, true, null, null);

            // Should not throw even if nothing is actually purged
            assertThatCode(() -> service.runScheduledPurge()).doesNotThrowAnyException();
        }
    }

    @Nested
    class OrphanedBlobs {

        @Test
        void orphanedBlob_noManifest_isDeletedDuringScheduledPurge() {
            // Simulate a blob written without a corresponding manifest (failed publish)
            String blobKey = storageService.blobKey(PLUGIN, ARTIFACT, V1);
            storageService.putBlob(blobKey, blob(), BLOB_BYTES.length);
            // Manifest intentionally not written

            assertThat(storageService.objectExists(blobKey)).isTrue();

            service.runScheduledPurge();

            // Orphaned blob should have been cleaned up
            assertThat(storageService.objectExists(blobKey)).isFalse();
        }

        @Test
        void blobWithManifest_isNotDeletedAsBlobOrphan() {
            // Fully published version must not be mistaken for an orphan
            publishV1();

            service.runScheduledPurge();

            assertThat(storageService.objectExists(storageService.blobKey(PLUGIN, ARTIFACT, V1))).isTrue();
            assertThat(storageService.objectExists(storageService.manifestKey(PLUGIN, ARTIFACT, V1))).isTrue();
        }
    }

    @Nested
    class ExtractChildNames {

        @Test
        void extractsFirstSegmentAfterPrefix() {
            List<String> keys = List.of("plugins/foo/art1/1.0/blob", "plugins/foo/art1/1.0/manifest.json", "plugins/foo/art2/2.0/blob");

            var names = ArtifactRegistryService.extractChildNames(keys, "plugins/foo/");
            assertThat(names).containsExactly("art1", "art2");
        }

        @Test
        void ignoresKeysNotUnderPrefix() {
            List<String> keys = List.of("shared/art/1.0/blob", "plugins/foo/art/1.0/blob");
            var names = ArtifactRegistryService.extractChildNames(keys, "shared/");
            assertThat(names).containsExactly("art");
        }
    }
}
