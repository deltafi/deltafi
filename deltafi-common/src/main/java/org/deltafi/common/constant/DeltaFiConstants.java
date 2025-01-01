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
package org.deltafi.common.constant;

import java.util.UUID;

public class DeltaFiConstants {

    private DeltaFiConstants(){}

    public static final UUID ADMIN_ID = new UUID(0, 0);
    public static final String ADMIN_PERMISSION = "Admin";
    public static final String INGRESS_ACTION = "IngressAction";
    public static final String INVALID_ACTION_EVENT_RECEIVED = "Invalid action event received";
    public static final String MISSING_FLOW_ACTION = "MissingRunningFlow";
    public static final String PERMISSIONS_HEADER = "X-User-Permissions";
    public static final String SYNTHETIC_EGRESS_ACTION_FOR_TEST = "SyntheticEgressActionForTestEgress";
    public static final String USER_NAME_HEADER = "X-User-Name";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_METRIC_ROLE_HEADER = "X-Metrics-Role";

    // Metric names
    public static final String BYTES_IN = "bytes_in";
    public static final String BYTES_OUT = "bytes_out";
    public static final String BYTES_FROM_SOURCE = "bytes_from_source";
    public static final String BYTES_TO_SINK = "bytes_to_sink";
    public static final String FILES_FROM_SOURCE = "files_from_source";
    public static final String FILES_TO_SINK = "files_to_sink";
    public static final String FILES_AUTO_RESUMED = "files_auto_resumed";
    public static final String FILES_DROPPED = "files_dropped";
    public static final String FILES_ERRORED = "files_errored";
    public static final String FILES_FILTERED = "files_filtered";
    public static final String FILES_IN = "files_in";
    public static final String FILES_OUT = "files_out";
    public static final String ACTION_EXECUTION = "action_execution";
    public static final String ACTION_EXECUTION_TIME_MS = "action_execution_time_ms";
    public static final String EXECUTION_TIME_MS = "execution_time_ms";
    public static final String DELETED_FILES = "deleted.files";
    public static final String DELETED_BYTES = "deleted.bytes";

    // Tag names
    public static final String ACTION = "action";
    public static final String DATA_SINK = "dataSink";
    public static final String DATA_SOURCE = "dataSource";
    public static final String SOURCE = "source";
    public static final String CLASS = "class";
}
