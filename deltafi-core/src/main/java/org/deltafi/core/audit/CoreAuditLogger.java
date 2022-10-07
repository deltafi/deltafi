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
package org.deltafi.core.audit;

import com.netflix.graphql.dgs.context.DgsContext;
import com.netflix.graphql.dgs.internal.DgsWebMvcRequestData;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.deltafi.common.constant.DeltaFiConstants.USER_HEADER;

@Component
@Slf4j(topic = "AUDIT")
public class CoreAuditLogger extends SimpleInstrumentation {

    private static final String UNKNOWN_USER = "system";
    private static final String IGNORABLE_PATH = "registerActions";

    @Override
    public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
        String path = parameters.getExecutionStepInfo().getPath().getSegmentName();
        boolean isMutation = isMutation(parameters);

        return environment -> {
            if (isMutation && !IGNORABLE_PATH.equals(path)) {

                DgsWebMvcRequestData webContext = DgsContext.getCustomContext(environment);
                List<String> id = null != webContext && null != webContext.getHeaders() ? webContext.getHeaders().getOrEmpty(USER_HEADER) : List.of();

                String userName = !id.isEmpty() ? id.get(0) : UNKNOWN_USER;

                MDC.put("user", userName);
                log.info("called mutation {}", path);
                MDC.remove("user");
            }

            return dataFetcher.get(environment);
        };
    }

    private boolean isMutation(InstrumentationFieldFetchParameters parameters) {
        GraphQLOutputType type = parameters.getExecutionStepInfo().getParent().getType();
        GraphQLObjectType parent;
        if (type instanceof GraphQLNonNull) {
            parent = (GraphQLObjectType) ((GraphQLNonNull) type).getWrappedType();
        } else {
            parent = (GraphQLObjectType) type;
        }

        return "Mutation".equals(parent.getName());
    }

    public void logIngress(String userName, String fileName) {
        MDC.put("user", userName);
        log.info("ingress {}", fileName);
        MDC.remove("user");
    }
}
