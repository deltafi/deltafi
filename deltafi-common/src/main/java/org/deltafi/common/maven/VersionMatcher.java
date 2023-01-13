/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.common.maven;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

public class VersionMatcher {
    /**
     * Determine if a given version satisfies a version spec.
     *
     * @param version version to check
     * @param spec spec to check against (see {@link VersionRange#createFromVersionSpec(String)} for examples)
     * @return true if the version satisfies the spec, false otherwise
     */
    public static boolean matches(String version, String spec) {
        String requiredVersionSpec = spec;

        // If a recommended spec, make it an explicit required version.
        if (!spec.contains("[") && !spec.contains("(")) {
            requiredVersionSpec = "[" + spec + "]";
        }

        try {
            return VersionRange.createFromVersionSpec(requiredVersionSpec)
                    .containsVersion(new DefaultArtifactVersion(version));
        } catch (InvalidVersionSpecificationException e) {
            return false;
        }
    }
}
