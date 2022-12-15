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
package org.deltafi.core.snapshot;

import org.springframework.core.Ordered;

public class SnapshotRestoreOrder {

    public static final int PROPERTIES_ORDER = Ordered.HIGHEST_PRECEDENCE;
    public static final int DELETE_POLICY_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;
    public static final int FLOW_ASSIGNMENT_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;
    public static final int PLUGIN_REGISTRY_ORDER = Ordered.HIGHEST_PRECEDENCE + 20;
    public static final int PLUGIN_VARIABLE_ORDER = Ordered.HIGHEST_PRECEDENCE + 30;
    public static final int FLOW_PLAN_ORDER = Ordered.HIGHEST_PRECEDENCE + 40;
    public static final int FLOW_ORDER = Ordered.HIGHEST_PRECEDENCE + 50;

    private SnapshotRestoreOrder() {}
}
