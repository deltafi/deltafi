package org.deltafi.config.server.datafetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import graphql.ExecutionResult;
import org.assertj.core.api.Assertions;
import org.deltafi.config.server.constants.PropertyConstants;
import org.deltafi.config.server.api.domain.PropertySet;
import org.deltafi.config.server.api.domain.PropertyUpdate;
import org.deltafi.config.server.repo.PropertyRepository;
import org.deltafi.config.server.service.PropertyService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.deltafi.common.test.TestConstants.MONGODB_CONTAINER;
import static org.deltafi.config.server.testUtil.DataProviderUtil.getPropertySetWithProperty;

@SpringBootTest
@Testcontainers
@ActiveProfiles("native")
class PropertiesDatafetcherTest {

    @Container
    public static MongoDBContainer mongoDBContainer = new MongoDBContainer(MONGODB_CONTAINER);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String REMOVE_TEMPLATE = "mutation {removePluginPropertySet(propertySetId: \"%s\")}";
    private static final String ADD_PROPERTY_SET = "mutation($propertySet: PropertySetInput!) {addPluginPropertySet(propertySet: $propertySet)}";
    private static final String UPDATE_PROPERTIES = "mutation($updates: [PropertyUpdate]!) {updateProperties(updates: $updates)}";

    @Autowired
    DgsQueryExecutor dgsQueryExecutor;

    @MockBean
    PropertyService propertyService;

    @MockBean
    PropertyRepository propertyRepository;

    @Test
    void getPropertySets() {
        Mockito.when(propertyService.getAllProperties()).thenReturn(List.of(getPropertySetWithProperty("a"), getPropertySetWithProperty("b")));
        String query = "query {getPropertySets {id displayName description properties {key value hidden editable refreshable}}}";
        List<PropertySet> propertySetList = dgsQueryExecutor.executeAndExtractJsonPath(query, "data.getPropertySets");

        Assertions.assertThat(propertySetList).hasSize(2);
    }

    @Test
    void updateProperties() {
        PropertyUpdate update = PropertyUpdate.builder()
                .key("key").value("value").propertySetId(PropertyConstants.ACTION_KIT_PROPERTY_SET).build();

        Map<String, Object> updatesMap = Map.of("updates", List.of(Map.of("key", "key", "value", "value", "propertySetId", PropertyConstants.ACTION_KIT_PROPERTY_SET)));

        ExecutionResult result = dgsQueryExecutor.execute(UPDATE_PROPERTIES, updatesMap);
        Mockito.verify(propertyService).updateProperties(List.of(update));
        Assertions.assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void addPluginPropertySet_valid() {
        PropertySet propSet = getPropertySetWithProperty("a");
        Map<String, Object> properSetMap = Map.of("propertySet", OBJECT_MAPPER.convertValue(propSet, Map.class));
        ExecutionResult result = dgsQueryExecutor.execute(ADD_PROPERTY_SET, properSetMap);
        Mockito.verify(propertyService).saveProperties(propSet);
        Assertions.assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void addPluginPropertySet_commonFails() {
        PropertySet propSet = getPropertySetWithProperty(PropertyConstants.DELTAFI_PROPERTY_SET);
        Map<String, Object> properSetMap = Map.of("propertySet", OBJECT_MAPPER.convertValue(propSet, Map.class));
        ExecutionResult result = dgsQueryExecutor.execute(ADD_PROPERTY_SET, properSetMap);
        Mockito.verify(propertyService, Mockito.times(0)).saveProperties(propSet);
        Assertions.assertThat(result.getErrors()).hasSize(1);
    }

    @Test
    void removePluginPropertySet() {
        String removeMutation = String.format(REMOVE_TEMPLATE, "stix-actions");
        dgsQueryExecutor.execute(removeMutation);
        Mockito.verify(propertyService).removeProperties("stix-actions");
    }

    @Test
    void removePluginPropertySet_commonFails() {
        String removeMutation = String.format(REMOVE_TEMPLATE, PropertyConstants.DELTAFI_PROPERTY_SET);
        ExecutionResult result = dgsQueryExecutor.execute(removeMutation);

        Assertions.assertThat(result.getErrors()).hasSize(1);
    }
}