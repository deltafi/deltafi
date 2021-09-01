package org.deltafi.actionkit.service;

import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.core.domain.generated.client.DeltaFileGraphQLQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InMemoryDomainGatewayServiceTest {
    InMemoryDomainGatewayService domainGatewayService = new InMemoryDomainGatewayService();

    @BeforeEach
    void setup() {
        domainGatewayService.clear();
    }

    @Test
    void testStorage() {
        DeltaFileGraphQLQuery query = DeltaFileGraphQLQuery.newRequest().did("did").build();
        GraphQLQueryRequest request = new GraphQLQueryRequest(query);

        domainGatewayService.submit(request);
        assertEquals(Collections.singletonList(request.serialize()), domainGatewayService.getUnsentQueries());
    }
}