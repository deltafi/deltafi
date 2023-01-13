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
package org.deltafi.common.graphql.dgs;

import com.netflix.graphql.dgs.client.GraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLError;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;

import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class GraphQLExecutor {
    public static <T> T executeQuery(GraphQLClient graphQLClient, GraphQLQueryRequest queryRequest,
            String responseObjectPath, Class<T> responseObjectClass) throws Exception {
        String serializedRequest = queryRequest.serialize();

        GraphQLResponse response = graphQLClient.executeQuery(serializedRequest);

        if (response.hasErrors()) {
            String errorMessage = "Error in DGS submission:\n\nOriginal query:\n" + serializedRequest + "\n\n" +
                    response.getErrors().stream().map(GraphQLError::getMessage).collect(Collectors.joining("\n"));
            throw new Exception(errorMessage);
        }

        return response.extractValueAsObject(responseObjectPath, responseObjectClass);
    }
}
