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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.artifact.ArtifactRegistryConstants;
import org.deltafi.common.artifact.VersionChangedEvent;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Business logic for the artifact registry.
 * Delegates storage I/O to {@link ArtifactRegistryStorageService} and
 * notifications to {@link ArtifactRegistryNotificationService}.
 *
 * <p>Both plugin-scoped and shared artifacts are handled by the same methods.
 * Pass {@value ArtifactRegistryStorageService#SHARED_OWNER} as {@code pluginName}
 * to operate on shared artifacts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArtifactRegistryService {

    static final PurgeConfig DEFAULT_PURGE_CONFIG =
            PurgeConfig.builder().keepLatest(5).maxAgeMinutes(43200L).build();

    static final String ACTIVE_ALIAS = ArtifactRegistryConstants.ACTIVE_ALIAS;

    static final int MAX_ARTIFACT_NAME_LENGTH = 512;
    private static final java.util.regex.Pattern ARTIFACT_NAME_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]*$");

    private static final DateTimeFormatter VERSION_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss.SSS").withZone(ZoneOffset.UTC);

    private final ArtifactRegistryStorageService storage;
    private final ArtifactRegistryNotificationService notifications;
    private final Clock clock;

    // ── Publish ──────────────────────────────────────────────────────────────

    /**
     * Publishes a new artifact version.
     *
     * @param pluginName   owner namespace; use {@value ArtifactRegistryStorageService#SHARED_OWNER} for shared artifacts
     * @param artifactName artifact identifier
     * @param version      version string — must be unique for this artifact
     * @param data         artifact binary stream
     * @param size         content length; pass -1 if unknown (slower, uses multipart upload)
     * @param contentType  MIME type; defaults to application/octet-stream if null/blank
     * @param description  optional human-readable description
     * @param makeActive   whether to update the 'active' alias
     * @param alias        optional additional alias to create pointing to the new version;
     *                     ignored if null, blank, or equal to 'active'
     * @param labels       freeform key-value metadata
     */
    public PublishArtifactResponse publishArtifact(
            String pluginName, String artifactName, String version,
            InputStream data, long size, String contentType,
            String description, boolean makeActive, String alias, Map<String, String> labels) {

        validateArtifactName(artifactName);

        if (version == null || version.isBlank()) {
            version = VERSION_FORMATTER.format(Instant.now(clock));
        } else {
            validateName(version, "Version");
        }

        String manifestKey = storage.manifestKey(pluginName, artifactName, version);
        if (storage.objectExists(manifestKey)) {
            throw new DuplicateVersionException(
                    "Version '" + version + "' already exists for artifact '" + artifactName + "'");
        }

        // Stream through SHA-256 digest while uploading
        String sha256;
        long storedSize;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            DigestInputStream digestStream = new DigestInputStream(data, digest);
            storedSize = storage.putBlob(storage.blobKey(pluginName, artifactName, version), digestStream, size);
            sha256 = HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new ArtifactStorageException("Failed to upload artifact blob", e);
        }

        if (description == null || description.isBlank()) {
            String activeVersion = storage.getAlias(
                    storage.aliasKey(pluginName, artifactName, ACTIVE_ALIAS)).orElse(null);
            if (activeVersion != null) {
                description = storage.getManifest(
                        storage.manifestKey(pluginName, artifactName, activeVersion))
                        .map(ArtifactManifest::getDescription).orElse(null);
            }
        }

        String effectiveContentType = (contentType != null && !contentType.isBlank())
                ? contentType : "application/octet-stream";
        Instant now = Instant.now(clock);
        ArtifactManifest manifest = ArtifactManifest.builder()
                .artifactName(artifactName)
                .version(version)
                .description(description)
                .contentType(effectiveContentType)
                .createdAt(now)
                .modifiedAt(now)
                .sha256(sha256)
                .size(size >= 0 ? size : storedSize)
                .labels(labels != null && !labels.isEmpty() ? labels : null)
                .build();
        storage.putManifest(manifestKey, manifest);

        if (makeActive) {
            String aliasKey = storage.aliasKey(pluginName, artifactName, ACTIVE_ALIAS);
            String previousVersion = storage.getAlias(aliasKey).orElse(null);
            storage.putAlias(aliasKey, version);
            notifications.publish(new VersionChangedEvent(
                    storage.eventPrefix(pluginName), artifactName, ACTIVE_ALIAS,
                    previousVersion, version, now));
        }

        if (alias != null && !alias.isBlank() && !alias.equals(ACTIVE_ALIAS)) {
            validateName(alias, "Alias");
            String aliasKey = storage.aliasKey(pluginName, artifactName, alias);
            String previousVersion = storage.getAlias(aliasKey).orElse(null);
            storage.putAlias(aliasKey, version);
            notifications.publish(new VersionChangedEvent(
                    storage.eventPrefix(pluginName), artifactName, alias,
                    previousVersion, version, now));
        }

        return PublishArtifactResponse.builder()
                .pluginName(pluginName)
                .artifactName(artifactName)
                .version(version)
                .isActive(makeActive)
                .sha256(sha256)
                .build();
    }

    // ── List all artifacts (all owners) ──────────────────────────────────────

    /**
     * Lists all artifacts across every owner (shared first, then plugin owners sorted alphabetically).
     * Owners that have no artifacts are omitted from the result.
     */
    public List<ListArtifactsResponse> listAllArtifacts() {
        List<String> owners = new ArrayList<>();
        owners.add(ArtifactRegistryStorageService.SHARED_OWNER);
        String pluginsRoot = ArtifactRegistryStorageService.PLUGIN_S3_PREFIX + "/";
        extractChildNames(storage.listKeys(pluginsRoot), pluginsRoot)
                .stream().sorted().forEach(owners::add);

        List<ListArtifactsResponse> result = new ArrayList<>();
        for (String owner : owners) {
            ListArtifactsResponse resp = listArtifacts(owner);
            if (!resp.getArtifacts().isEmpty()) {
                result.add(resp);
            }
        }
        return result;
    }

    // ── List artifacts for one owner ─────────────────────────────────────────

    /**
     * Lists all artifacts for the given owner with their latest version summary.
     *
     * @param pluginName owner namespace; use {@value ArtifactRegistryStorageService#SHARED_OWNER} for shared
     */
    public ListArtifactsResponse listArtifacts(String pluginName) {
        String prefix = storage.artifactsPrefix(pluginName);
        List<String> keys = storage.listKeys(prefix);
        Set<String> artifactNames = extractChildNames(keys, prefix);

        List<ArtifactSummary> summaries = new ArrayList<>();
        for (String artifactName : artifactNames) {
            if (ArtifactRegistryStorageService.ALIASES_SEGMENT.equals(artifactName)
                    || ArtifactRegistryStorageService.CONFIG_SEGMENT.equals(artifactName)) continue;
            try {
                String versionsPrefix = storage.versionsPrefix(pluginName, artifactName);
                String aliasesPrefix = versionsPrefix + ArtifactRegistryStorageService.ALIASES_SEGMENT + "/";

                // Read all aliases from the already-fetched key list (avoids extra listKeys call)
                Map<String, String> aliasMap = new LinkedHashMap<>();
                for (String key : keys) {
                    if (key.startsWith(aliasesPrefix)) {
                        String aliasName = key.substring(aliasesPrefix.length());
                        if (!aliasName.isEmpty() && !aliasName.contains("/")) {
                            storage.getAlias(key).ifPresent(v -> aliasMap.put(aliasName, v));
                        }
                    }
                }

                String activeVersion = aliasMap.get(ACTIVE_ALIAS);
                ArtifactManifest activeManifest = null;
                if (activeVersion != null) {
                    activeManifest = storage.getManifest(
                            storage.manifestKey(pluginName, artifactName, activeVersion)).orElse(null);
                }
                int totalVersions = countVersions(versionsPrefix);
                summaries.add(ArtifactSummary.builder()
                        .artifactName(artifactName)
                        .activeVersion(activeVersion)
                        .activeManifest(activeManifest)
                        .totalVersions(totalVersions)
                        .aliases(aliasMap.isEmpty() ? null : aliasMap)
                        .build());
            } catch (Exception e) {
                log.warn("Error building summary for artifact '{}': {}", artifactName, e.getMessage());
            }
        }
        return ListArtifactsResponse.builder().pluginName(pluginName).artifacts(summaries).build();
    }

    // ── List versions ────────────────────────────────────────────────────────

    /**
     * Lists all versions of an artifact, newest-first.
     *
     * @param pluginName   owner namespace; use {@value ArtifactRegistryStorageService#SHARED_OWNER} for shared
     * @param artifactName artifact identifier
     */
    public ListVersionsResponse listArtifactVersions(String pluginName, String artifactName) {
        String versionsPrefix = storage.versionsPrefix(pluginName, artifactName);
        List<String> keys = storage.listKeys(versionsPrefix);

        List<ArtifactManifest> versions = loadVersionManifests(keys);
        if (versions.isEmpty()) {
            throw new ArtifactNotFoundException(
                    "Artifact '" + artifactName + "' not found for owner '" + pluginName + "'");
        }
        String aliasesPrefix = versionsPrefix + ArtifactRegistryStorageService.ALIASES_SEGMENT + "/";
        Map<String, List<String>> versionAliases = new LinkedHashMap<>();
        String activeVersion = null;
        for (String key : keys) {
            if (!key.startsWith(aliasesPrefix)) continue;
            String aliasName = key.substring(aliasesPrefix.length());
            if (!aliasName.isEmpty() && !aliasName.contains("/")) {
                Optional<String> target = storage.getAlias(key);
                if (target.isPresent()) {
                    String ver = target.get();
                    if (ACTIVE_ALIAS.equals(aliasName)) {
                        activeVersion = ver;
                    }
                    versionAliases.computeIfAbsent(ver, k -> new ArrayList<>()).add(aliasName);
                }
            }
        }
        return ListVersionsResponse.builder()
                .pluginName(pluginName)
                .artifactName(artifactName)
                .activeVersion(activeVersion)
                .versions(versions)
                .aliases(versionAliases.isEmpty() ? null : versionAliases)
                .build();
    }

    // ── Download ─────────────────────────────────────────────────────────────

    /**
     * Returns the manifest and blob stream for an artifact version.
     * The {@code version} may be a concrete version string or any alias name (e.g. "active",
     * "stable"). Alias resolution is attempted first; if no alias with that name exists the
     * value is treated as a direct version string.
     * Caller must close the returned {@link InputStream}.
     *
     * @param pluginName   owner namespace; use {@value ArtifactRegistryStorageService#SHARED_OWNER} for shared
     * @param artifactName artifact identifier
     * @param version      version string or alias name
     */
    public ArtifactDownload downloadArtifact(String pluginName, String artifactName, String version) {
        String resolvedVersion = resolveVersion(version, pluginName, artifactName);
        ArtifactManifest manifest = storage.getManifest(
                        storage.manifestKey(pluginName, artifactName, resolvedVersion))
                .orElseThrow(() -> new ArtifactNotFoundException(
                        "Version '" + resolvedVersion + "' of artifact '" + artifactName + "' not found"));
        InputStream blob = storage.getBlob(storage.blobKey(pluginName, artifactName, resolvedVersion));
        return new ArtifactDownload(manifest, blob);
    }

    /** Minimal holder for a download response — manifest metadata + blob stream. */
    public record ArtifactDownload(ArtifactManifest manifest, InputStream blobStream) {}

    // ── Rollback ─────────────────────────────────────────────────────────────

    /**
     * Points the 'active' alias to a previously-published version.
     *
     * @param pluginName    owner namespace; use {@value ArtifactRegistryStorageService#SHARED_OWNER} for shared
     * @param artifactName  artifact identifier
     * @param targetVersion version to roll back to
     */
    public RollbackResponse rollbackArtifact(String pluginName, String artifactName, String targetVersion) {
        String manifestKey = storage.manifestKey(pluginName, artifactName, targetVersion);
        if (!storage.objectExists(manifestKey)) {
            throw new ArtifactNotFoundException(
                    "Version '" + targetVersion + "' not found for artifact '" + artifactName + "'");
        }
        String aliasKey = storage.aliasKey(pluginName, artifactName, ACTIVE_ALIAS);
        String previousVersion = storage.getAlias(aliasKey).orElse(null);
        storage.putAlias(aliasKey, targetVersion);
        notifications.publish(new VersionChangedEvent(
                storage.eventPrefix(pluginName), artifactName, ACTIVE_ALIAS,
                previousVersion, targetVersion, Instant.now(clock)));
        return RollbackResponse.builder()
                .pluginName(pluginName)
                .artifactName(artifactName)
                .rolledBackTo(targetVersion)
                .build();
    }

    // ── Delete version ───────────────────────────────────────────────────────

    /**
     * Deletes a specific version (blob + manifest). Refuses if the version is currently 'active'.
     *
     * @param pluginName   owner namespace; use {@value ArtifactRegistryStorageService#SHARED_OWNER} for shared
     * @param artifactName artifact identifier
     * @param version      version to delete
     */
    public void deleteArtifactVersion(String pluginName, String artifactName, String version) {
        String manifestKey = storage.manifestKey(pluginName, artifactName, version);
        if (!storage.objectExists(manifestKey)) {
            throw new ArtifactNotFoundException(
                    "Version '" + version + "' not found for artifact '" + artifactName + "'");
        }
        String currentLatest = storage.getAlias(
                storage.aliasKey(pluginName, artifactName, ACTIVE_ALIAS)).orElse(null);
        if (version.equals(currentLatest)) {
            throw new ActiveAliasRequiredException(
                    "Version '" + version + "' is currently 'active' for artifact '" + artifactName +
                    "'. Roll back to another version before deleting.");
        }
        storage.deleteKeys(List.of(
                storage.blobKey(pluginName, artifactName, version), manifestKey));
    }

    // ── Delete artifact ──────────────────────────────────────────────────────

    /**
     * Deletes an entire artifact — all versions, aliases, and config — from storage.
     *
     * @param pluginName   owner namespace; use {@value ArtifactRegistryStorageService#SHARED_OWNER} for shared
     * @param artifactName artifact identifier
     * @throws ArtifactNotFoundException if no keys exist under the artifact prefix
     */
    public void deleteArtifact(String pluginName, String artifactName) {
        String prefix = storage.versionsPrefix(pluginName, artifactName);
        List<String> keys = storage.listKeys(prefix);
        if (keys.isEmpty()) {
            throw new ArtifactNotFoundException(
                    "Artifact '" + artifactName + "' not found for owner '" + pluginName + "'");
        }
        storage.deleteKeys(keys);
    }

    // ── Delete alias ─────────────────────────────────────────────────────────

    /**
     * Removes an alias from an artifact.
     *
     * @param pluginName   owner namespace; use {@value ArtifactRegistryStorageService#SHARED_OWNER} for shared
     * @param artifactName artifact identifier
     * @param alias        alias name to remove
     * @throws ArtifactNotFoundException if the alias does not exist
     */
    public void deleteAlias(String pluginName, String artifactName, String alias) {
        if (ACTIVE_ALIAS.equals(alias)) {
            throw new ActiveAliasRequiredException(
                    "The 'active' alias cannot be deleted. Use rollback or set-alias to point it elsewhere first.");
        }
        String aliasKey = storage.aliasKey(pluginName, artifactName, alias);
        if (!storage.objectExists(aliasKey)) {
            throw new ArtifactNotFoundException(
                    "Alias '" + alias + "' not found for artifact '" + artifactName + "'");
        }
        storage.deleteKeys(List.of(aliasKey));
    }

    // ── Set alias ────────────────────────────────────────────────────────────

    /**
     * Points any alias to a specific version of an artifact.
     *
     * @param pluginName   owner namespace; use {@value ArtifactRegistryStorageService#SHARED_OWNER} for shared
     * @param artifactName artifact identifier
     * @param alias        alias name, e.g. "active"
     * @param version      target version
     * @throws ArtifactNotFoundException if the target version does not exist
     */
    public SetAliasResponse setAlias(String pluginName, String artifactName, String alias, String version) {
        validateName(alias, "Alias");
        if (!storage.objectExists(storage.manifestKey(pluginName, artifactName, version))) {
            throw new ArtifactNotFoundException(
                    "Version '" + version + "' not found for artifact '" + artifactName + "'");
        }
        String aliasKey = storage.aliasKey(pluginName, artifactName, alias);
        String previousVersion = storage.getAlias(aliasKey).orElse(null);
        storage.putAlias(aliasKey, version);
        notifications.publish(new VersionChangedEvent(
                storage.eventPrefix(pluginName), artifactName, alias,
                previousVersion, version, Instant.now(clock)));
        return new SetAliasResponse(pluginName, artifactName, alias, version);
    }

    // ── Purge config ─────────────────────────────────────────────────────────

    /**
     * Returns the purge configuration for an artifact, or the system default if none is set.
     *
     * @param pluginName   owner namespace; use {@value ArtifactRegistryStorageService#SHARED_OWNER} for shared
     * @param artifactName artifact identifier
     */
    public PurgeConfig getPurgeConfig(String pluginName, String artifactName) {
        return storage.getPurgeConfig(storage.purgeConfigKey(pluginName, artifactName))
                .orElse(DEFAULT_PURGE_CONFIG);
    }

    /**
     * Sets the purge configuration for an artifact.
     *
     * @param pluginName   owner namespace; use {@value ArtifactRegistryStorageService#SHARED_OWNER} for shared
     * @param artifactName artifact identifier
     * @param config       purge policy to apply
     */
    public void setPurgeConfig(String pluginName, String artifactName, PurgeConfig config) {
        storage.putPurgeConfig(storage.purgeConfigKey(pluginName, artifactName), config);
    }

    // ── Purge ─────────────────────────────────────────────────────────────────

    /**
     * Immediately applies the purge policy to an artifact.
     *
     * @param pluginName   owner namespace; use {@value ArtifactRegistryStorageService#SHARED_OWNER} for shared
     * @param artifactName artifact identifier
     */
    public PurgeResponse purgeArtifact(String pluginName, String artifactName) {
        PurgeConfig config = getPurgeConfig(pluginName, artifactName);
        List<String> purged = doPurge(
                storage.versionsPrefix(pluginName, artifactName),
                storage.aliasKey(pluginName, artifactName, ACTIVE_ALIAS),
                config, pluginName, artifactName);
        return PurgeResponse.builder()
                .pluginName(pluginName)
                .artifactName(artifactName)
                .purgedVersions(purged)
                .build();
    }

    /**
     * Runs the purge scheduler across all plugin and shared artifacts.
     * Called by {@link ArtifactPurgeScheduler} on a scheduled interval.
     */
    public void runScheduledPurge() {
        // Collect unique (pluginName, artifactName) pairs from all plugin keys
        String pluginsRoot = ArtifactRegistryStorageService.PLUGIN_S3_PREFIX + "/";
        List<String> pluginKeys = storage.listKeys(pluginsRoot);
        Map<String, Set<String>> byPlugin = new LinkedHashMap<>();
        for (String key : pluginKeys) {
            String[] segments = key.substring(pluginsRoot.length()).split("/", 3);
            if (segments.length >= 2 && !segments[1].startsWith("_")) {
                byPlugin.computeIfAbsent(segments[0], k -> new LinkedHashSet<>()).add(segments[1]);
            }
        }
        for (Map.Entry<String, Set<String>> entry : byPlugin.entrySet()) {
            for (String artifactName : entry.getValue()) {
                try {
                    purgeArtifact(entry.getKey(), artifactName);
                    List<String> keys = storage.listKeys(storage.versionsPrefix(entry.getKey(), artifactName));
                    cleanOrphanedBlobs(entry.getKey(), artifactName, keys);
                } catch (Exception e) {
                    log.warn("Purge failed for plugin={} artifact={}: {}",
                            entry.getKey(), artifactName, e.getMessage());
                }
            }
        }

        // Collect unique artifact names from all shared keys
        String sharedRoot = ArtifactRegistryStorageService.SHARED_S3_PREFIX + "/";
        List<String> sharedKeys = storage.listKeys(sharedRoot);
        Set<String> sharedArtifacts = new LinkedHashSet<>();
        for (String key : sharedKeys) {
            String relative = key.substring(sharedRoot.length());
            int slash = relative.indexOf('/');
            String segment = slash >= 0 ? relative.substring(0, slash) : relative;
            if (!segment.isEmpty() && !segment.startsWith("_")) {
                sharedArtifacts.add(segment);
            }
        }
        for (String artifactName : sharedArtifacts) {
            try {
                purgeArtifact(ArtifactRegistryStorageService.SHARED_OWNER, artifactName);
                List<String> keys = storage.listKeys(storage.versionsPrefix(ArtifactRegistryStorageService.SHARED_OWNER, artifactName));
                cleanOrphanedBlobs(ArtifactRegistryStorageService.SHARED_OWNER, artifactName, keys);
            } catch (Exception e) {
                log.warn("Purge failed for shared artifact={}: {}", artifactName, e.getMessage());
            }
        }
    }

    private List<String> doPurge(String versionsPrefix, String aliasKey, PurgeConfig config,
                                  String pluginName, String artifactName) {
        String currentLatest = storage.getAlias(aliasKey).orElse(null);
        List<ArtifactManifest> manifests = loadVersionManifests(versionsPrefix);
        if (manifests.isEmpty()) {
            return List.of();
        }

        Instant cutoff = Instant.now(clock).minusSeconds(config.getMaxAgeMinutes() * 60);
        List<String> candidates = new ArrayList<>();

        for (int i = 0; i < manifests.size(); i++) {
            ArtifactManifest m = manifests.get(i);
            String v = m.getVersion();
            if (!v.equals(currentLatest)
                    && i >= config.getKeepLatest()
                    && m.getCreatedAt() != null
                    && m.getCreatedAt().isBefore(cutoff)) {
                candidates.add(v);
            }
        }
        if (candidates.isEmpty()) return List.of();

        // Re-read alias immediately before deleting to close the rollback/setAlias race window
        String freshActive = storage.getAlias(aliasKey).orElse(null);
        candidates.removeIf(v -> v.equals(freshActive));

        List<String> purgedVersions = new ArrayList<>();
        for (String v : candidates) {
            storage.deleteKeys(List.of(
                    storage.blobKey(pluginName, artifactName, v),
                    storage.manifestKey(pluginName, artifactName, v)));
            purgedVersions.add(v);
            log.info("Purged artifact version: owner={} artifact={} version={}",
                    pluginName, artifactName, v);
        }
        return purgedVersions;
    }

    /**
     * Deletes blobs that have no corresponding manifest — left behind by a failed publish.
     * Uses the already-fetched key list to avoid an extra S3 list call.
     */
    private void cleanOrphanedBlobs(String pluginName, String artifactName, List<String> keys) {
        String blobSuffix = "/" + ArtifactRegistryStorageService.BLOB_NAME;
        String manifestSuffix = "/" + ArtifactRegistryStorageService.MANIFEST_NAME;
        Set<String> manifestVersionPrefixes = new HashSet<>();
        List<String> blobKeys = new ArrayList<>();

        String versionsPrefix = storage.versionsPrefix(pluginName, artifactName);
        for (String key : keys) {
            if (!key.startsWith(versionsPrefix) || key.contains("/_")) continue;
            if (key.endsWith(manifestSuffix)) {
                manifestVersionPrefixes.add(key.substring(0, key.length() - manifestSuffix.length()));
            } else if (key.endsWith(blobSuffix)) {
                blobKeys.add(key);
            }
        }

        for (String blobKey : blobKeys) {
            String versionPrefix = blobKey.substring(0, blobKey.length() - blobSuffix.length());
            if (!manifestVersionPrefixes.contains(versionPrefix)) {
                log.warn("Deleting orphaned blob (no manifest): owner={} artifact={} key={}",
                        pluginName, artifactName, blobKey);
                storage.deleteKeys(List.of(blobKey));
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Loads and returns all version manifests under a prefix, sorted newest-first by createdAt.
     * Only keys matching {@code {prefix}{version}/manifest.json} are included;
     * keys under {@code _aliases/} and {@code _config/} are ignored.
     */
    private List<ArtifactManifest> loadVersionManifests(String versionsPrefix) {
        return loadVersionManifests(storage.listKeys(versionsPrefix));
    }


    private List<ArtifactManifest> loadVersionManifests(List<String> keys) {
        List<ArtifactManifest> manifests = new ArrayList<>();
        for (String key : keys) {
            if (key.endsWith("/" + ArtifactRegistryStorageService.MANIFEST_NAME)) {
                storage.getManifest(key).ifPresent(manifests::add);
            }
        }
        manifests.sort(Comparator.comparing(
                m -> m.getCreatedAt() != null ? m.getCreatedAt() : Instant.EPOCH,
                Comparator.reverseOrder()));
        return manifests;
    }

    private int countVersions(String versionsPrefix) {
        return loadVersionManifests(versionsPrefix).size();
    }

    /**
     * Resolves the version string. Any alias name (e.g. "active", "stable") is resolved
     * to its target version by looking up the alias key. If no alias with that name exists
     * the value is returned as-is (treated as a direct version string).
     */
    private String resolveVersion(String version, String pluginName, String artifactName) {
        return storage.getAlias(storage.aliasKey(pluginName, artifactName, version))
                .orElse(version);
    }

    private static void validateArtifactName(String name) {
        validateName(name, "Artifact name");
    }

    static void validateName(String name, String label) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        if (name.length() > MAX_ARTIFACT_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    label + " exceeds maximum length of " + MAX_ARTIFACT_NAME_LENGTH + " characters");
        }
        if (!ARTIFACT_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    label + " '" + name + "' is invalid: must start with a letter or digit and contain only " +
                    "letters, digits, hyphens (-), underscores (_), and dots (.)");
        }
    }

    /**
     * Extracts the immediate child names (first path segment after the prefix)
     * from a list of keys, preserving insertion order.
     */
    static Set<String> extractChildNames(List<String> keys, String prefix) {
        Set<String> names = new LinkedHashSet<>();
        for (String key : keys) {
            if (!key.startsWith(prefix)) continue;
            String relative = key.substring(prefix.length());
            int slash = relative.indexOf('/');
            String segment = slash >= 0 ? relative.substring(0, slash) : relative;
            if (!segment.isEmpty()) {
                names.add(segment);
            }
        }
        return names;
    }
}
