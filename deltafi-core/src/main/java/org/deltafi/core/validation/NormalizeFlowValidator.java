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
package org.deltafi.core.validation;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.types.NormalizeFlow;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class NormalizeFlowValidator extends FlowValidator<NormalizeFlow> {

    public NormalizeFlowValidator(SchemaComplianceValidator schemaComplianceValidator) {
        super(schemaComplianceValidator);
    }

    @Override
    public List<FlowConfigError> flowSpecificValidation(NormalizeFlow normalizeFlow) {
        return Collections.emptyList();
    }
}
