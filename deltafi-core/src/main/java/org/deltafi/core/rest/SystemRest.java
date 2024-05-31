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

import lombok.AllArgsConstructor;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.SystemService;
import org.deltafi.core.services.SystemService.Status;
import org.deltafi.core.services.SystemService.Versions;
import org.deltafi.core.types.AppName;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@AllArgsConstructor
public class SystemRest {

    private final SystemService systemService;

    @GetMapping("status")
    @NeedsPermission.StatusView
    public Status systemStatus() {
        return systemService.systemStatus();
    }

    @GetMapping("versions")
    @NeedsPermission.VersionsView
    public Versions getRunningVersions() {
        return systemService.getRunningVersions();
    }

    // TODO - Remove when everything points to the v2 api. This is currently used in v1/api versions endpoint when running in compose
    @GetMapping("appsByNode")
    public Map<String, List<AppName>> getAppsByNode() {
        return systemService.getNodeInfo();
    }
}
