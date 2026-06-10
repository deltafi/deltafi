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

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Inserts or replaces the license header on every in-scope source file. Idempotent: a second run
 * against an already-licensed tree leaves files byte-identical.
 */
@DisableCachingByDefault(because = 'Writes source files in place; not cacheable.')
class LicenseFormatTask extends DefaultTask {
    @Internal
    FileTree source

    @Internal
    final List<File> sourceDirs = []

    void setSource(FileTree source) { this.source = source }

    void sourceDir(File dir) { sourceDirs.add(dir) }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    FileTree getInputFiles() {
        FileTree tree = source ?: project.files().asFileTree
        for (File dir : sourceDirs) {
            if (dir.exists()) {
                tree = tree.plus(project.fileTree(dir) as ConfigurableFileTree)
            }
        }
        return tree
    }

    @TaskAction
    void format() {
        LicenseExtension ext = project.extensions.getByType(LicenseExtension)
        LicenseSupport support = new LicenseSupport(ext)

        applyToTree(support, filtered(source, ext), logger)
        for (File dir : sourceDirs) {
            if (dir.exists()) applyToTree(support, filtered(project.fileTree(dir), ext), logger)
        }
    }

    private static FileTree filtered(FileTree tree, LicenseExtension ext) {
        if (tree == null) return null
        return tree.matching {
            if (!ext.includes.isEmpty()) it.includes = ext.includes
            it.excludes = ext.excludes
        }
    }

    private static void applyToTree(LicenseSupport support, FileTree tree, org.gradle.api.logging.Logger logger) {
        if (tree == null) return
        tree.visit { details ->
            if (details.directory) return
            File file = details.file
            String style = support.styleFor(file)
            if (style == null) return

            String expected = support.rendered(style)
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8)
            if (CommentStyle.isGeneratedExempt(content)) return
            int shebangEnd = CommentStyle.shebangEnd(content)
            int prefixEnd = CommentStyle.leadingLineCommentEnd(style, content, shebangEnd)
            int existingEnd = CommentStyle.existingHeaderEnd(style, content, prefixEnd)

            String prefix = content.substring(0, prefixEnd)
            String body = existingEnd < 0 ? content.substring(prefixEnd) : content.substring(existingEnd)
            String rewritten = prefix + expected + body
            if (rewritten == content) return

            Files.writeString(file.toPath(), rewritten, StandardCharsets.UTF_8)
            logger.lifecycle("Updated license header: ${file.path}")
        }
    }
}
