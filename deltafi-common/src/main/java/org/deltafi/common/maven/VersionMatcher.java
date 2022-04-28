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
