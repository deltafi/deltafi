/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.services;

import org.deltafi.core.types.AppInfo;
import org.deltafi.core.types.AppName;

import java.util.List;
import java.util.Map;

public interface PlatformService {

    /**
     * Get a map of node names to the list of applications
     * running on that node
     * @return node to app list map
     */
    Map<String, List<AppName>> appsByNode();

    /**
     * Find all running applications with their versions
     * @return running application info
     */
    List<AppInfo> getRunningVersions();

    /**
     * Return the name of the node where content is stored
     * @return node name
     */
    String contentNodeName();
}
