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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet

/**
 * Wires the {@code license { }} extension and the aggregate {@code license} / {@code licenseFormat}
 * tasks. When the {@code java} plugin is also applied, additionally registers per-source-set
 * sub-tasks ({@code licenseMain}, {@code licenseFormatMain}, …) so every source set is scanned
 * with no extra configuration.
 */
class LicensePlugin implements Plugin<Project> {

    static final String CHECK_TASK_NAME = 'license'
    static final String FORMAT_TASK_NAME = 'licenseFormat'

    @Override
    void apply(Project project) {
        project.extensions.create('license', LicenseExtension)

        LicenseCheckTask check = project.tasks.create(CHECK_TASK_NAME, LicenseCheckTask, {
            group = 'License'
            description = 'Checks that every source file carries the project license header.'
        })
        LicenseFormatTask format = project.tasks.create(FORMAT_TASK_NAME, LicenseFormatTask, {
            group = 'License'
            description = 'Inserts or refreshes the project license header on every source file.'
        })

        project.plugins.withId('java') {
            registerJavaSourceSetTasks(project, check, format)
        }
    }

    private static boolean isUnder(File child, File ancestor) {
        try {
            return child.canonicalPath.startsWith(ancestor.canonicalPath + File.separator) ||
                    child.canonicalPath == ancestor.canonicalPath
        } catch (IOException ignored) {
            return false
        }
    }

    private static void registerJavaSourceSetTasks(Project project, LicenseCheckTask check, LicenseFormatTask format) {
        JavaPluginExtension java = project.extensions.getByType(JavaPluginExtension)
        java.sourceSets.all { SourceSet sourceSet ->
            String suffix = sourceSet.name.capitalize()
            LicenseCheckTask perSetCheck = project.tasks.create(
                    CHECK_TASK_NAME + suffix, LicenseCheckTask, {
                        group = 'License'
                        description = "Checks license headers in ${sourceSet.name} sources."
                    })
            LicenseFormatTask perSetFormat = project.tasks.create(
                    FORMAT_TASK_NAME + suffix, LicenseFormatTask, {
                        group = 'License'
                        description = "Formats license headers in ${sourceSet.name} sources."
                    })
            project.afterEvaluate {
                File buildDir = project.layout.buildDirectory.get().asFile
                sourceSet.allSource.srcDirs.each { dir ->
                    if (isUnder(dir, buildDir)) return
                    perSetCheck.sourceDir(dir)
                    perSetFormat.sourceDir(dir)
                }
            }
            check.dependsOn perSetCheck
            format.dependsOn perSetFormat
        }
    }
}
