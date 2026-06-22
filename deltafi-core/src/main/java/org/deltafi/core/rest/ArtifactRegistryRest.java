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
package org.deltafi.core.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.artifact.*;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.types.ErrorResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unified REST API for the artifact registry.
 *
 * <pre>
 * Base path: /api/v2/artifacts
 *
 * GET    /                                               list all artifacts across all owners
 * POST   /{owner}/{artifactName}                         publish a new version
 * GET    /{owner}                                        list artifacts for an owner
 * GET    /{owner}/{artifactName}/versions                list all versions of an artifact
 * GET    /{owner}/{artifactName}/versions/{version}      download an artifact (version or alias)
 * POST   /{owner}/{artifactName}/rollback                move the 'active' alias to a prior version
 * DELETE /{owner}/{artifactName}/versions/{version}      delete a specific version
 * DELETE /{owner}/{artifactName}                         delete entire artifact (all versions)
 * PUT    /{owner}/{artifactName}/aliases/{alias}         set an alias to a specific version
 * DELETE /{owner}/{artifactName}/aliases/{alias}         remove an alias
 * GET    /{owner}/{artifactName}/purge-config            get purge policy
 * PUT    /{owner}/{artifactName}/purge-config            set purge policy
 * POST   /{owner}/{artifactName}/purge                   trigger immediate purge
 * </pre>
 */
@RestController
@RequestMapping(value = "/api/v2/artifacts", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
@Slf4j
public class ArtifactRegistryRest {

    private static final Set<String> RESERVED_PARAMS =
            Set.of("file", "version", "description", "makeActive", "alias");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ArtifactRegistryService registryService;

    // ── List all artifacts (all owners) ───────────────────────────────────────

    @NeedsPermission.ArtifactRegistryRead
    @GetMapping
    public ResponseEntity<List<ListArtifactsResponse>> listAllArtifacts() {
        return ResponseEntity.ok(registryService.listAllArtifacts());
    }

    // ── Publish ───────────────────────────────────────────────────────────────

    @NeedsPermission.ArtifactRegistryWrite
    @PostMapping(value = "/{owner}/{artifactName}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @io.swagger.v3.oas.annotations.Operation(
            // The handler takes a flat multipart form plus a catch-all Map for arbitrary labels, which
            // springdoc cannot model (it leaks the fields as query params). Declare the true body shape:
            // named fields, a binary file, and additionalProperties:string for the dynamic labels. The
            // handler is unchanged, so the on-the-wire contract is identical (no client breaks). Also
            // documents the real 201 response (the handler returns ResponseEntity.status(CREATED)).
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schemaProperties = {
                                    @io.swagger.v3.oas.annotations.media.SchemaProperty(name = "version",
                                            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
                                    @io.swagger.v3.oas.annotations.media.SchemaProperty(name = "makeActive",
                                            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "boolean", defaultValue = "true")),
                                    @io.swagger.v3.oas.annotations.media.SchemaProperty(name = "description",
                                            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
                                    @io.swagger.v3.oas.annotations.media.SchemaProperty(name = "alias",
                                            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
                                    @io.swagger.v3.oas.annotations.media.SchemaProperty(name = "file",
                                            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "binary"))
                            },
                            // NOTE: under springdoc 3.x / Boot 4 swagger-core this renders as untyped
                            // additionalProperties:{} rather than {type:string}; still correct (arbitrary
                            // string label fields allowed), just less precise. A customizer could force the type.
                            additionalPropertiesSchema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))),
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Artifact published",
                    content = @io.swagger.v3.oas.annotations.media.Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PublishArtifactResponse.class))))
    public ResponseEntity<PublishArtifactResponse> publish(
            @PathVariable String owner,
            @PathVariable String artifactName,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestParam("file") MultipartFile file,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestParam(required = false) String version,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestParam(required = false) String description,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestParam(defaultValue = "true") boolean makeActive,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestParam(required = false) String alias,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestParam Map<String, String> allParams) throws IOException {

        Map<String, String> labels = extractLabels(allParams);
        String contentType = file.getContentType();
        long size = file.getSize();

        PublishArtifactResponse response = registryService.publishArtifact(
                owner, artifactName, version,
                file.getInputStream(), size, contentType,
                description, makeActive, alias, labels);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── List artifacts for one owner ──────────────────────────────────────────

    @NeedsPermission.ArtifactRegistryRead
    @GetMapping("/{owner}")
    public ResponseEntity<ListArtifactsResponse> listArtifacts(@PathVariable String owner) {
        return ResponseEntity.ok(registryService.listArtifacts(owner));
    }

    // ── List versions ─────────────────────────────────────────────────────────

    @NeedsPermission.ArtifactRegistryRead
    @GetMapping("/{owner}/{artifactName}/versions")
    public ResponseEntity<ListVersionsResponse> listVersions(
            @PathVariable String owner,
            @PathVariable String artifactName) {
        return ResponseEntity.ok(registryService.listArtifactVersions(owner, artifactName));
    }

    // ── Download ──────────────────────────────────────────────────────────────

    @NeedsPermission.ArtifactRegistryRead
    @GetMapping(value = "/{owner}/{artifactName}/versions/{version}",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<InputStreamResource> download(
            @PathVariable String owner,
            @PathVariable String artifactName,
            @PathVariable String version) {

        ArtifactRegistryService.ArtifactDownload download =
                registryService.downloadArtifact(owner, artifactName, version);

        return buildDownloadResponse(download, owner);
    }

    // ── Rollback ──────────────────────────────────────────────────────────────

    @NeedsPermission.ArtifactRegistryWrite
    @PostMapping("/{owner}/{artifactName}/rollback")
    public ResponseEntity<RollbackResponse> rollback(
            @PathVariable String owner,
            @PathVariable String artifactName,
            @RequestBody RollbackRequest request) {

        return ResponseEntity.ok(registryService.rollbackArtifact(
                owner, artifactName, request.getVersion()));
    }

    // ── Delete version ────────────────────────────────────────────────────────

    @NeedsPermission.ArtifactRegistryWrite
    @DeleteMapping("/{owner}/{artifactName}/versions/{version}")
    public ResponseEntity<Void> deleteVersion(
            @PathVariable String owner,
            @PathVariable String artifactName,
            @PathVariable String version) {

        registryService.deleteArtifactVersion(owner, artifactName, version);
        return ResponseEntity.noContent().build();
    }

    // ── Delete entire artifact ────────────────────────────────────────────────

    @NeedsPermission.ArtifactRegistryWrite
    @DeleteMapping("/{owner}/{artifactName}")
    public ResponseEntity<Void> deleteArtifact(
            @PathVariable String owner,
            @PathVariable String artifactName) {

        registryService.deleteArtifact(owner, artifactName);
        return ResponseEntity.noContent().build();
    }

    // ── Set alias ─────────────────────────────────────────────────────────────

    @NeedsPermission.ArtifactRegistryWrite
    @PutMapping(value = "/{owner}/{artifactName}/aliases/{alias}", consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<SetAliasResponse> setAlias(
            @PathVariable String owner,
            @PathVariable String artifactName,
            @PathVariable String alias,
            @RequestBody SetAliasRequest request) {

        return ResponseEntity.ok(registryService.setAlias(owner, artifactName, alias, request.version()));
    }

    // ── Remove alias ──────────────────────────────────────────────────────────

    @NeedsPermission.ArtifactRegistryWrite
    @DeleteMapping("/{owner}/{artifactName}/aliases/{alias}")
    public ResponseEntity<Void> deleteAlias(
            @PathVariable String owner,
            @PathVariable String artifactName,
            @PathVariable String alias) {

        registryService.deleteAlias(owner, artifactName, alias);
        return ResponseEntity.noContent().build();
    }

    // ── Purge config ──────────────────────────────────────────────────────────

    @NeedsPermission.ArtifactRegistryRead
    @GetMapping("/{owner}/{artifactName}/purge-config")
    public ResponseEntity<PurgeConfig> getPurgeConfig(
            @PathVariable String owner,
            @PathVariable String artifactName) {

        return ResponseEntity.ok(registryService.getPurgeConfig(owner, artifactName));
    }

    @NeedsPermission.ArtifactRegistryWrite
    @PutMapping(value = "/{owner}/{artifactName}/purge-config", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> setPurgeConfig(
            @PathVariable String owner,
            @PathVariable String artifactName,
            @RequestBody PurgeConfig config) {

        registryService.setPurgeConfig(owner, artifactName, config);
        return ResponseEntity.noContent().build();
    }

    // ── Trigger purge ─────────────────────────────────────────────────────────

    @NeedsPermission.ArtifactRegistryWrite
    @PostMapping("/{owner}/{artifactName}/purge")
    public ResponseEntity<PurgeResponse> triggerPurge(
            @PathVariable String owner,
            @PathVariable String artifactName) {

        return ResponseEntity.ok(registryService.purgeArtifact(owner, artifactName));
    }

    // ── Exception handlers ────────────────────────────────────────────────────

    @ExceptionHandler(ArtifactNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ArtifactNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateVersionException.class)
    public ResponseEntity<ErrorResponse> handleConflict(DuplicateVersionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(ActiveAliasRequiredException.class)
    public ResponseEntity<ErrorResponse> handleActiveConflict(ActiveAliasRequiredException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(ArtifactStorageException.class)
    public ResponseEntity<ErrorResponse> handleStorageError(ArtifactStorageException ex) {
        log.error("Artifact storage error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    public static ResponseEntity<InputStreamResource> buildDownloadResponse(
            ArtifactRegistryService.ArtifactDownload download, String owner) {

        ArtifactManifest manifest = download.manifest();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(manifest.getArtifactName()).build());
        if (manifest.getSize() > 0) {
            headers.setContentLength(manifest.getSize());
        }

        // Rich metadata headers
        if (manifest.getVersion() != null) {
            headers.set("X-Artifact-Version", manifest.getVersion());
        }
        if (manifest.getSha256() != null) {
            headers.set("X-Artifact-Sha256", manifest.getSha256());
        }
        if (manifest.getDescription() != null) {
            headers.set("X-Artifact-Description", manifest.getDescription());
        }
        Map<String, String> labels = manifest.getLabels();
        if (labels != null && !labels.isEmpty()) {
            try {
                headers.set("X-Artifact-Labels", MAPPER.writeValueAsString(labels));
            } catch (Exception e) {
                log.warn("Failed to serialize artifact labels for header", e);
            }
        }
        String sourcePrefix = "shared".equals(owner) ? "shared" : "plugins/" + owner;
        headers.set("X-Artifact-Source-Prefix", sourcePrefix);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(
                    manifest.getContentType() != null ? manifest.getContentType() : "application/octet-stream");
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(mediaType)
                .body(new InputStreamResource(download.blobStream()));
    }

    static Map<String, String> extractLabels(Map<String, String> allParams) {
        Map<String, String> labels = new HashMap<>(allParams);
        labels.keySet().removeAll(RESERVED_PARAMS);
        return labels.isEmpty() ? null : labels;
    }
}
