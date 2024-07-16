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
package org.deltafi.core.types;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.ActionFamily;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;

@Document
@Data
@EqualsAndHashCode(callSuper = true)
public class EgressFlow extends Flow implements Subscriber {
    private ActionConfiguration egressAction;
    private Set<String> expectedAnnotations;
    @JsonProperty(required = true)
    private Set<Rule> subscribe;

    /**
     * Schema versions:
     * 0 - original
     * 1 - skipped
     * 2 - separate flow and action name
     */
    public static final int CURRENT_SCHEMA_VERSION = 2;
    private int schemaVersion;

    @Override
    public boolean migrate() {
        if (schemaVersion < 2) {
            migrateAction(egressAction);
        }

        if (schemaVersion < CURRENT_SCHEMA_VERSION) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
            return true;
        }

        return false;
    }

    @Override
    public ActionConfiguration findActionConfigByName(String actionName) {
        return nameMatches(egressAction, actionName) ? egressAction : null;
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        List<ActionConfiguration> actionConfigurations = new ArrayList<>();
        if (egressAction != null) {
            actionConfigurations.add(egressAction);
        }
        return actionConfigurations;
    }

    @Override
    public void updateActionNamesByFamily(Map<ActionType, ActionFamily> actionFamilyMap) {
        updateActionNamesByFamily(actionFamilyMap, ActionType.EGRESS, egressAction.getName());
    }


    public Set<Rule> subscribeRules() {
        return subscribe;
    }

    @Override
    public FlowType flowType() {
        return FlowType.EGRESS;
    }
}
