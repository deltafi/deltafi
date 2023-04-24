/**
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
package org.deltafi.common.types;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeltaFileMessage {
    String sourceFilename;
    String ingressFlow;
    Map<String, String> metadata;
    List<Content> contentList;
    List<Domain> domains;
    List<Enrichment> enrichment;

    public Map<String, Domain> domainMap() {
        return getDomains().stream().collect(Collectors.toMap(Domain::getName, Function.identity()));
    }

    public Map<String, Enrichment> enrichmentMap() {
        return getEnrichment().stream().collect(Collectors.toMap(Enrichment::getName, Function.identity()));
    }

    public SourceInfo buildSourceInfo() {
        return SourceInfo.builder().filename(sourceFilename).flow(ingressFlow).metadata(metadata).build();
    }
}
