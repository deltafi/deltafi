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
package org.deltafi.core.rest;

import lombok.AllArgsConstructor;
import org.deltafi.core.exceptions.StorageCheckException;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.SystemService;
import org.deltafi.core.types.NodeMetrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("metrics")
@AllArgsConstructor
public class MetricsRest {

    private final SystemService systemService;

    @NeedsPermission.MetricsView
    @GetMapping("system/content")
    public ContentMetric contentMetrics() throws StorageCheckException {
        return new ContentMetric(systemService.contentNodeMetrics());
    }

    @NeedsPermission.MetricsView
    @GetMapping("system/nodes")
    public Nodes nodeAppsAndMetrics() {
        return new Nodes(systemService.nodeAppsAndMetrics());
    }

    public record ContentMetric(Map<String, Long> content, OffsetDateTime timestamp) {
        public ContentMetric(Map<String, Long> content) {
            this(content, OffsetDateTime.now());
        }
    }

    public record Nodes(List<NodeMetrics> nodes, OffsetDateTime timestamp) {
        public Nodes(List<NodeMetrics> nodes) {
            this(nodes, OffsetDateTime.now());
        }
    }
}
