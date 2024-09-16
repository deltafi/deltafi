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
package org.deltafi.core.datafetchers;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import org.deltafi.core.types.PropertySet;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.configuration.ui.Link;
import org.deltafi.core.configuration.ui.Link.LinkType;
import org.intellij.lang.annotations.Language;

import java.util.List;
import java.util.Map;
import java.util.UUID;


public class PropertiesDatafetcherTestHelper {

    @Language("GraphQL")
    private static final String UPDATE_PROPERTIES = "mutation($updates: [KeyValueInput]!) {updateProperties(updates: $updates)}";
    @Language("GraphQL")
    private static final String GET_PROPERTIES = "query { getDeltaFiProperties { systemName requeueDuration coreServiceThreads scheduledServiceThreads checkActionQueueSizeThreshold checkContentStoragePercentThreshold deleteFrequency ageOffDays deletePolicyBatchSize ingressEnabled ingressDiskSpaceRequirementInMb pluginImageRepositoryBase pluginImagePullSecret  metricsEnabled}}";
    @Language("GraphQL")
    private static final String UNSET_PROPERTY = "mutation($ids: [String]!) { removePropertyOverrides(propertyNames: $ids)}";
    @Language("GraphQL")
    private static final String SAVE_LINK = "mutation($link: LinkInput!) { saveLink(link: $link) {id name url description linkType }}";

    public static List<PropertySet> getPropertySets(DgsQueryExecutor dgsQueryExecutor) {
        @Language("GraphQL") String query = "query {getPropertySets {id displayName description properties {key value refreshable}}}";
        return dgsQueryExecutor.executeAndExtractJsonPath(query, "data.getPropertySets");
    }

    public static boolean updateProperties(DgsQueryExecutor dgsQueryExecutor) {
        Map<String, Object> updatesMap = Map.of("updates", List.of(Map.of("key", "scheduledServiceThreads", "value", "3")));
        return dgsQueryExecutor.executeAndExtractJsonPath(UPDATE_PROPERTIES, "data.updateProperties", updatesMap);
    }

    public static DeltaFiProperties getDeltaFiProperties(DgsQueryExecutor dgsQueryExecutor) {
        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(GET_PROPERTIES, "data.getDeltaFiProperties", DeltaFiProperties.class);
    }

    public static boolean removePropertyOverrides(DgsQueryExecutor dgsQueryExecutor) {
        Map<String, Object> updatesMap = Map.of("ids", List.of("scheduledServiceThreads"));
        return dgsQueryExecutor.executeAndExtractJsonPath(UNSET_PROPERTY, "data.removePropertyOverrides", updatesMap);
    }

    public static Link saveLink(DgsQueryExecutor dgsQueryExecutor) {
        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(SAVE_LINK, "data.saveLink", linkArgs(LinkType.EXTERNAL.name()), Link.class);
    }

    public static boolean removeLink(DgsQueryExecutor dgsQueryExecutor, UUID linkId) {
        return dgsQueryExecutor.executeAndExtractJsonPath("mutation {removeLink(id: \"" + linkId + "\")}", "data.removeLink");
    }

    private static Map<String, Object> linkArgs(String linkType) {
        return Map.of("link", Map.of("name", "some link", "url", "www.some.place", "description", "some place described", "linkType", linkType));
    }

}