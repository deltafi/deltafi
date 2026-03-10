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

import java.io.IOException;

/**
 * Thrown when a requested artifact, version, or alias does not exist in the registry (HTTP 404).
 *
 * <p>Extends {@link IOException} so it is caught by any code already handling general I/O
 * failures, while also allowing callers to distinguish the not-found case explicitly:
 *
 * <pre>{@code
 * try {
 *     return artifactRegistry.getArtifact("rules");
 * } catch (ArtifactNotFoundException e) {
 *     // first run — seed the artifact and return the freshly published handle
 *     return publishDefaultRules();
 * }
 * }</pre>
 */
public class ArtifactNotFoundException extends IOException {

    private final String owner;
    private final String artifactName;
    private final String alias;

    ArtifactNotFoundException(String owner, String artifactName, String alias) {
        super("Artifact not found: " + owner + "/" + artifactName + " @ " + alias);
        this.owner        = owner;
        this.artifactName = artifactName;
        this.alias        = alias;
    }

    /** The owner namespace — plugin name or {@code "shared"}. */
    public String getOwner() {
        return owner;
    }

    /** The artifact identifier that was requested. */
    public String getArtifactName() {
        return artifactName;
    }

    /** The alias or version that was requested (e.g. {@code "active"}). */
    public String getAlias() {
        return alias;
    }
}
