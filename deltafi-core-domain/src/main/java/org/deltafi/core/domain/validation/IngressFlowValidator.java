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
package org.deltafi.core.domain.validation;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.domain.generated.types.FlowConfigError;
import org.deltafi.core.domain.types.IngressFlow;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class IngressFlowValidator extends FlowValidator<IngressFlow> {

    public IngressFlowValidator(SchemaComplianceValidator schemaComplianceValidator) {
        super(schemaComplianceValidator);
    }

    @Override
    public List<FlowConfigError> flowSpecificValidation(IngressFlow ingressFlow) {
        return Collections.emptyList();
    }
}
