/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.types;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DeletePolicies {

    private List<TimedDeletePolicy> timedPolicies;

    public DeletePolicies() {
        timedPolicies = new ArrayList<>();
    }

    public DeletePolicies(List<DeletePolicy> policies) {
        this.timedPolicies = new ArrayList<>();
        if (policies != null) {
            for (DeletePolicy policy : policies) {
                if (policy instanceof TimedDeletePolicy timedPolicy) {
                    timedPolicies.add(timedPolicy);
                }
            }
        }
    }

    public List<DeletePolicy> allPolicies() {
        List<DeletePolicy> allPolicies = new ArrayList<>();
        if (null != timedPolicies) {
            allPolicies.addAll(timedPolicies);
        }

        return allPolicies;
    }
}
