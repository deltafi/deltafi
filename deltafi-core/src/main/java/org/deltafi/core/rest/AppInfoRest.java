/*
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
package org.deltafi.core.rest;

import org.deltafi.core.services.DockerAppInfoService;
import org.deltafi.core.types.AppInfo;
import org.deltafi.core.types.AppName;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@Profile("!kubernetes")
public class AppInfoRest {

    private final DockerAppInfoService appInfoService;

    public AppInfoRest(DockerAppInfoService appInfoService) {
        this.appInfoService = appInfoService;
    }

    @GetMapping("appsByNode")
    public Map<String, List<AppName>> getAppsByNode() {
        return appInfoService.getNodeInfo();
    }

    @GetMapping("app/versions")
    public List<AppInfo> getRunningVersions() {
        return appInfoService.getRunningVersions();
    }
}
