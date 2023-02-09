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
package org.deltafi.core.datafetchers;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import org.deltafi.common.types.PropertySet;
import org.deltafi.core.configuration.DeltaFiProperties;

import java.util.List;
import java.util.Map;


public class PropertiesDatafetcherTestHelper {

    private static final String UPDATE_PROPERTIES = "mutation($updates: [PropertyUpdate]!) {updateProperties(updates: $updates)}";
    private static final String GET_PROPERTIES = "query { getDeltaFiProperties { systemName requeueSeconds coreServiceThreads scheduledServiceThreads checks {actionQueueSizeThreshold contentStoragePercentThreshold} delete {frequency ageOffDays policyBatchSize} ingress {enabled diskSpaceRequirementInMb} plugins {imageRepositoryBase imagePullSecret} metrics {enabled} setProperties }}";
    private static final String UNSET_PROPERTY = "mutation($ids: [PropertyId]!) { removePropertyOverrides(propertyIds: $ids)}";
    private static final String ADD_EXTERNAL_LINK = "mutation($link: LinkInput!) {saveExternalLink(link: $link)}";
    private static final String ADD_DELTA_FILE_LINK = "mutation($link: LinkInput!) {saveDeltaFileLink(link: $link)}";

    public static List<PropertySet> getPropertySets(DgsQueryExecutor dgsQueryExecutor) {
        String query = "query {getPropertySets {id displayName description properties {key value hidden editable refreshable}}}";
        return dgsQueryExecutor.executeAndExtractJsonPath(query, "data.getPropertySets");
    }

    public static boolean updateProperties(DgsQueryExecutor dgsQueryExecutor) {
        Map<String, Object> updatesMap = Map.of("updates", List.of(Map.of("key", "scheduledServiceThreads", "value", "3", "propertySetId", "test-plugin")));
        return dgsQueryExecutor.executeAndExtractJsonPath(UPDATE_PROPERTIES, "data.updateProperties", updatesMap);
    }

    public static DeltaFiProperties getDeltaFiProperties(DgsQueryExecutor dgsQueryExecutor) {
        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(GET_PROPERTIES, "data.getDeltaFiProperties", DeltaFiProperties.class);
    }

    public static boolean removePropertyOverrides(DgsQueryExecutor dgsQueryExecutor) {
        Map<String, Object> updatesMap = Map.of("ids", List.of(Map.of("propertySetId", "deltafi-common", "key", "scheduledServiceThreads")));
        return dgsQueryExecutor.executeAndExtractJsonPath(UNSET_PROPERTY, "data.removePropertyOverrides", updatesMap);
    }

    public static boolean addExternalLink(DgsQueryExecutor dgsQueryExecutor) {
        return dgsQueryExecutor.executeAndExtractJsonPath(ADD_EXTERNAL_LINK, "data.saveExternalLink", linkArgs());
    }

    public static boolean addDeltaFileLink(DgsQueryExecutor dgsQueryExecutor) {
        return dgsQueryExecutor.executeAndExtractJsonPath(ADD_DELTA_FILE_LINK, "data.saveDeltaFileLink", linkArgs());
    }

    public static boolean removeExternalLink(DgsQueryExecutor dgsQueryExecutor) {
        return dgsQueryExecutor.executeAndExtractJsonPath("mutation {removeExternalLink(linkName: \"some link\")}", "data.removeExternalLink", linkArgs());
    }

    public static boolean removeDeltaFileLink(DgsQueryExecutor dgsQueryExecutor) {
        return dgsQueryExecutor.executeAndExtractJsonPath("mutation {removeDeltaFileLink(linkName: \"some link\")}", "data.removeDeltaFileLink", linkArgs());
    }

    private static Map<String, Object> linkArgs() {
        return Map.of("link", Map.of("name", "some link", "url", "www.some.place", "description", "some place described"));
    }

}