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

package org.deltafi.gradle.docker

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Zip

class DockerPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        DockerExtension ext = project.extensions.create('docker', DockerExtension, project)

        Delete clean = project.tasks.create('dockerClean', Delete, {
            group = 'Docker'
            description = 'Cleans Docker build directory.'
        })

        Copy prepare = project.tasks.create('dockerPrepare', Copy, {
            group = 'Docker'
            description = 'Prepares Docker build directory.'
            dependsOn clean
        })

        Exec exec = project.tasks.create('docker', Exec, {
            group = 'Docker'
            description = 'Builds Docker image.'
            dependsOn prepare
        })

        Task tag = project.tasks.create('dockerTag', {
            group = 'Docker'
            description = 'Applies all tags to the Docker image.'
            dependsOn exec
        })

        Zip dockerfileZip = project.tasks.create('dockerfileZip', Zip, {
            group = 'Docker'
            description = 'Bundles the configured Dockerfile in a zip file'
        })

        project.afterEvaluate {
            ext.resolvePathsAndValidate()
            String dockerDir = "${project.buildDir}/docker"
            clean.delete dockerDir

            prepare.with {
                with ext.copySpec
                from(ext.resolvedDockerfile) {
                    rename { fileName ->
                        fileName.replace(ext.resolvedDockerfile.getName(), 'Dockerfile')
                    }
                }
                into dockerDir
            }

            exec.with {
                workingDir dockerDir
                List<Object> buildCommand = ['docker', 'build', '-t', "${-> ext.name}"]
                ext.labels.each { key, value ->
                    buildCommand.add('--label')
                    buildCommand.add("${key}=${value}")
                }
                buildCommand.add('.')
                commandLine buildCommand
            }

            ext.namedTags.each { taskName, tagValue ->
                String tagTaskName = 'dockerTag' + generateTagTaskName(taskName)
                Exec tagSubTask = project.tasks.create(tagTaskName, Exec, {
                    group = 'Docker'
                    description = "Tags Docker image with tag '${tagValue}'"
                    workingDir dockerDir
                    commandLine 'docker', 'tag', "${-> ext.name}", tagValue
                    dependsOn exec
                })
                tag.dependsOn tagSubTask
            }

            dockerfileZip.with {
                from(ext.resolvedDockerfile)
            }
        }
    }

    private static String generateTagTaskName(String name) {
        if (name == null || name.isEmpty()) {
            throw new GradleException("Task name of docker tag '${name}' must not be empty.")
        }
        return name.substring(0, 1).toUpperCase() + name.substring(1)
    }
}
