/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.domain.datafetchers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import graphql.ExecutionResult;
import org.deltafi.core.config.server.constants.PropertyConstants;
import org.deltafi.core.domain.api.types.PropertySet;

import java.util.List;
import java.util.Map;

import static org.deltafi.core.domain.Util.getPropertySetWithProperty;


public class PropertiesDatafetcherTestHelper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String REMOVE_TEMPLATE = "mutation {removePluginPropertySet(propertySetId: \"%s\")}";
    private static final String ADD_PROPERTY_SET = "mutation($propertySet: PropertySetInput!) {addPluginPropertySet(propertySet: $propertySet)}";
    private static final String UPDATE_PROPERTIES = "mutation($updates: [PropertyUpdate]!) {updateProperties(updates: $updates)}";

    public static List<PropertySet> getPropertySets(DgsQueryExecutor dgsQueryExecutor) {
        String query = "query {getPropertySets {id displayName description properties {key value hidden editable refreshable}}}";
        return dgsQueryExecutor.executeAndExtractJsonPath(query, "data.getPropertySets");
    }

    public static int updateProperties(DgsQueryExecutor dgsQueryExecutor) {
        Map<String, Object> updatesMap = Map.of("updates", List.of(Map.of("key", "editable", "value", "changedit", "propertySetId", "test-plugin")));
        return dgsQueryExecutor.executeAndExtractJsonPath(UPDATE_PROPERTIES, "data.updateProperties", updatesMap);
    }

    public static boolean addPluginPropertySet_valid(DgsQueryExecutor dgsQueryExecutor) {
        PropertySet propSet = getPropertySetWithProperty("a");
        Map<String, Object> properSetMap = Map.of("propertySet", OBJECT_MAPPER.convertValue(propSet, Map.class));
        return dgsQueryExecutor.executeAndExtractJsonPath(ADD_PROPERTY_SET, "data.addPluginPropertySet", properSetMap);
    }

    public static ExecutionResult addPluginPropertySet_commonFails(DgsQueryExecutor dgsQueryExecutor) {
        PropertySet propSet = getPropertySetWithProperty(PropertyConstants.DELTAFI_PROPERTY_SET);
        Map<String, Object> properSetMap = Map.of("propertySet", OBJECT_MAPPER.convertValue(propSet, Map.class));
        return dgsQueryExecutor.execute(ADD_PROPERTY_SET, properSetMap);
    }

    public static boolean removePluginPropertySet(DgsQueryExecutor dgsQueryExecutor) {
        String removeMutation = String.format(REMOVE_TEMPLATE, "test-plugin");
        return dgsQueryExecutor.executeAndExtractJsonPath(removeMutation, "data.removePluginPropertySet");
    }

    public static ExecutionResult removePluginPropertySet_commonFails(DgsQueryExecutor dgsQueryExecutor) {
        String removeMutation = String.format(REMOVE_TEMPLATE, PropertyConstants.DELTAFI_PROPERTY_SET);
        return dgsQueryExecutor.execute(removeMutation);
    }
}