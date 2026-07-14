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
package org.deltafi.actionkit.artifact;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.artifact.ArtifactRegistryConstants;
import org.deltafi.common.http.HttpService;

import java.io.*;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST client for publishing and deleting artifacts in the DeltaFi artifact registry.
 *
 * <p>Artifact ownership:
 * <ul>
 *   <li>Plugin-scoped: methods without a {@code shared} qualifier use the plugin's own
 *       artifact ID as the owner namespace.
 *   <li>Shared: {@code ...Shared...} methods publish/delete under the shared namespace
 *       visible to all plugins.
 * </ul>
 *
 * <p>Injected automatically by the action-kit autoconfiguration. Inject this bean directly
 * into any action or service that needs to write artifacts at runtime:
 * <pre>{@code
 * @Autowired
 * private ArtifactRegistryClient artifactRegistry;
 *
 * artifactRegistry.publishArtifact("my-model",
 *         new ArtifactManifest(null, "Trained model v2", "application/octet-stream", null, null),
 *         Files.newInputStream(modelPath), true);
 * }</pre>
 */
@Slf4j
@SuppressWarnings("unused")
class ArtifactRegistryClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};
    private static final Map<String, String> DEFAULT_HEADERS = Map.of(
            "X-User-Permissions", "Admin",
            "X-User-Name", "deltafi-action-kit");
    public static final String ARTIFACTS_PATH = "/artifacts/";
    public static final String APPLICATION_JSON = "application/json";

    /** Base URL through to (and including) {@code /api/v2}. */
    private final String coreUrl;
    /** Artifact ID of this plugin, used as the plugin-scoped owner. */
    private final String pluginName;
    private final HttpService httpService;

    /**
     * Create a client bound to the given core URL and plugin namespace.
     *
     * @param coreUrl     base URL including {@code /api/v2}, e.g. {@code http://deltafi-core:8080/api/v2}
     * @param pluginName  Maven artifact ID of this plugin; used as the owner for plugin-scoped operations
     * @param httpService shared HTTP client
     */
    ArtifactRegistryClient(String coreUrl, String pluginName, HttpService httpService) {
        this.coreUrl = coreUrl;
        this.pluginName = pluginName;
        this.httpService = httpService;
    }

    // ── Poll DTOs ─────────────────────────────────────────────────────────────

    /** Deserialization target for the list-artifacts response (poll use only). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ArtifactListResponse(List<ArtifactSummaryEntry> artifacts) {}

    /** Per-artifact entry within {@link ArtifactListResponse}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ArtifactSummaryEntry(String artifactName, Map<String, String> aliases) {}

    // ── Read-side ─────────────────────────────────────────────────────────────

    /**
     * Download the artifact identified by {@code owner}/{@code name}/{@code alias} in a single
     * request. All metadata is returned as response headers alongside the blob body.
     *
     * @param owner owner namespace — plugin name or {@code "shared"}
     * @param name  artifact identifier
     * @param alias alias or concrete version to download (e.g. {@code "active"})
     * @return the fetched artifact, or {@code null} if the artifact does not exist (HTTP 404)
     * @throws ArtifactRegistryException if the download fails with any other non-2xx status
     */
    FetchedArtifact download(String owner, String name, String alias) {
        String url = coreUrl + ARTIFACTS_PATH + owner + "/" + name + "/versions/" + alias;
        HttpRequest request = buildGetRequest(url);
        try {
            HttpResponse<byte[]> response = httpService.execute(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) {
                return null;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ArtifactRegistryException("Failed to download artifact " + owner + "/" + name
                        + " version " + alias + ": HTTP " + response.statusCode());
            }
            ArtifactManifest manifest = parseManifestHeaders(response, owner, name);
            String sourcePrefix = parseSourcePrefix(response, owner);
            return new FetchedArtifact(manifest, sourcePrefix, response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ArtifactRegistryException("Failed to download artifact " + owner + "/" + name
                    + " version " + alias, e);
        }
    }

    /**
     * Open a streaming response for the artifact identified by {@code owner}/{@code name}/{@code alias}.
     * Unlike {@link #download}, the artifact bytes are never buffered — the HTTP connection stays
     * open until the caller closes the returned stream.
     *
     * <p>The caller <strong>must</strong> close the returned {@link ArtifactRegistryService.ArtifactStream}
     * (or its {@code stream()} component) when done, typically via try-with-resources.
     *
     * @param owner owner namespace — plugin name or {@code "shared"}
     * @param name  artifact identifier
     * @param alias alias or concrete version to stream (e.g. {@code "active"})
     * @return a stream holding the manifest and the open HTTP body stream,
     *         or {@code null} if the artifact does not exist (HTTP 404)
     * @throws IOException               if an I/O error occurs opening the stream
     * @throws ArtifactRegistryException if the request fails with any other non-2xx status
     */
    ArtifactRegistryService.ArtifactStream stream(String owner, String name, String alias) throws IOException {
        String url = coreUrl + ARTIFACTS_PATH + owner + "/" + name + "/versions/" + alias;
        HttpRequest request = buildGetRequest(url);
        try {
            HttpResponse<InputStream> response = httpService.execute(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 404) {
                response.body().close();
                return null;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (InputStream body = response.body()) {
                    throw new ArtifactRegistryException("Failed to stream artifact " + owner + "/" + name
                            + " version " + alias + ": HTTP " + response.statusCode());
                }
            }
            InputStream body = response.body();
            try {
                ArtifactManifest manifest = parseManifestHeaders(response, owner, name);
                return new ArtifactRegistryService.ArtifactStream(manifest, body);
            } catch (Exception e) {
                body.close();
                throw e;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new ArtifactRegistryException("Failed to stream artifact " + owner + "/" + name
                    + " version " + alias, e);
        }
    }

    // ── Response header helpers ───────────────────────────────────────────────

    private ArtifactManifest parseManifestHeaders(HttpResponse<?> response, String owner, String name) {
        String version = response.headers().firstValue("X-Artifact-Version").orElse(null);
        String sha256 = response.headers().firstValue("X-Artifact-Sha256").orElse(null);
        String description = response.headers().firstValue("X-Artifact-Description").orElse(null);
        String contentType = response.headers().firstValue("Content-Type").orElse(null);
        // Strip charset and other parameters from Content-Type if present
        if (contentType != null && contentType.contains(";")) {
            contentType = contentType.substring(0, contentType.indexOf(';')).trim();
        }
        String labelsJson = response.headers().firstValue("X-Artifact-Labels").orElse(null);
        Map<String, String> labels = readLabels(labelsJson, owner, name);
        return new ArtifactManifest(version, description, contentType, sha256, labels);
    }

    private static String parseSourcePrefix(HttpResponse<?> response, String owner) {
        return response.headers().firstValue("X-Artifact-Source-Prefix")
                .orElse("shared".equals(owner) ? "shared" : "plugins/" + owner);
    }

    private Map<String, String> readLabels(String labelsJson, String owner, String name) {
        if (labelsJson != null && !labelsJson.isBlank()) {
            try {
                return MAPPER.readValue(labelsJson, STRING_MAP);
            } catch (Exception e) {
                log.warn("Failed to parse X-Artifact-Labels header for {}/{}: {}", owner, name, e.getMessage());
            }
        }
        return Map.of();
    }

    /**
     * Lists all artifacts for the given owner, including their alias→version maps.
     * Used by the background poll to detect drifted cache entries.
     *
     * @param owner owner namespace — plugin name or {@code "shared"}
     * @return the list response, or {@code null} if the owner has no artifacts (HTTP 404)
     * @throws RuntimeException if the request fails with any other non-2xx status
     */
    ArtifactListResponse listArtifacts(String owner) {
        String url = coreUrl + ARTIFACTS_PATH + owner;
        HttpRequest request = buildGetRequest(url);
        try {
            HttpResponse<byte[]> response = httpService.execute(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) {
                return null;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ArtifactRegistryException("Failed to list artifacts for owner '" + owner
                        + "': HTTP " + response.statusCode());
            }
            return MAPPER.readValue(response.body(), ArtifactListResponse.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ArtifactRegistryException("Failed to list artifacts for owner '" + owner + "'", e);
        }
    }

    /**
     * Publish {@code defaultBytes} as a new version of the artifact if no version exists yet.
     * Published with label {@code default=true} so the handle's {@code isDefault()} returns true.
     * A 409 Conflict response (artifact already exists) is silently ignored.
     *
     * <p>Always publishes as the active version. If {@code alias} is not {@code "active"}, the
     * alias is also created in the same request so the caller's requested alias is immediately
     * resolvable after seeding.
     *
     * @param owner        owner namespace — plugin name or {@code "shared"}
     * @param artifactName artifact identifier
     * @param alias        alias the caller will resolve; determines whether an extra alias field
     *                     is sent alongside {@code makeActive=true}
     * @param defaultBytes raw bytes to publish as the seed version
     */
    /**
     * Publish {@code defaultBytes} as a new version of the artifact if no version exists yet.
     * Returns the server-provided manifest on success so the caller can cache the local bytes
     * with accurate metadata. Returns {@code null} on failure or 409 conflict.
     */
    ArtifactManifest seedIfMissing(String owner, String artifactName, String alias, byte[] defaultBytes) {
        String url = coreUrl + ARTIFACTS_PATH + owner + "/" + artifactName;
        String boundary = UUID.randomUUID().toString().replace("-", "");
        try {
            boolean isActive = ArtifactRegistryConstants.ACTIVE_ALIAS.equals(alias);
            ArtifactManifest defaultManifest = new ArtifactManifest(null, null, null, null, Map.of("default", "true"));
            byte[] body = buildMultipartBody(boundary, artifactName, defaultManifest,
                    new ByteArrayInputStream(defaultBytes), true, isActive ? null : alias);
            String multipartContentType = "multipart/form-data; boundary=" + boundary;
            HttpRequest request = HttpService.newRequestBuilder(url, DEFAULT_HEADERS, multipartContentType)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<InputStream> response = httpService.execute(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            try (InputStream responseBody = response.body()) {
                if (status == 409) {
                    return null;
                }
                if (status < 200 || status >= 300) {
                    log.warn("seedIfMissing failed for {}/{}: HTTP {}", owner, artifactName, status);
                    return null;
                }
                PublishArtifactResponse publishResponse = MAPPER.readValue(responseBody, PublishArtifactResponse.class);
                return new ArtifactManifest(
                        publishResponse.getVersion(), null, "application/octet-stream",
                        publishResponse.getSha256(), Map.of("default", "true"));
            }
        } catch (Exception e) {
            log.warn("seedIfMissing failed for {}/{}: {}", owner, artifactName, e.getMessage());
            return null;
        }
    }

    private HttpRequest buildGetRequest(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET();
        DEFAULT_HEADERS.forEach(builder::header);
        return builder.build();
    }

    // ── Plugin-scoped publish ─────────────────────────────────────────────────

    /**
     * Publish a new artifact version scoped to this plugin.
     *
     * @param artifactName  artifact identifier (must be unique within the plugin namespace)
     * @param manifest      artifact metadata; {@code manifest.version()} may be null to let the
     *                      server generate a timestamp-based version
     * @param data          artifact byte stream — caller is responsible for closing
     * @param makeActive    whether to update the {@code active} alias after publishing
     * @return the server's publish response including the assigned version and SHA-256 digest
     * @throws IOException if the HTTP request fails or the server returns a non-2xx status
     */
    public PublishArtifactResponse publishArtifact(
            String artifactName, ArtifactManifest manifest,
            InputStream data, boolean makeActive) throws IOException {
        return publish(pluginBasePath(), artifactName, manifest, data, makeActive);
    }

    // ── Shared publish ────────────────────────────────────────────────────────

    /**
     * Publish a new artifact version to the shared namespace (visible to all plugins).
     *
     * @param artifactName artifact identifier (must be unique within the shared namespace)
     * @param manifest     artifact metadata; {@code manifest.version()} may be null to let the
     *                     server generate a timestamp-based version
     * @param data         artifact byte stream — caller is responsible for closing
     * @param makeActive   whether to update the {@code active} alias after publishing
     * @return the server's publish response including the assigned version and SHA-256 digest
     * @throws IOException if the HTTP request fails or the server returns a non-2xx status
     */
    public PublishArtifactResponse publishSharedArtifact(
            String artifactName, ArtifactManifest manifest,
            InputStream data, boolean makeActive) throws IOException {
        return publish(sharedBasePath(), artifactName, manifest, data, makeActive);
    }

    /**
     * Set an alias of a plugin-scoped artifact to point to a specific version.
     *
     * @param artifactName artifact identifier
     * @param alias        alias name, e.g. "active"
     * @param version      target version
     * @throws IOException if the HTTP request fails or the server returns a non-2xx status
     */
    public void setAlias(String artifactName, String alias, String version) throws IOException {
        putAlias(pluginBasePath(), artifactName, alias, version);
    }

    /**
     * Set an alias of a shared artifact to point to a specific version.
     *
     * @param artifactName artifact identifier
     * @param alias        alias name, e.g. "active"
     * @param version      target version
     * @throws IOException if the HTTP request fails or the server returns a non-2xx status
     */
    public void setSharedAlias(String artifactName, String alias, String version) throws IOException {
        putAlias(sharedBasePath(), artifactName, alias, version);
    }

    private PublishArtifactResponse publish(
            String basePath, String artifactName, ArtifactManifest manifest,
            InputStream data, boolean makeActive) throws IOException {

        String url = coreUrl + basePath + "/" + artifactName;
        String boundary = UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildMultipartBody(boundary, artifactName, manifest, data, makeActive, null);

        String multipartContentType = "multipart/form-data; boundary=" + boundary;
        HttpRequest request = HttpService.newRequestBuilder(url, DEFAULT_HEADERS, multipartContentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<InputStream> response = httpService.execute(request, HttpResponse.BodyHandlers.ofInputStream());

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            try (InputStream responseBody = response.body()) {
                throw new IOException("Failed to publish artifact '" + artifactName + "': " +
                        status + " - " + new String(responseBody.readAllBytes(), StandardCharsets.UTF_8));
            }
        }

        try (InputStream responseBody = response.body()) {
            return MAPPER.readValue(responseBody, PublishArtifactResponse.class);
        }
    }

    private void putAlias(String basePath, String artifactName, String alias, String version) throws IOException {
        String url = coreUrl + basePath + "/" + artifactName + "/aliases/" + alias;
        String jsonBody = "{\"version\":\"" + version + "\"}";
        byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        HttpResponse<byte[]> response = httpService.putForBytes(url, DEFAULT_HEADERS,
                new ByteArrayInputStream(bodyBytes), APPLICATION_JSON);

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("Failed to set alias '" + alias + "' for artifact '" + artifactName +
                    "': " + status + " - " + new String(response.body(), StandardCharsets.UTF_8));
        }
    }

    /**
     * Builds the raw bytes for a {@code multipart/form-data} request.
     * Fields are written first, followed by the binary file part.
     *
     * @param alias optional alias to create pointing to the new version; null means none
     */
    private static byte[] buildMultipartBody(
            String boundary, String artifactName, ArtifactManifest manifest,
            InputStream data, boolean makeActive, String alias) throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        String version = manifest != null ? manifest.version() : null;
        String description = manifest != null ? manifest.description() : null;
        String contentType = manifest != null ? manifest.contentType() : null;
        Map<String, String> labels = manifest != null ? manifest.labels() : null;

        if (version != null && !version.isBlank()) {
            writeField(out, boundary, "version", version);
        }
        writeField(out, boundary, "makeActive", String.valueOf(makeActive));
        if (alias != null && !alias.isBlank()) {
            writeField(out, boundary, "alias", alias);
        }
        if (description != null && !description.isBlank()) {
            writeField(out, boundary, "description", description);
        }
        if (labels != null) {
            for (Map.Entry<String, String> label : labels.entrySet()) {
                writeField(out, boundary, label.getKey(), label.getValue());
            }
        }

        // File part
        String effectiveContentType = (contentType != null && !contentType.isBlank())
                ? contentType : "application/octet-stream";
        String fileHeader = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"" + artifactName + "\"\r\n" +
                "Content-Type: " + effectiveContentType + "\r\n\r\n";
        out.write(fileHeader.getBytes(StandardCharsets.UTF_8));
        data.transferTo(out);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        return out.toByteArray();
    }

    private static void writeField(OutputStream out, String boundary, String name, String value)
            throws IOException {
        String part = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" +
                value + "\r\n";
        out.write(part.getBytes(StandardCharsets.UTF_8));
    }

    String getPluginName() {
        return pluginName;
    }

    private String pluginBasePath() {
        return ARTIFACTS_PATH + pluginName;
    }

    private static String sharedBasePath() {
        return ARTIFACTS_PATH + ArtifactRegistryConstants.SHARED_OWNER;
    }
}
