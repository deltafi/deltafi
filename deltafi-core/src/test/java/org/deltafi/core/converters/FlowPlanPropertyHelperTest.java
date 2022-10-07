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
package org.deltafi.core.converters;

import org.deltafi.common.types.ActionDescriptor;
import org.deltafi.common.types.Variable;
import org.deltafi.common.types.VariableDataType;
import org.deltafi.core.Util;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.EgressActionConfiguration;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.validation.SchemaComplianceValidator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class FlowPlanPropertyHelperTest {

    @Test
    void resolve_populatedList() {
        String toResolve = "${value}";

        // spaces around each value should be removed
        Variable variable = Variable.newBuilder().name("value").value("a,  b  c ,   d").dataType(VariableDataType.LIST).build();

        Object result = executeResolvePrimitive(toResolve, List.of(variable));

        assertThat(result).isInstanceOf(List.class);
        List<String> castedResult = (List<String>) result;
        assertThat(castedResult).hasSize(3).contains("a", "b  c", "d");
    }

    @Test
    void resolve_nullRequiredList() {
        String toResolve = "${value}";
        Variable variable = Variable.newBuilder().name("value").value(null).defaultValue(null).required(true).dataType(VariableDataType.LIST).build();

        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(List.of(variable), "action");
        Object result = flowPlanPropertyHelper.resolveObject(toResolve, "");

        // when the list is required the null value will cause an error to be tracked in the helper and return the original unresolved value
        assertThat(result).isEqualTo(toResolve);
        assertThat(flowPlanPropertyHelper.getErrors()).hasSize(1);
    }

    @Test
    void resolve_nullOptionalList() {
        String toResolve = "${value}";
        Variable variable = Variable.newBuilder().name("value").value(null).defaultValue(null).required(false).dataType(VariableDataType.LIST).build();

        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(List.of(variable), "action");
        Object result = flowPlanPropertyHelper.resolveObject(toResolve, "");

        // when the list is optional the null value will be returned and no errors will be added
        assertThat(result).isNull();
        assertThat(flowPlanPropertyHelper.getErrors()).isEmpty();
    }

    @Test
    void resolve_emptyRequiredList() {
        String toResolve = "${value}";
        Variable variable = Variable.newBuilder().name("value").value("").defaultValue(null).required(true).dataType(VariableDataType.LIST).build();
        Object result = executeResolvePrimitive(toResolve, List.of(variable));

        assertThat(result).isInstanceOf(List.class);
        List<String> castedResult = (List<String>) result;
        assertThat(castedResult).isEmpty();
    }

    @Test
    void resolve_emptyOptionalList() {
        String toResolve = "${value}";
        Variable variable = Variable.newBuilder().name("value").value("").defaultValue(null).required(false).dataType(VariableDataType.LIST).build();
        Object result = executeResolvePrimitive(toResolve, List.of(variable));

        assertThat(result).isInstanceOf(List.class);
        List<String> castedResult = (List<String>) result;
        assertThat(castedResult).isEmpty();
    }

    @Test
    void resolve_populatedMap() {
        String toResolve = "${value}";

        // spaces around the keys and values should be removed
        Variable variable = Variable.newBuilder().name("value").value(" one  key    :  first  , two:  second value  ").dataType(VariableDataType.MAP).build();

        Object result = executeResolvePrimitive(toResolve, List.of(variable));

        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> castedResult = (Map<String, Object>) result;
        assertThat(castedResult).hasSize(2).containsEntry("one  key", "first").containsEntry("two", "second value");
    }

    @Test
    void resolve_nullRequiredMap() {
        String toResolve = "${value}";
        Variable variable = Variable.newBuilder().name("value").value(null).defaultValue(null).required(true).dataType(VariableDataType.MAP).build();

        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(List.of(variable), "action");
        Object result = flowPlanPropertyHelper.resolveObject(toResolve, "");

        // when the map is required the null value will cause an error to be tracked in the helper and return the original unresolved value
        assertThat(result).isEqualTo(toResolve);
        assertThat(flowPlanPropertyHelper.getErrors()).hasSize(1);
    }

    @Test
    void resolve_nullOptionalMap() {
        String toResolve = "${value}";
        Variable variable = Variable.newBuilder().name("value").value(null).defaultValue(null).required(false).dataType(VariableDataType.MAP).build();

        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(List.of(variable), "action");
        Object result = flowPlanPropertyHelper.resolveObject(toResolve, "");

        // when the map is optional the null value will be returned and no errors will be added
        assertThat(result).isNull();
        assertThat(flowPlanPropertyHelper.getErrors()).isEmpty();
    }

    @Test
    void resolve_emptyRequiredMap() {
        String toResolve = "${value}";
        Variable variable = Variable.newBuilder().name("value").value("").defaultValue(null).required(true).dataType(VariableDataType.MAP).build();
        Object result = executeResolvePrimitive(toResolve, List.of(variable));

        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> castedResult = (Map<String, Object>) result;
        assertThat(castedResult).isEmpty();
    }

    @Test
    void resolve_emptyOptionalMap() {
        String toResolve = "${value}";
        Variable variable = Variable.newBuilder().name("value").value("").defaultValue(null).required(false).dataType(VariableDataType.MAP).build();
        Object result = executeResolvePrimitive(toResolve, List.of(variable));

        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> castedResult = (Map<String, Object>) result;
        assertThat(castedResult).isEmpty();
    }

    @Test
    void test_replaceListPlaceholders() {
        Variable setValue = Variable.newBuilder().name("value").value("setIt").dataType(VariableDataType.STRING).build();
        Variable optionalValue = Variable.newBuilder().name("unsetOptional").value(null).defaultValue(null).dataType(VariableDataType.STRING).build();
        Variable expandMe = Variable.newBuilder().name("expandMe").value("x, y, z").dataType(VariableDataType.LIST).build();

        List<Object> listToProcess = new ArrayList<>();

        Map<String, String> subObject = new HashMap<>();
        subObject.put("key", "${value}");

        List<String> subList = List.of("a", "${value}", "c");

        listToProcess.add(subObject);
        listToProcess.add(subList);
        listToProcess.add(null);
        listToProcess.add("${unsetOptional}");
        listToProcess.add("${expandMe}");

        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(List.of(setValue, optionalValue, expandMe), "action");
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
        Variable setValue = Variable.newBuilder().name("value").value("setIt").dataType(VariableDataType.STRING).build();
        Variable optionalValue = Variable.newBuilder().name("unsetOptional").value(null).defaultValue(null).dataType(VariableDataType.STRING).build();
        Variable expandMe = Variable.newBuilder().name("expandMe").value("x, y, z").dataType(VariableDataType.LIST).build();

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

        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(List.of(setValue, optionalValue, expandMe), "action");
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
        SchemaComplianceValidator validator = new SchemaComplianceValidator(null);

        Map<String, Object> parameters = Util.readResource("config-test/complex-parameter-values.json", Map.class);

        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(variables(), "action");
        Map<String, Object> mappedParameters = flowPlanPropertyHelper.replaceMapPlaceholders(parameters, "");

        ActionConfiguration actionConfiguration = new EgressActionConfiguration();
        actionConfiguration.setParameters(mappedParameters);

        ActionDescriptor egressActionDescriptor = Util.egressActionDescriptor("config-test/complex-parameter-action-descriptor.json");
        List<FlowConfigError> errors = validator.validateAgainstSchema(egressActionDescriptor, actionConfiguration);
        assertThat(errors).isEmpty();
    }

    Object executeResolvePrimitive(Object object, List<Variable> variables) {
        FlowPlanPropertyHelper flowPlanPropertyHelper = new FlowPlanPropertyHelper(variables, "action");

        return flowPlanPropertyHelper.resolveObject(object, "");
    }


    List<Variable> variables() {
        return List.of(
                Variable.newBuilder().name("enrichments").defaultValue("{'test1':'test1','test2':'test2'}").dataType(VariableDataType.MAP).build(),
                Variable.newBuilder().name("some.optional.list").value(null).required(false).dataType(VariableDataType.LIST).build(),
                Variable.newBuilder().name("some.required.list").value("").required(true).dataType(VariableDataType.LIST).build(),
                Variable.newBuilder().name("egressUrl").defaultValue("http://deltafi-egress-sink-service").dataType(VariableDataType.STRING).build(),
                Variable.newBuilder().name("retryCount").defaultValue("150").dataType(VariableDataType.NUMBER).build(),
                Variable.newBuilder().name("subKeyValue").required(false).dataType(VariableDataType.STRING).build(),
                Variable.newBuilder().name("listValue").value("").required(false).dataType(VariableDataType.LIST).build(),
                Variable.newBuilder().name("boolVal").value(" True ").dataType(VariableDataType.BOOLEAN).build());
    }

}