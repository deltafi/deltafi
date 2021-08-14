package org.deltafi.actionkit.service;

import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;

import java.util.Collections;
import java.util.List;

public interface DomainGatewayService {
    GraphQLResponse submit(GraphQLQueryRequest request);
    default List<String> getUnsentQueries() { return Collections.emptyList(); }
}