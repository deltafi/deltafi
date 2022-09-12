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
package org.deltafi.core.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.deltafi.core.types.ActionSchema;
import org.deltafi.core.types.EnrichActionSchema;

import java.util.ArrayList;
import java.util.List;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
public class EnrichActionConfiguration extends org.deltafi.core.generated.types.EnrichActionConfiguration implements ActionRequiringDomainConfiguration {

    public EnrichActionConfiguration() {
        setRequiresDomains(new ArrayList<>());
        setRequiresEnrichment(new ArrayList<>());
        setRequiresMetadataKeyValues(new ArrayList<>());
    }

    @Override
    public List<String> validate(ActionSchema actionSchema) {
        List<String> errors = new ArrayList<>();

        if (actionSchema instanceof EnrichActionSchema) {
            EnrichActionSchema schema = (EnrichActionSchema) actionSchema;
            if (!ActionConfiguration.equalOrAny(schema.getRequiresDomains(), this.getRequiresDomains())) {
                errors.add("The action configuration requiresDomain value must be: " + schema.getRequiresDomains());
            }
            if (!ActionConfiguration.equalOrAny(schema.getRequiresEnrichment(), this.getRequiresEnrichment())) {
                errors.add("The action configuration requiresEnrichment value must be: " + schema.getRequiresEnrichment());
            }
        } else {
            errors.add("Action: " + getType() + " is not registered as an EnrichAction");
        }

        return errors;
    }

}