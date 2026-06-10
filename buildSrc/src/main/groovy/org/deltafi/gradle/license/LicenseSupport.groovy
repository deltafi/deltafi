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
package org.deltafi.gradle.license

import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Shared helpers for {@link LicenseCheckTask} and {@link LicenseFormatTask}: reads the HEADER
 * once per task (with {@code $&#123;year&#125;} substituted), caches the rendered header per
 * comment style, and picks the comment style for a file by its extension.
 */
class LicenseSupport {
    private final LicenseExtension extension
    private final List<String> headerLines
    private final Map<String, String> renderedByStyle = [:]

    LicenseSupport(LicenseExtension extension) {
        this.extension = extension
        if (extension.header == null) {
            throw new IllegalStateException("license { header = ... } must be configured")
        }
        String raw = Files.readString(extension.header.toPath(), StandardCharsets.UTF_8)
        String substituted = raw.replace('${year}', Integer.toString(extension.year))
        this.headerLines = substituted
                .split('\n', -1)
                .collect { String line -> line.replaceAll(/\s+$/, '') }
        // The HEADER file ends with a trailing newline (and a trailing whitespace-only line).
        // Strip every trailing empty entry so the rendered comment doesn't carry stray blank lines.
        while (!headerLines.isEmpty() && headerLines.last().isEmpty()) {
            headerLines.removeAt(headerLines.size() - 1)
        }
    }

    String styleFor(File file) {
        String name = file.name
        int dot = name.lastIndexOf('.')
        if (dot < 0) return null
        return extension.mappings[name.substring(dot + 1)]
    }

    String rendered(String style) {
        return renderedByStyle.computeIfAbsent(style) { CommentStyle.render(it, headerLines) }
    }

}
