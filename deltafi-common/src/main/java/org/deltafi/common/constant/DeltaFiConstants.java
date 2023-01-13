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
package org.deltafi.common.constant;

public class DeltaFiConstants {

    private DeltaFiConstants(){}

    public static final String ADMIN_PERMISSION = "Admin";
    public static final String AUTO_RESOLVE_FLOW_NAME = "auto-resolve";
    public static final String DGS_QUEUE = "dgs";
    public static final String INGRESS_ACTION = "IngressAction";
    public static final String MATCHES_ANY = "any";
    public static final String MONGO_MAP_KEY_DOT_REPLACEMENT = ";;";
    public static final String NO_EGRESS_FLOW_CONFIGURED_ACTION = "NoEgressFlowConfiguredAction";
    public static final String PERMISSIONS_HEADER = "X-User-Permissions";
    public static final String SURVEY_ACTION = "SurveyAction";
    public static final String SYNTHETIC_EGRESS_ACTION_FOR_TEST_INGRESS = "SyntheticEgressActionForTestIngress";
    public static final String SYNTHETIC_EGRESS_ACTION_FOR_TEST_EGRESS = "SyntheticEgressActionForTestEgress";
    public static final String USER_HEADER = "X-User-Name";

    // Metric names
    public static final String BYTES_IN = "bytes_in";
    public static final String BYTES_OUT = "bytes_out";
    public static final String FILES_DROPPED = "files_dropped";
    public static final String FILES_ERRORED = "files_errored";
    public static final String FILES_FILTERED = "files_filtered";
    public static final String FILES_IN = "files_in";
    public static final String FILES_OUT = "files_out";

    // Tag names
    public static final String ACTION = "action";
    public static final String EGRESS_FLOW = "egressFlow";
    public static final String INGRESS_FLOW = "ingressFlow";
    public static final String SOURCE = "source";
}
