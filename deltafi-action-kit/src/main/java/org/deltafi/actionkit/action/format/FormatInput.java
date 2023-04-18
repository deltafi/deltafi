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
package org.deltafi.actionkit.action.format;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.deltafi.actionkit.exception.ExpectedContentException;
import org.deltafi.actionkit.exception.MissingMetadataException;
import org.deltafi.actionkit.exception.MissingSourceMetadataException;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.Domain;
import org.deltafi.common.types.Enrichment;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Builder
@Data
public class FormatInput {
    String sourceFilename;
    String ingressFlow;
    Map<String, String> sourceMetadata;
    List<Content> contentList;
    Map<String, String> metadata;
    Map<String, Domain> domains;
    Map<String, Enrichment> enrichment;

    /**
     * Returns the value of the source metadata for the given key.
     * @param key the key for the metadata.
     * @return the value of the metadata for the given key.
     * @throws MissingSourceMetadataException if the key is not found in the source metadata map.
     */
    @SuppressWarnings("unused")
    public String sourceMetadata(String key) {
        if (sourceMetadata.containsKey(key)) {
            return sourceMetadata.get(key);
        } else {
            throw new MissingSourceMetadataException(key);
        }
    }

    /**
     * Returns the value of the source metadata for the given key or a default value if the key is not found.
     * @param key the key for the metadata.
     * @param defaultValue the default value to return if the key is not found.
     * @return the value of the metadata for the given key or the default value if the key is not found.
     */
    @SuppressWarnings("unused")
    public String sourceMetadata(String key, String defaultValue) {
        return sourceMetadata.getOrDefault(key, defaultValue);
    }

    /**
     * Returns the Domain object for the given domain name.
     * @param domainName the name of the domain.
     * @return the Domain object for the given domain name.
     */
    public Domain domain(String domainName) {
        return domains.get(domainName);
    }

    public Enrichment enrichment(String enrichmentName) {
        return enrichment.get(enrichmentName);
    }

    /**
     * Returns the value of the last action's metadata for the given key.
     * @param key the key for the metadata.
     * @return the value of the metadata for the given key.
     * @throws MissingMetadataException if the key is not found in the metadata map.
     */
    public String metadata(String key) {
        if (metadata.containsKey(key)) {
            return metadata.get(key);
        } else {
            throw new MissingMetadataException(key);
        }
    }

    /**
     * Returns the value of the last action's  metadata for the given key or a default value if the key is not found.
     * @param key the key for the metadata.
     * @param defaultValue the default value to return if the key is not found.
     * @return the value of the metadata for the given key or the default value if the key is not found.
     */
    public String metadata(String key, String defaultValue) {
        return metadata.getOrDefault(key, defaultValue);
    }

    /**
     * Returns true if the ContentList has any content, false otherwise.
     * @return true if the ContentList has any content, false otherwise.
     */
    public boolean hasContent() {
        return !contentList.isEmpty();
    }

    /**
     * Returns the Content object at the given index.
     * @param index the index of the Content object to retrieve.
     * @return the Content object at the given index.
     */
    public Content contentAt(int index) {
        return contentList.get(index);
    }

    /**
     * Returns the first Content object in the ContentList.
     * @return the first Content object in the ContentList.
     * @throws ExpectedContentException if the ContentList is empty.
     */
    @SuppressWarnings("unused")
    public Content firstContent() {
        if (!hasContent()) {
            throw new ExpectedContentException();
        }
        return contentAt(0);
    }
}
