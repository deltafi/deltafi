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
package org.deltafi.ingress.client;

import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.common.types.IngressInput;

import java.util.HashSet;
import java.util.Set;

public class IngressGraphQLQuery extends GraphQLQuery {
  public IngressGraphQLQuery(IngressInput input, Set<String> fieldsSet) {
    super("mutation");
    if (input != null || fieldsSet.contains("input")) {
        getInput().put("input", input);
    }
  }

  public IngressGraphQLQuery() {
    super("mutation");
  }

  @Override
  public String getOperationName() {
     return "ingress";
                    
  }

  public static Builder newRequest() {
    return new Builder();
  }

  public static class Builder {
    private Set<String> fieldsSet = new HashSet<>();

    private IngressInput input;

    public IngressGraphQLQuery build() {
      return new IngressGraphQLQuery(input, fieldsSet);
               
    }

    public Builder input(IngressInput input) {
      this.input = input;
      this.fieldsSet.add("input");
      return this;
    }
  }
}
