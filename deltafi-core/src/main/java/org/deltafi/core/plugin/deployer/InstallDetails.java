/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.plugin.deployer;

public record InstallDetails(String image, String appName, String imagePullSecret) {

    public static InstallDetails from(String image) {
        return from(image, null);
    }

    public static InstallDetails from(String image, String imagePullSecret) {
        int lastColon = image.lastIndexOf(':');
        int lastSlash = image.lastIndexOf('/');

        String imageNoTag = image;
        if (lastColon > lastSlash) {
            imageNoTag = image.substring(0, lastColon);
        }

        String appName = lastSlash != -1 ? imageNoTag.substring(lastSlash + 1) : imageNoTag;
        return new InstallDetails(image, appName, imagePullSecret);
    }
}
