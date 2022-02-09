package org.deltafi.core.domain.datafetchers;

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.core.domain.generated.client.DeltaFiConfigsGraphQLQuery;
import org.deltafi.core.domain.generated.client.DeltaFiConfigsProjectionRoot;
import org.deltafi.core.domain.generated.client.RemoveDeltaFiConfigsGraphQLQuery;
import org.deltafi.core.domain.generated.types.ConfigQueryInput;
import org.deltafi.core.domain.generated.types.ConfigType;
import org.deltafi.core.domain.generated.types.DeltaFiConfiguration;
import org.deltafi.core.domain.generated.types.LoadActionConfiguration;
import org.deltafi.core.domain.services.DeltaFiConfigService;
import org.deltafi.core.domain.validation.DeltafiRuntimeConfigurationValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static graphql.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties = {"enableScheduling=false"})
class DeltafiConfigDatafetcherTest {

    public static final String NAME = "SampleLoadAction";

    @Autowired
    DgsQueryExecutor dgsQueryExecutor;

    @Autowired
    DeltaFiConfigService configService;

    @MockBean
    DeltafiRuntimeConfigurationValidator configValidator;

    @BeforeEach
    void setup() throws IOException {
        String config = new String(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("deltafi-config.yaml")).readAllBytes());
        configService.replaceConfig(config);
    }

    @Test
    void findConfigsTest() {
        ConfigQueryInput configQueryInput = ConfigQueryInput.newBuilder().configType(ConfigType.LOAD_ACTION).name(NAME).build();

        DeltaFiConfigsProjectionRoot projection = rootProject()
                .onLoadActionConfiguration()
                .consumes()
                .parent();

        DeltaFiConfigsGraphQLQuery findConfig = DeltaFiConfigsGraphQLQuery.newRequest().configQuery(configQueryInput).build();

        TypeRef<List<DeltaFiConfiguration>> listOfConfigs = new TypeRef<>() {
        };
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(findConfig, projection);
        List<DeltaFiConfiguration> configs = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + findConfig.getOperationName(),
                listOfConfigs);

        assertTrue(configs.get(0) instanceof LoadActionConfiguration);

        LoadActionConfiguration loadActionConfiguration = (LoadActionConfiguration) configs.get(0);
        assertEquals(NAME, loadActionConfiguration.getName());
        assertEquals("json-utf8-sample", loadActionConfiguration.getConsumes());
        Assertions.assertNull(loadActionConfiguration.getType()); // not in the projection should be null
    }

    @Test
    void deleteConfigsTest() {
        DeltaFiConfigsProjectionRoot projection = rootProject();

        RemoveDeltaFiConfigsGraphQLQuery remove = RemoveDeltaFiConfigsGraphQLQuery.newRequest().build();
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(remove, null);
        Integer removed = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + remove.getOperationName(),
                Integer.class);
        assertEquals(16, removed.intValue());
    }

    DeltaFiConfigsProjectionRoot rootProject() {
        return new DeltaFiConfigsProjectionRoot()
                .name()
                .apiVersion();
    }
}
