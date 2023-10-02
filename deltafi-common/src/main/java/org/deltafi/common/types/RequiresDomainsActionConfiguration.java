/*
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
package org.deltafi.common.types;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RequiresDomainsActionConfiguration extends ActionConfiguration {
    @Getter
    private final List<String> requiresDomains;

    public RequiresDomainsActionConfiguration(String name, ActionType actionType, String type,
                                              List<String> requiresDomains) {
        super(name, actionType, type);
        this.requiresDomains = requiresDomains;
    }

    @Override
    public List<String> validate(ActionDescriptor actionDescriptor) {
        List<String> errors = super.validate(actionDescriptor);

        if (null == actionDescriptor.getRequiresDomains() || actionDescriptor.getRequiresDomains().isEmpty()) {
            errors.add("The action configuration requiresDomains must have one or more values");
        }
        if (!ActionConfiguration.equalOrAny(actionDescriptor.getRequiresDomains(), requiresDomains)) {
            errors.add("The action configuration requiresDomains value must be: " + actionDescriptor.getRequiresDomains());
        }

        return errors;
    }
}
