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
package org.deltafi.core.converters;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.util.UtilService;
import org.deltafi.core.validation.ActionConfigurationValidator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class FlowPlanPropertyHelperTest {

    FlowPlanPropertyHelper propertyHelper = new FlowPlanPropertyHelper(List.of());

    @Test
    void resolve_populatedList() {
        String toResolve = "${value}";

        // spaces around each value should be removed
        Variable variable = Variable.builder().name("value").value("a,  b  c ,   d").dataType(VariableDataType.LIST).build();

        Object result = executeResolvePrimitive(toResolve, List.of(variable));

        assertThat(result).isInstanceOf(List.class);
        List<String> castedResult = (List<String>) result;
        assertThat(castedResult).hasSize(3).contains("a", "b  c", "d");
    }

    @Test
    void resolve_nullRequiredList() {
        String toResolve = "${value}";
        Variable variable = Variable.builder().name("value").value(null).defaultValue(null).required(true).dataType(VariableDataType.LIST).build();

        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(List.of(variable));
        Object result = flowPlanPropertyHelper.resolveObject(toResolve, "");

        // when the list is required the null value will cause an error to be tracked in the helper and return the original unresolved value
        assertThat(result).isEqualTo(toResolve);
        assertThat(flowPlanPropertyHelper.getErrors()).hasSize(1);
    }

    @Test
    void resolve_nullOptionalList() {
        String toResolve = "${value}";
        Variable variable = Variable.builder().name("value").value(null).defaultValue(null).required(false).dataType(VariableDataType.LIST).build();

        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(List.of(variable));
        Object result = flowPlanPropertyHelper.resolveObject(toResolve, "");

        // when the list is optional the null value will be returned and no errors will be added
        assertThat(result).isNull();
        assertThat(flowPlanPropertyHelper.getErrors()).isEmpty();
    }

    @Test
    void resolve_emptyRequiredList() {
        String toResolve = "${value}";
        Variable variable = Variable.builder().name("value").value("").defaultValue(null).required(true).dataType(VariableDataType.LIST).build();
        Object result = executeResolvePrimitive(toResolve, List.of(variable));

        assertThat(result).isInstanceOf(List.class);
        List<String> castedResult = (List<String>) result;
        assertThat(castedResult).isEmpty();
    }

    @Test
    void resolve_emptyOptionalList() {
        String toResolve = "${value}";
        Variable variable = Variable.builder().name("value").value("").defaultValue(null).required(false).dataType(VariableDataType.LIST).build();
        Object result = executeResolvePrimitive(toResolve, List.of(variable));

        assertThat(result).isInstanceOf(List.class);
        List<String> castedResult = (List<String>) result;
        assertThat(castedResult).isEmpty();
    }

    @Test
    void resolve_listWhitespaceHandling() {
        String toResolve = "${value}";
        Variable variable = Variable.builder().name("value").value(" [ ] ").defaultValue(null).required(false).dataType(VariableDataType.LIST).build();

        Object result = executeResolvePrimitive(toResolve, List.of(variable));
        assertThat(result).isInstanceOf(List.class);
        List<String> castedResult = (List<String>) result;
        assertThat(castedResult).isEmpty();

        variable = Variable.builder().name("value").value(" [a,b]").dataType(VariableDataType.LIST).build();
        result = executeResolvePrimitive(toResolve, List.of(variable));
        assertThat(result).isInstanceOf(List.class);
        castedResult = (List<String>) result;
        assertThat(castedResult).containsExactly("a", "b");
    }

    @Test
    void resolve_populatedMap() {
        String toResolve = "${value}";

        // spaces around the keys and values should be removed
        Variable variable = Variable.builder().name("value").value(" one  key    :  first  , two:  second value  ").dataType(VariableDataType.MAP).build();

        Object result = executeResolvePrimitive(toResolve, List.of(variable));

        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> castedResult = (Map<String, Object>) result;
        assertThat(castedResult).hasSize(2).containsEntry("one  key", "first").containsEntry("two", "second value");
    }

    @Test
    void resolve_nullRequiredMap() {
        String toResolve = "${value}";
        Variable variable = Variable.builder().name("value").value(null).defaultValue(null).required(true).dataType(VariableDataType.MAP).build();

        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(List.of(variable));
        Object result = flowPlanPropertyHelper.resolveObject(toResolve, "");

        // when the map is required the null value will cause an error to be tracked in the helper and return the original unresolved value
        assertThat(result).isEqualTo(toResolve);
        assertThat(flowPlanPropertyHelper.getErrors()).hasSize(1);
    }

    @Test
    void resolve_nullOptionalMap() {
        String toResolve = "${value}";
        Variable variable = Variable.builder().name("value").value(null).defaultValue(null).required(false).dataType(VariableDataType.MAP).build();

        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(List.of(variable));
        Object result = flowPlanPropertyHelper.resolveObject(toResolve, "");

        // when the map is optional the null value will be returned and no errors will be added
        assertThat(result).isNull();
        assertThat(flowPlanPropertyHelper.getErrors()).isEmpty();
    }

    @Test
    void resolve_emptyRequiredMap() {
        String toResolve = "${value}";
        Variable variable = Variable.builder().name("value").value("").defaultValue(null).required(true).dataType(VariableDataType.MAP).build();
        Object result = executeResolvePrimitive(toResolve, List.of(variable));

        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> castedResult = (Map<String, Object>) result;
        assertThat(castedResult).isEmpty();
    }

    @Test
    void resolve_emptyOptionalMap() {
        String toResolve = "${value}";
        Variable variable = Variable.builder().name("value").value("").defaultValue(null).required(false).dataType(VariableDataType.MAP).build();
        Object result = executeResolvePrimitive(toResolve, List.of(variable));

        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> castedResult = (Map<String, Object>) result;
        assertThat(castedResult).isEmpty();
    }

    @Test
    void test_replaceListPlaceholders() {
        Variable setValue = Variable.builder().name("value").value("setIt").dataType(VariableDataType.STRING).build();
        Variable optionalValue = Variable.builder().name("unsetOptional").value(null).defaultValue(null).dataType(VariableDataType.STRING).build();
        Variable expandMe = Variable.builder().name("expandMe").value("x, y, z").dataType(VariableDataType.LIST).build();

        List<Object> listToProcess = new ArrayList<>();

        Map<String, String> subObject = new HashMap<>();
        subObject.put("key", "${value}");

        List<String> subList = List.of("a", "${value}", "c");

        listToProcess.add(subObject);
        listToProcess.add(subList);
        listToProcess.add(null);
        listToProcess.add("${unsetOptional}");
        listToProcess.add("${expandMe}");

        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(List.of(setValue, optionalValue, expandMe));
        Object result = flowPlanPropertyHelper.replaceListPlaceholders(listToProcess, "");

        assertThat(result).isInstanceOf(List.class);
        List<Object> castedResult = (List<Object>) result;

        assertThat(castedResult).hasSize(3);

        assertThat(castedResult.get(0)).isInstanceOf(Map.class);
        assertThat((Map<String, Object>) castedResult.get(0)).containsEntry("key", "setIt");
        assertThat(castedResult.get(1)).isInstanceOf(List.class);
        assertThat((List<String>) castedResult.get(1)).contains("a", "setIt", "c");
        assertThat(castedResult.get(2)).isInstanceOf(List.class);
        assertThat((List<String>) castedResult.get(2)).contains("x", "y", "z");
    }

    @Test
    void test_replaceMapPlaceholders() {
        Variable setValue = Variable.builder().name("value").value("setIt").dataType(VariableDataType.STRING).build();
        Variable optionalValue = Variable.builder().name("unsetOptional").value(null).defaultValue(null).dataType(VariableDataType.STRING).build();
        Variable expandMe = Variable.builder().name("expandMe").value("x, y, z").dataType(VariableDataType.LIST).build();

        Map<String, Object> mapToProcess = new HashMap<>();

        Map<String, String> subObject = new HashMap<>();
        subObject.put("key", "${value}");

        List<String> subList = List.of("a", "${value}", "c");

        mapToProcess.put("${value}", subObject);
        mapToProcess.put("subList", subList);
        mapToProcess.put("pruneNullValue", null);
        mapToProcess.put(null, "pruneNullKey");
        mapToProcess.put("pruneEmptyValue", "");
        mapToProcess.put("pruneOptional", "${unsetOptional}");
        mapToProcess.put("expanded", "${expandMe}");

        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(List.of(setValue, optionalValue, expandMe));
        Object result = flowPlanPropertyHelper.replaceMapPlaceholders(mapToProcess, "");

        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> castedResult = (Map<String, Object>) result;

        assertThat(castedResult).hasSize(3);

        assertThat(castedResult.get("setIt")).isInstanceOf(Map.class);
        assertThat((Map<String, Object>) castedResult.get("setIt")).hasSize(1).containsEntry("key", "setIt");
        assertThat(castedResult.get("subList")).isInstanceOf(List.class);
        assertThat((List<String>) castedResult.get("subList")).hasSize(3).contains("a", "setIt", "c");
        assertThat(castedResult.get("expanded")).isInstanceOf(List.class);
        assertThat((List<String>) castedResult.get("expanded")).hasSize(3).contains("x", "y", "z");
    }

    @Test
    void testValidParameterSubstitution() {
        ActionConfigurationValidator validator = new ActionConfigurationValidator(null);

        Map<String, Object> parameters = UtilService.readResource("config-test/complex-parameter-values.json", Map.class);

        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(variables());
        Map<String, Object> mappedParameters = flowPlanPropertyHelper.replaceMapPlaceholders(parameters, "");

        ActionConfiguration actionConfiguration = new ActionConfiguration(null, ActionType.EGRESS, null);
        actionConfiguration.setInternalParameters(mappedParameters);

        ActionDescriptor egressActionDescriptor = UtilService.egressActionDescriptor("config-test/complex-parameter-action-descriptor.json");
        List<FlowConfigError> errors = validator.validateAgainstSchema(egressActionDescriptor, actionConfiguration);
        assertThat(errors).isEmpty();
    }

    @Test
    void testDelegateToMaskedHelper() {
        Variable notMasked = UtilService.buildVariable("notMasked", "plainValue", null);
        Variable masked = UtilService.buildVariable("masked", "maskedValue", null);
        masked.setMasked(true);
        List<Variable> variables = List.of(notMasked, masked);
        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(variables);
        ActionConfiguration toPopulate = new ActionConfiguration("", ActionType.TRANSFORM, ActionType.TRANSFORM.name());
        ActionConfiguration template = new ActionConfiguration("Loader", ActionType.TRANSFORM, ActionType.TRANSFORM.name());
        template.setParameters(Map.of("notMasked", "${notMasked}", "masked", "${masked}"));
        flowPlanPropertyHelper.replaceCommonActionPlaceholders(toPopulate, template);

        assertThat(toPopulate.getParameters()).hasSize(2).containsEntry("notMasked", "plainValue").containsEntry("masked", Variable.MASK_STRING);
        assertThat(toPopulate.getInternalParameters()).hasSize(2).containsEntry("notMasked", "plainValue").containsEntry("masked", "maskedValue");
    }

    @Test
    void resolve_multiVariables() {
        String toResolve = "[mixedValueTypes-${value}-${value2}]";
        Variable variableString = Variable.builder().name("value").value("myvalue").defaultValue(null).required(true).dataType(VariableDataType.STRING).build();
        Variable variableNumber = Variable.builder().name("value2").value("3").defaultValue(null).required(true).dataType(VariableDataType.NUMBER).build();
        Object result = executeResolvePrimitive(toResolve, List.of(variableString, variableNumber));

        assertThat(result).isInstanceOf(String.class).isEqualTo("[mixedValueTypes-myvalue-3]");
    }

    @Test
    void resolve_doNotRecurseOnVariableValues() {
        String toResolve = "${value}";
        Variable variableString = Variable.builder().name("value").value("${someOtherPlaceholder}").defaultValue(null).required(true).dataType(VariableDataType.STRING).build();
        Object result = executeResolvePrimitive(toResolve, List.of(variableString));

        assertThat(result).isInstanceOf(String.class).isEqualTo("${someOtherPlaceholder}");
    }

    @Test
    void testSimpleDefaultValues() {
        // Generate schema from SimpleConfig class
        Map<String, Object> schema = UtilService.generateSchema(SimpleConfig.class);

        // Empty parameters map
        Map<String, Object> parameters = new HashMap<>();

        // Apply defaults
        propertyHelper.setDefaultValues(schema, parameters);

        assertThat(parameters).containsEntry("maxDelayMS", 0).containsEntry("minDelayMS", 0);
    }

    @Test
    void testExistingValuesAreNotOverwritten() {
        // Generate schema from UserProfile class
        Map<String, Object> schema = UtilService.generateSchema(UserProfile.class);

        // Parameters map with existing value
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("name", "John Doe");

        propertyHelper.setDefaultValues(schema, parameters);
        assertThat(parameters).containsEntry("name", "John Doe").containsEntry("age", 30);
    }

    @Test
    void testNestedObjectDefaultValues() {
        Map<String, Object> schema = UtilService.generateSchema(PersonWithAddress.class);
        Map<String, Object> parameters = new HashMap<>();

        propertyHelper.setDefaultValues(schema, parameters);

        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) parameters.get("address");
        assertThat(address).isNotNull().containsEntry("city", "New York").containsEntry("zip", "10001");
    }

    @Test
    void testPartiallyPopulatedNestedObject() {
        Map<String, Object> schema = UtilService.generateSchema(PersonWithContact.class);

        Map<String, Object> parameters = new HashMap<>();
        Map<String, Object> existingContact = new HashMap<>();
        existingContact.put("email", "john@example.com");
        parameters.put("contact", existingContact);

        propertyHelper.setDefaultValues(schema, parameters);

        @SuppressWarnings("unchecked")
        Map<String, Object> contact = (Map<String, Object>) parameters.get("contact");
        // kept the custom email parameter but filled in missing phone number
        assertThat(contact).isNotNull().containsEntry("email", "john@example.com").containsEntry("phone", "555-1234");
    }

    @Test
    void testArrayOfObjects() {
        Map<String, Object> schema = UtilService.generateSchema(ItemContainer.class);

        Map<String, Object> parameters = new HashMap<>();
        List<Map<String, Object>> existingItems = new ArrayList<>();

        Map<String, Object> item1 = new HashMap<>();
        item1.put("name", "First Item");
        existingItems.add(item1);

        Map<String, Object> item2 = new HashMap<>();
        item2.put("value", 42);
        existingItems.add(item2);

        parameters.put("items", existingItems);

        propertyHelper.setDefaultValues(schema, parameters);

        // Verify array items got defaults for missing properties
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) parameters.get("items");

        assertThat(items).hasSize(2);
        Map<String, Object> firstItem = items.getFirst();
        Map<String, Object> secondItem = items.getLast();

        // first item gets name from the parameters but fills in the default value of 2
        assertThat(firstItem).isNotNull().containsEntry("name", "First Item").containsEntry("value", 2);
        // second item gets the default name but keeps the value from the parameter map
        assertThat(secondItem).isNotNull().containsEntry("name", "Untitled").containsEntry("value", 42);
    }

    @Test
    void testMixedComplexTypes() {
        Map<String, Object> schema = UtilService.generateSchema(UserWithProfile.class);

        Map<String, Object> parameters = new HashMap<>();
        Map<String, Object> user = new HashMap<>();
        user.put("name", "John");
        parameters.put("user", user);

        propertyHelper.setDefaultValues(schema, parameters);

        @SuppressWarnings("unchecked")
        Map<String, Object> resultUser = (Map<String, Object>) parameters.get("user");
        assertThat(resultUser).isNotNull().containsEntry("name", "John");

        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) resultUser.get("profile");
        // default profile of light should be added
        assertThat(profile).isNotNull().containsEntry("theme", "light");
    }

    @Test
    void testArrayDefaultValues() {
        // Generate schema from TagContainer class
        Map<String, Object> schema = UtilService.generateSchema(TagContainer.class);

        // Empty parameters map
        Map<String, Object> parameters = new HashMap<>();

        // Apply defaults
        propertyHelper.setDefaultValues(schema, parameters);

        // Verify array default was applied
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) parameters.get("tags");
        assertThat(tags).hasSize(2).contains("tag1", "tag2");
    }

    @Test
    void testIgnoreNullDefaultValues() {
        Map<String, Object> schema = UtilService.generateSchema(User.class);

        // set the name default value to null to mimic the schema sent back by the python action kit
        ((Map<String,Object>)((Map<String,Object>)schema.get("properties")).get("name")).put("default", null);
        Map<String, Object> parameters = new HashMap<>();

        propertyHelper.setDefaultValues(schema, parameters);

        // verify name was not added to the parameters b/c it does not have a default value set
        assertThat(parameters).hasSize(1).containsKey("profile");
    }

    Object executeResolvePrimitive(Object object, List<Variable> variables) {
        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(variables);

        return flowPlanPropertyHelper.resolveObject(object, "");
    }

    List<Variable> variables() {
        return List.of(
                Variable.builder().name("enrichments").defaultValue("{'test1':'test1','test2':'test2'}").dataType(VariableDataType.MAP).build(),
                Variable.builder().name("some.optional.list").value(null).required(false).dataType(VariableDataType.LIST).build(),
                Variable.builder().name("some.required.list").value("").required(true).dataType(VariableDataType.LIST).build(),
                Variable.builder().name("egressUrl").defaultValue("http://deltafi-egress-sink-service").dataType(VariableDataType.STRING).build(),
                Variable.builder().name("retryCount").defaultValue("150").dataType(VariableDataType.NUMBER).build(),
                Variable.builder().name("subKeyValue").required(false).dataType(VariableDataType.STRING).build(),
                Variable.builder().name("listValue").value("").required(false).dataType(VariableDataType.LIST).build(),
                Variable.builder().name("boolVal").value(" True ").dataType(VariableDataType.BOOLEAN).build());
    }

    // Sample classes with Jackson annotations for schema generation
    @SuppressWarnings("unused")
    static class SimpleConfig {
        @JsonProperty(defaultValue = "0")
        @JsonPropertyDescription("Maximum time to delay processing in ms. Set equal to minDelayMS for a fixed delay.")
        private int maxDelayMS;

        @JsonProperty(defaultValue = "0")
        @JsonPropertyDescription("Minimum time to delay processing in ms. Set equal to maxDelayMS for a fixed delay.")
        private int minDelayMS;
    }

    @SuppressWarnings("unused")
    static class UserProfile {
        @JsonProperty(defaultValue = "default-name")
        @JsonPropertyDescription("User's full name")
        private String name;

        @JsonProperty(defaultValue = "30")
        @JsonPropertyDescription("User's age")
        private int age;
    }

    @SuppressWarnings("unused")
    static class Address {
        @JsonProperty(defaultValue = "New York")
        @JsonPropertyDescription("City name")
        private String city;

        @JsonProperty(defaultValue = "10001")
        @JsonPropertyDescription("ZIP code")
        private String zip;
    }

    @SuppressWarnings("unused")
    static class PersonWithAddress {
        @JsonProperty
        @JsonPropertyDescription("Person's address information")
        private Address address;
    }

    @SuppressWarnings("unused")
    static class Contact {
        @JsonProperty(defaultValue = "default@example.com")
        @JsonPropertyDescription("Email address")
        private String email;

        @JsonProperty(defaultValue = "555-1234")
        @JsonPropertyDescription("Phone number")
        private String phone;
    }

    @SuppressWarnings("unused")
    static class PersonWithContact {
        @JsonProperty
        @JsonPropertyDescription("Contact information")
        private Contact contact;
    }

    @SuppressWarnings("unused")
    static class Item {
        @JsonProperty(defaultValue = "Untitled")
        @JsonPropertyDescription("Item name")
        private String name;

        @JsonProperty(defaultValue = "2")
        @JsonPropertyDescription("Item value")
        private int value;
    }

    @SuppressWarnings("unused")
    static class ItemContainer {
        @JsonProperty
        @JsonPropertyDescription("List of items")
        private List<Item> items;
    }

    @SuppressWarnings("unused")
    static class Profile {
        @JsonProperty(defaultValue = "light")
        @JsonPropertyDescription("User interface theme")
        private String theme;
    }

    @SuppressWarnings("unused")
    static class User {
        @JsonProperty(defaultValue = "Anonymous")
        @JsonPropertyDescription("User's name")
        private String name;

        @JsonProperty
        @JsonPropertyDescription("User's profile settings")
        private Profile profile;
    }

    @SuppressWarnings("unused")
    static class UserWithProfile {
        @JsonProperty
        @JsonPropertyDescription("User information")
        private User user;
    }

    @SuppressWarnings("unused")
    static class TagContainer {
        @JsonProperty(defaultValue = "[\"tag1\", \"tag2\"]")
        @JsonPropertyDescription("List of tags")
        private List<String> tags;
    }
}