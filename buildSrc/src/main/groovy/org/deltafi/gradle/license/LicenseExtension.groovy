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

/**
 * DSL backing object for the {@code license { }} block. Configures the header file, year token
 * substitution, file extension → comment style mappings, glob include/exclude patterns shared by
 * every source set scanned by {@link LicenseCheckTask} / {@link LicenseFormatTask}, and strict-vs-
 * warn check semantics.
 *
 * <p>A small set of default extension mappings covers the languages we license today; explicit
 * {@code mapping(ext, style)} calls override them.</p>
 */
class LicenseExtension {
    static final Map<String, String> DEFAULT_MAPPINGS = [
            'java'    : CommentStyle.SLASHSTAR_STYLE,
            'groovy'  : CommentStyle.SLASHSTAR_STYLE,
            'go'      : CommentStyle.SLASHSTAR_STYLE,
            'graphql' : CommentStyle.SCRIPT_STYLE,
            'graphqls': CommentStyle.SCRIPT_STYLE,
            'sh'      : CommentStyle.SCRIPT_STYLE,
            'bash'    : CommentStyle.SCRIPT_STYLE,
            'py'      : CommentStyle.SCRIPT_STYLE,
            'rb'      : CommentStyle.SCRIPT_STYLE,
    ]

    File header
    int year = 2026
    boolean strictCheck = true
    final Map<String, String> mappings = new LinkedHashMap<>(DEFAULT_MAPPINGS)
    final List<String> excludes = []
    final List<String> includes = []

    void header(File f) { this.header = f }
    void setHeader(File f) { this.header = f }

    void year(int y) { this.year = y }

    void strictCheck(boolean v) { this.strictCheck = v }

    void mapping(String extension, String style) { mappings[extension] = style }

    void excludes(List<String> patterns) { excludes.addAll(patterns) }

    void exclude(String pattern) { excludes.add(pattern) }

    void includes(List<String> patterns) { includes.addAll(patterns) }

    void include(String pattern) { includes.add(pattern) }
}
