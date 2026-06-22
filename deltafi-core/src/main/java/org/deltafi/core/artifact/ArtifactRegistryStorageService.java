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

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.artifact.ArtifactRegistryConstants;
import org.deltafi.common.storage.s3.MissingContentException;
import org.deltafi.common.storage.s3.ObjectReference;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Low-level S3 I/O for the artifact registry, backed by {@link ObjectStorageService}.
 *
 * <p>All artifacts live in a single bucket under two top-level prefixes:
 * <ul>
 *   <li>{@code plugins/{pluginName}/} — plugin-specific artifacts
 *   <li>{@code shared/} — organization-wide shared artifacts
 * </ul>
 *
 * <p>The special owner value {@value #SHARED_OWNER} routes to the {@code shared/} prefix;
 * any other value routes to {@code plugins/{owner}/}.
 */
@RequiredArgsConstructor
@Slf4j
public class ArtifactRegistryStorageService {

    public static final String SHARED_OWNER = ArtifactRegistryConstants.SHARED_OWNER;

    static final String PLUGIN_S3_PREFIX = "plugins";
    static final String SHARED_S3_PREFIX = "shared";
    static final String ALIASES_SEGMENT = "_aliases";
    static final String CONFIG_SEGMENT = "_config";
    static final String BLOB_NAME = "blob";
    static final String MANIFEST_NAME = "manifest.json";
    static final String PURGE_CONFIG_NAME = "purge.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObjectStorageService objectStorageService;
    private final String bucketName;

    // ── Key builders ─────────────────────────────────────────────────────────

    /**
     * Returns the S3 root prefix for the given owner.
     * <p>"shared" → {@code "shared/"},  any other value → {@code "plugins/{owner}/"}
     */
    public String ownerPrefix(String pluginName) {
        return SHARED_OWNER.equals(pluginName)
                ? SHARED_S3_PREFIX + "/"
                : PLUGIN_S3_PREFIX + "/" + pluginName + "/";
    }

    /** Prefix covering all artifacts for an owner: used for listing. */
    public String artifactsPrefix(String pluginName) {
        return ownerPrefix(pluginName);
    }

    /** Prefix covering all versions of a single artifact. */
    public String versionsPrefix(String pluginName, String artifactName) {
        return ownerPrefix(pluginName) + artifactName + "/";
    }

    public String blobKey(String pluginName, String artifactName, String version) {
        return versionsPrefix(pluginName, artifactName) + version + "/" + BLOB_NAME;
    }

    public String manifestKey(String pluginName, String artifactName, String version) {
        return versionsPrefix(pluginName, artifactName) + version + "/" + MANIFEST_NAME;
    }

    public String aliasKey(String pluginName, String artifactName, String alias) {
        return versionsPrefix(pluginName, artifactName) + ALIASES_SEGMENT + "/" + alias;
    }

    public String purgeConfigKey(String pluginName, String artifactName) {
        return versionsPrefix(pluginName, artifactName) + CONFIG_SEGMENT + "/" + PURGE_CONFIG_NAME;
    }

    /**
     * Returns the event prefix for {@link org.deltafi.common.artifact.VersionChangedEvent}.
     * <p>"shared" → {@code "shared"},  plugin → {@code "plugins/{pluginName}"}
     */
    public String eventPrefix(String pluginName) {
        return SHARED_OWNER.equals(pluginName)
                ? SHARED_S3_PREFIX
                : PLUGIN_S3_PREFIX + "/" + pluginName;
    }

    // ── Storage operations ───────────────────────────────────────────────────

    /** Uploads an artifact blob. Pass {@code size=-1} if content length is unknown. */
    public long putBlob(String key, InputStream inputStream, long size) {
        try {
            ObjectReference objectReference = objectStorageService.putObject(new ObjectReference(bucketName, key, 0, size), inputStream);
            return objectReference.getSize();
        } catch (ObjectStorageException e) {
            throw new ArtifactStorageException("Failed to put blob: " + key, e);
        }
    }

    /**
     * Opens a streaming read of an artifact blob. Caller must close the returned stream.
     * @throws ArtifactNotFoundException if the key does not exist
     */
    public InputStream getBlob(String key) {
        try {
            return getObject(key);
        } catch (MissingContentException e) {
            throw new ArtifactNotFoundException("Blob not found: " + key);
        } catch (ObjectStorageException e) {
            throw new ArtifactStorageException("Failed to get blob: " + key, e);
        }
    }

    public void putManifest(String key, ArtifactManifest manifest) {
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(manifest);
            objectStorageService.putObjectAsByteArray(new ObjectReference(bucketName, key, 0, bytes.length), bytes);
        } catch (Exception e) {
            throw new ArtifactStorageException("Failed to put manifest: " + key, e);
        }
    }

    public Optional<ArtifactManifest> getManifest(String key) {
        try {
            InputStream stream = getObject(key);
            return Optional.of(MAPPER.readValue(stream, ArtifactManifest.class));
        } catch (MissingContentException e) {
            return Optional.empty();
        } catch (Exception e) {
            throw new ArtifactStorageException("Failed to get manifest: " + key, e);
        }
    }

    public void putAlias(String key, String version) {
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(new ArtifactAlias(version));
            objectStorageService.putObjectAsByteArray(new ObjectReference(bucketName, key, 0, bytes.length), bytes);
        } catch (Exception e) {
            throw new ArtifactStorageException("Failed to put alias: " + key, e);
        }
    }

    public Optional<String> getAlias(String key) {
        try {
            InputStream stream = getObject(key);
            ArtifactAlias alias = MAPPER.readValue(stream, ArtifactAlias.class);
            return Optional.ofNullable(alias.getVersion());
        } catch (MissingContentException e) {
            return Optional.empty();
        } catch (Exception e) {
            throw new ArtifactStorageException("Failed to get alias: " + key, e);
        }
    }

    public void putPurgeConfig(String key, PurgeConfig config) {
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(config);
            objectStorageService.putObjectAsByteArray(new ObjectReference(bucketName, key, 0, bytes.length), bytes);
        } catch (Exception e) {
            throw new ArtifactStorageException("Failed to put purge config: " + key, e);
        }
    }

    public Optional<PurgeConfig> getPurgeConfig(String key) {
        try {
            InputStream stream = getObject(key);
            return Optional.of(MAPPER.readValue(stream, PurgeConfig.class));
        } catch (MissingContentException e) {
            return Optional.empty();
        } catch (Exception e) {
            throw new ArtifactStorageException("Failed to get purge config: " + key, e);
        }
    }

    public void deleteKeys(List<String> keys) {
        if (!keys.isEmpty()) {
            objectStorageService.removeObjects(bucketName, keys);
        }
    }

    /** Returns true if an object with the given key exists in the artifact bucket. */
    public boolean objectExists(String key) {
        return objectStorageService.getTotalSize(new ObjectReference(bucketName, key, 0, 0)) >= 0;
    }

    /**
     * Lists all object keys under a prefix (recursive).
     * Used to discover manifest files and derive artifact/version names.
     */
    public List<String> listKeys(String prefix) {
        return objectStorageService.listKeys(bucketName, prefix);
    }

    private InputStream getObject(String name) throws ObjectStorageException {
        return objectStorageService.getObject(new ObjectReference(bucketName, name, 0, -1));
    }
}
