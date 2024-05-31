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
package org.deltafi.core.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import org.deltafi.common.queue.jackey.ValkeyKeyedBlockingQueue;
import org.deltafi.core.exceptions.SystemStatusException;
import org.deltafi.core.types.AppInfo;
import org.deltafi.core.types.AppName;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@AllArgsConstructor
public class SystemService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final PlatformService platformService;
    private final ValkeyKeyedBlockingQueue valkeyKeyedBlockingQueue;

    public Status systemStatus() {
        String status = Optional.ofNullable(valkeyKeyedBlockingQueue.getByKey("org.deltafi.monitor.status"))
                .orElseThrow(this::missingStatus);

        try {
            ObjectNode statusJson = MAPPER.readValue(status, ObjectNode.class);
            return new Status(statusJson);
        } catch (IOException ioException) {
            throw new SystemStatusException("Unable to parse the system status");
        }
    }

    public Map<String, List<AppName>> getNodeInfo() {
        return platformService.getNodeInfo();
    }

    public Versions getRunningVersions() {
        return new Versions(platformService.getRunningVersions());
    }

    public record Versions(List<AppInfo> versions, OffsetDateTime timestamp) {
        public Versions(List<AppInfo> versions) {
            this(versions, OffsetDateTime.now());
        }
    }

    public record Status(ObjectNode status, OffsetDateTime timestamp) {
        public Status(ObjectNode status) {
            this(status, OffsetDateTime.now());
        }
    }

    private SystemStatusException missingStatus() {
        return new SystemStatusException("Received empty response from valkey");
    }
}
