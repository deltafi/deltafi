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

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.common.types.ActionDescriptor;
import org.deltafi.core.generated.client.ActionDescriptorsGraphQLQuery;
import org.deltafi.core.generated.client.ActionDescriptorsProjectionRoot;

import java.util.List;

public class ActionDescriptorDatafetcherTestHelper {

    public static final String EGRESS_ACTION = "org.deltafi.EgressAction";
    public static final String DOMAIN_ACTION = "org.deltafi.DomainAction";
    public static final String ENRICH_ACTION = "org.deltafi.EnrichAction";
    public static final String FORMAT_ACTION = "org.deltafi.FormatAction";
    public static final String LOAD_ACTION = "org.deltafi.LoadAction";
    public static final String TRANSFORM_ACTION = "org.deltafi.TransformAction";
    public static final String VALIDATE_ACTION = "org.deltafi.ValidatreAction";
    public static final String PARAM_CLASS = "paramClass";
    public static final String DOMAIN = "domain";

    public static List<ActionDescriptor> getActionDescriptors(DgsQueryExecutor dgsQueryExecutor) {
        ActionDescriptorsProjectionRoot projection = new ActionDescriptorsProjectionRoot()
                .name()
                .paramClass()
                .schema()
                .requiresDomains()
                .requiresEnrichments();

        ActionDescriptorsGraphQLQuery actionDescriptorsGraphQLQuery = ActionDescriptorsGraphQLQuery.newRequest().build();

        GraphQLQueryRequest graphQLQueryRequest =
                new GraphQLQueryRequest(actionDescriptorsGraphQLQuery, projection);

        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
                "data." + actionDescriptorsGraphQLQuery.getOperationName(), new TypeRef<>() {});
    }
}
