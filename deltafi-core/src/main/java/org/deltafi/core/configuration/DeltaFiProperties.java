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
package org.deltafi.core.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.LogSeverity;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.VariableDataType;
import org.deltafi.core.types.leader.CredentialsConfig;
import org.deltafi.core.types.leader.MemberConfig;

import java.time.Duration;
import java.util.*;

/**
 * Holds the system properties. To add a new property that will be exposed
 * add the field to the class with PropertyInfo annotation. If the field
 * requires validation add a custom setter with the validation logic.
 */
@Data
@Slf4j
@SuppressWarnings("unused")
public class DeltaFiProperties {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private List<MemberConfig> cachedMemberConfigs;

    @PropertyInfo(group = PropertyGroup.UI_CONTROLS, description = "Name of the DeltaFi system", defaultValue = "DeltaFi")
    private String systemName = "DeltaFi";

    @PropertyInfo(group = PropertyGroup.DATA_FLOW_CONTROLS, description = "[Duration or ISO 8601] Time to wait for an action to finish processing a DeltaFile before requeuing the action", defaultValue = "PT5M")
    private Duration requeueDuration = Duration.ofMinutes(5);

    @PropertyInfo(group = PropertyGroup.ERROR_CONTROLS, description = "[Duration or ISO 8601] Frequency that the auto-resume check is triggered", defaultValue = "PT1M")
    private Duration autoResumeCheckFrequency = Duration.ofMinutes(1);

    @PropertyInfo(group = PropertyGroup.PERFORMANCE_CONTROLS, description = "The number of threads used in core processing", defaultValue = "16", dataType = VariableDataType.NUMBER)
    private int coreServiceThreads = 16;

    @PropertyInfo(group = PropertyGroup.PERFORMANCE_CONTROLS, description = "The number of incoming events for core to queue internally for processing", defaultValue = "64", dataType = VariableDataType.NUMBER)
    private int coreInternalQueueSize = 64;

    @PropertyInfo(group = PropertyGroup.PERFORMANCE_CONTROLS, description = "Maximum allowed number of threads", defaultValue = "8", dataType = VariableDataType.NUMBER)
    private int scheduledServiceThreads = 8;

    @PropertyInfo(group = PropertyGroup.METRICS_AND_ANALYTICS, description = "Enable reporting of metrics to VictoriaMetrics", defaultValue = "true", refreshable = false, dataType = VariableDataType.BOOLEAN)
    private boolean metricsEnabled = true;

    @PropertyInfo(group = PropertyGroup.METRICS_AND_ANALYTICS, description = "Enable analytics storage in TimescaleDB", defaultValue = "true", dataType = VariableDataType.BOOLEAN)
    private boolean timescaleAnalyticsEnabled = true;

    @PropertyInfo(group = PropertyGroup.METRICS_AND_ANALYTICS, description = "Name of the analytics group used to aggregate metrics. This provides a level of grouping more specific than data source.")
    private String analyticsGroupName;

    @PropertyInfo(group = PropertyGroup.METRICS_AND_ANALYTICS, description = "Comma-separated list of allowed analytics annotation keys to be promoted into metrics. Only these annotations will be used for grouping/filtering in analytics.", dataType = VariableDataType.LIST)
    private String allowedAnalyticsAnnotations;

    @PropertyInfo(group = PropertyGroup.METRICS_AND_ANALYTICS, description = "Enable analytics export to the Parquet-based analytics collector", defaultValue = "false", dataType = VariableDataType.BOOLEAN)
    private boolean parquetAnalyticsEnabled = false;

    @PropertyInfo(group = PropertyGroup.METRICS_AND_ANALYTICS, description = "Number of days to retain Parquet analytics data before automatic deletion", defaultValue = "30", dataType = VariableDataType.NUMBER)
    private int parquetAnalyticsAgeOffDays = 30;

    @PropertyInfo(group = PropertyGroup.DATA_RETENTION, description = "Number of days that a DeltaFile should live, any records older will be removed", defaultValue = "13", dataType = VariableDataType.NUMBER)
    private int ageOffDays = 13;

    @PropertyInfo(group = PropertyGroup.DATA_RETENTION, description = "The max percentage of disk space to use. When the system exceeds this percentage, content will be removed to lower the disk space usage.", defaultValue = "80.0")
    private double diskSpacePercentThreshold = 80.0;

    @PropertyInfo(group = PropertyGroup.DATA_RETENTION, description = "The max percentage of disk space to use for database metadata. When the metadata size exceeds this percentage, deltaFiles will be removed to lower the metadata disk space usage.", defaultValue = "40.0")
    private double metadataDiskSpacePercentThreshold = 40.0;

    @PropertyInfo(group = PropertyGroup.DATA_RETENTION, description = "[Duration or ISO 8601] Frequency that the delete action is triggered", defaultValue = "PT5M")
    private Duration deleteFrequency = Duration.ofMinutes(5);

    @PropertyInfo(group = PropertyGroup.DATA_RETENTION, description = "Maximum deletes per policy iteration loop", defaultValue = "1000", dataType = VariableDataType.NUMBER)
    private int deletePolicyBatchSize = 1000;

    @PropertyInfo(group = PropertyGroup.DATABASE_CONTROLS, description = "Maximum DeltaFiles to insert in a batch", defaultValue = "1000", dataType = VariableDataType.NUMBER)
    private int insertBatchSize = 1000;

    @PropertyInfo(group = PropertyGroup.PERFORMANCE_CONTROLS, description = "[Duration or ISO 8601] Sync all DeltaFiles that have not been modified for this duration", defaultValue = "PT30S")
    private Duration cacheSyncDuration = Duration.ofSeconds(30);

    @PropertyInfo(group = PropertyGroup.EGRESS_CONTROLS, description = "Enables or disables all egress. When this is false all DeltaFiles will go to a paused state when they reach a DataSink until this value is set back to true.", defaultValue = "true", dataType = VariableDataType.BOOLEAN)
    private boolean egressEnabled = true;

    @PropertyInfo(group = PropertyGroup.INGRESS_CONTROLS, description = "Enables or disables all ingress", defaultValue = "true", dataType = VariableDataType.BOOLEAN)
    private boolean ingressEnabled = true;

    @PropertyInfo(group = PropertyGroup.INGRESS_CONTROLS, description = "The threshold for automatic disable of ingress.  If the available storage for ingress drops below this requirement, ingress will be temporarily disabled until the system frees up storage.", defaultValue = "1000", dataType = VariableDataType.NUMBER)
    private long ingressDiskSpaceRequirementInMb = 1000;

    @PropertyInfo(group = PropertyGroup.PLUGIN_CONTROLS, description = "Default imagePullSecret used in plugin deployments")
    private String pluginImagePullSecret;

    @PropertyInfo(group = PropertyGroup.PLUGIN_CONTROLS, description = "[Duration or ISO 8601] Max time to wait for a plugin deployment to succeed", defaultValue = "PT3M")
    private Duration pluginDeployTimeout = Duration.ofMinutes(3);

    @PropertyInfo(group = PropertyGroup.DATA_FLOW_CONTROLS, description = "[Duration or ISO 8601] Max time to allow an action to run before restarting the pod. Null (or 0) indicates disabled, which is the default. To enable, this value must be greater than 30 seconds and should be less than the requeueDuration. To disable this feature use 'Revert' (or set the value to 0)")
    private Duration actionExecutionTimeout;

    @PropertyInfo(group = PropertyGroup.DATA_FLOW_CONTROLS, description = "[Duration or ISO 8601] Minimum time to allow an action to remain running before a warning is generated . Null (or 0) indicates disabled, which is the default. To disable this feature use 'Revert' (or set the value to 0)")
    private Duration actionExecutionWarning;

    @PropertyInfo(group = PropertyGroup.SYSTEM_MONITORING, description = "Threshold for Action Queue size check", defaultValue = "10", dataType = VariableDataType.NUMBER)
    private int checkActionQueueSizeThreshold = 10;

    @PropertyInfo(group = PropertyGroup.SYSTEM_MONITORING, description = "Minimum number for cold queue size before checking if growing", defaultValue = "10000", dataType = VariableDataType.NUMBER)
    private int checkColdQueueMinimumGrowing = 10000;

    @PropertyInfo(group = PropertyGroup.SYSTEM_MONITORING, description = "Threshold for cold queue size warning", defaultValue = "75000", dataType = VariableDataType.NUMBER)
    private int checkColdQueueWarningThreshold = 75000;

    @PropertyInfo(group = PropertyGroup.DATA_RETENTION, description = "Threshold for content storage usage check", defaultValue = "90", dataType = VariableDataType.NUMBER)
    private int checkContentStoragePercentThreshold = 90;

    @PropertyInfo(group = PropertyGroup.SYSTEM_MONITORING, description = "Pending delete lag warning threshold. System status becomes degraded when any node has more pending deletes than the configured value.", defaultValue = "100000", dataType = VariableDataType.NUMBER)
    private int checkDeleteLagWarningThreshold = 100_000;

    @PropertyInfo(group = PropertyGroup.SYSTEM_MONITORING, description = "Pending delete lag error threshold. System status becomes unhealthy when any node has more pending deletes than the configured value.", defaultValue = "500000", dataType = VariableDataType.NUMBER)
    private int checkDeleteLagErrorThreshold = 500_000;

    @PropertyInfo(group = PropertyGroup.SYSTEM_MONITORING, description = "Certificate expiration error threshold (days). System status becomes unhealthy when any certificate expires within this timeframe.", defaultValue = "4", dataType = VariableDataType.NUMBER)
    private int checkSslExpirationErrorThreshold = 4;

    @PropertyInfo(group = PropertyGroup.SYSTEM_MONITORING, description = "Certificate expiration warning threshold (days). System status becomes degraded when any certificate expires within this timeframe.", defaultValue = "14", dataType = VariableDataType.NUMBER)
    private int checkSslExpirationWarningThreshold = 14;

    @PropertyInfo(group = PropertyGroup.SYSTEM_MONITORING, description = "Leader-member monitoring configuration. Only configured on leader instances. JSON object mapping member names to their config: {\"site1\": {\"url\": \"https://site1.example.com\", \"tags\": [\"east\"]}, \"site2\": {\"url\": \"https://site2.example.com\", \"tags\": [\"west\", \"production\"]}}", defaultValue = "{}", refreshable = true, dataType = VariableDataType.STRING)
    private String leaderConfig = "{}";

    public void setLeaderConfig(String leaderConfig) {
        // Parse and validate in one pass, caching the result
        this.cachedMemberConfigs = parseAndValidateLeaderConfig(leaderConfig);
        this.leaderConfig = leaderConfig;
    }

    private List<MemberConfig> parseAndValidateLeaderConfig(String config) {
        if (StringUtils.isBlank(config) || "{}".equals(config.trim())) {
            return List.of();
        }

        List<String> errors = new ArrayList<>();
        List<MemberConfig> configs = new ArrayList<>();

        Map<String, Object> configMap;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(config, Map.class);
            configMap = parsed;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid leaderConfig JSON: " + e.getMessage());
        }

        for (Map.Entry<String, Object> entry : configMap.entrySet()) {
            String memberName = entry.getKey();
            if (!(entry.getValue() instanceof Map)) {
                errors.add("Member '" + memberName + "': value must be an object");
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> memberMap = (Map<String, Object>) entry.getValue();

            // Validate and extract URL
            Object urlObj = memberMap.get("url");
            if (urlObj == null || !(urlObj instanceof String) || ((String) urlObj).isBlank()) {
                errors.add("Member '" + memberName + "': url is required and must be a non-empty string");
                continue;
            }
            String url = (String) urlObj;
            
            // Validate URL format (must start with http:// or https://)
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                errors.add("Member '" + memberName + "': url must start with http:// or https://");
                continue;
            }
            
            // Basic URL validation
            try {
                java.net.URI.create(url);
            } catch (IllegalArgumentException e) {
                errors.add("Member '" + memberName + "': url is not a valid URI: " + e.getMessage());
                continue;
            }

            // Validate and extract tags
            List<String> tags = List.of();
            if (memberMap.containsKey("tags")) {
                Object tagsObj = memberMap.get("tags");
                if (!(tagsObj instanceof List)) {
                    errors.add("Member '" + memberName + "': tags must be a list");
                } else {
                    @SuppressWarnings("unchecked")
                    List<String> tagsList = (List<String>) tagsObj;
                    tags = tagsList;
                }
            }

            // Validate and extract credentials
            CredentialsConfig credentials = null;
            if (memberMap.containsKey("credentials")) {
                Object creds = memberMap.get("credentials");
                if (!(creds instanceof Map)) {
                    errors.add("Member '" + memberName + "': credentials must be an object");
                } else {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> credMap = (Map<String, Object>) creds;

                    Object type = credMap.get("type");
                    if (type == null || !"basic".equals(type)) {
                        errors.add("Member '" + memberName + "': credentials.type must be 'basic'");
                    }

                    Object username = credMap.get("username");
                    if (username == null || !(username instanceof String) || ((String) username).isBlank()) {
                        errors.add("Member '" + memberName + "': credentials.username is required");
                    }

                    Object passwordEnvVar = credMap.get("passwordEnvVar");
                    if (passwordEnvVar == null || !(passwordEnvVar instanceof String) || ((String) passwordEnvVar).isBlank()) {
                        errors.add("Member '" + memberName + "': credentials.passwordEnvVar is required");
                    }

                    if (errors.isEmpty()) {
                        credentials = new CredentialsConfig(
                            (String) type,
                            (String) username,
                            (String) passwordEnvVar
                        );
                    }
                }
            }

            if (errors.isEmpty()) {
                configs.add(new MemberConfig(memberName, url, tags, credentials));
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid leaderConfig: " + String.join("; ", errors));
        }

        return configs;
    }

    @PropertyInfo(group = PropertyGroup.SYSTEM_MONITORING, description = "Interval between polling cycles for each member (milliseconds). Healthy members are polled at this interval, unhealthy members at 1/6 of this interval.", defaultValue = "30000", refreshable = true, dataType = VariableDataType.NUMBER)
    private int memberPollingInterval = 30000;

    @PropertyInfo(group = PropertyGroup.SYSTEM_MONITORING, description = "Timeout for individual HTTP requests to members (milliseconds). Must be less than memberPollingTimeout.", defaultValue = "10000", refreshable = true, dataType = VariableDataType.NUMBER)
    private int memberRequestTimeout = 10000;

    /**
     * Get the timeout for polling operations (80% of polling interval).
     * This ensures requests complete before the next polling cycle.
     */
    public int getMemberPollingTimeout() {
        return (int) (memberPollingInterval * 0.8);
    }

    public int getMemberRequestTimeout() {
        return memberRequestTimeout;
    }

    public void setMemberPollingInterval(int memberPollingInterval) {
        if (memberPollingInterval < 5000) {
            throw new IllegalArgumentException("memberPollingInterval must be >= 5000ms, but was " + memberPollingInterval);
        }
        if (memberRequestTimeout >= memberPollingInterval * 0.8) {
            throw new IllegalArgumentException("memberRequestTimeout (" + memberRequestTimeout + "ms) must be < memberPollingTimeout (" + (int)(memberPollingInterval * 0.8) + "ms)");
        }
        this.memberPollingInterval = memberPollingInterval;
    }

    public void setMemberRequestTimeout(int memberRequestTimeout) {
        if (memberRequestTimeout < 1000) {
            throw new IllegalArgumentException("memberRequestTimeout must be >= 1000ms, but was " + memberRequestTimeout);
        }
        int pollingTimeout = (int) (memberPollingInterval * 0.8);
        if (memberRequestTimeout >= pollingTimeout) {
            throw new IllegalArgumentException("memberRequestTimeout (" + memberRequestTimeout + "ms) must be < memberPollingTimeout (" + pollingTimeout + "ms)");
        }
        this.memberRequestTimeout = memberRequestTimeout;
    }

    @PropertyInfo(group = PropertyGroup.UI_CONTROLS, description = "Display times in UTC", defaultValue = "true", dataType = VariableDataType.BOOLEAN)
    private boolean uiUseUTC = true;

    @PropertyInfo(group = PropertyGroup.UI_CONTROLS, description = "Maximum number of bytes the UI will retrieve from the backend when viewing content", defaultValue = "32768", dataType = VariableDataType.NUMBER)
    private long uiContentPreviewSize = 32768; // 32KB

    @PropertyInfo(group = PropertyGroup.UI_CONTROLS, description = "Background color of the top bar")
    private String topBarBackgroundColor;

    @PropertyInfo(group = PropertyGroup.UI_CONTROLS, description = "Text color of the top bar")
    private String topBarTextColor;

    @PropertyInfo(group = PropertyGroup.UI_CONTROLS, description = "Text to display in the security banner")
    private String securityBannerText;

    @PropertyInfo(group = PropertyGroup.UI_CONTROLS, description = "Background color of the security banner")
    private String securityBannerBackgroundColor;

    @PropertyInfo(group = PropertyGroup.UI_CONTROLS, description = "Color of the text in the security banner")
    private String securityBannerTextColor;

    @PropertyInfo(group = PropertyGroup.UI_CONTROLS, description = "Toggles the security banner display", defaultValue = "false", dataType = VariableDataType.BOOLEAN)
    private boolean securityBannerEnabled = false;

    @PropertyInfo(group = PropertyGroup.PERFORMANCE_CONTROLS, description = "Maximum size for in memory action queues before tasks are moved to on-disk queues", defaultValue = "5000", dataType = VariableDataType.NUMBER)
    private int inMemoryQueueSize = 5000;

    @PropertyInfo(group = PropertyGroup.DATA_FLOW_CONTROLS, description = "The maximum number of flows a DeltaFile may traverse", defaultValue = "32", dataType = VariableDataType.NUMBER)
    private int maxFlowDepth = 32;

    @PropertyInfo(group = PropertyGroup.JOIN_CONTROLS, description = "The amount of time to wait before timing out while waiting to acquire the join lock", defaultValue = "30000", dataType = VariableDataType.NUMBER)
    private long joinAcquireLockTimeoutMs = 30000;

    @PropertyInfo(group = PropertyGroup.JOIN_CONTROLS, description = "[Duration or ISO 8601] Maximum duration a database lock can be held on a " +
            "join entry before it is automatically unlocked", defaultValue = "PT1M")
    private Duration joinMaxLockDuration = Duration.ofMinutes(1);

    @PropertyInfo(group = PropertyGroup.JOIN_CONTROLS, description = "[Duration or ISO 8601] Frequency that database locks on join entries are " +
            "checked against the join.maxLockDuration", defaultValue = "PT1M")
    private Duration joinLockCheckInterval = Duration.ofMinutes(1);

    @PropertyInfo(group = PropertyGroup.DATABASE_CONTROLS, description = "Enable pg_squeeze extension. Postgres must be manually restarted if this is changed from true to false", defaultValue = "false", dataType = VariableDataType.BOOLEAN)
    private boolean autoCleanPostgres = false;

    @PropertyInfo(group = PropertyGroup.DATA_FLOW_CONTROLS, description = "Minimum severity for action log messages. "
            + "Choose: TRACE, INFO, WARNING, ERROR, or USER", defaultValue = "INFO")
    private LogSeverity minimumActionLogSeverity = LogSeverity.INFO;

    public long getIngressDiskSpaceRequirementInBytes() {
        return ingressDiskSpaceRequirementInMb * 1000000;
    }

    public void setSystemName(String systemName) {
        notBlankCheck(systemName, "systemName");
        this.systemName = systemName;
    }

    public void setRequeueDuration(Duration requeueDuration) {
        positiveDurationCheck(requeueDuration, "requeueDuration");
        this.requeueDuration = requeueDuration;
    }

    public void setAutoResumeCheckFrequency(Duration autoResumeCheckFrequency) {
        positiveDurationCheck(autoResumeCheckFrequency, "autoResumeCheckFrequency");
        this.autoResumeCheckFrequency = autoResumeCheckFrequency;
    }

    public void setCoreServiceThreads(int coreServiceThreads) {
        minCheck(coreServiceThreads, 1, "coreServiceThreads");
        this.coreServiceThreads = coreServiceThreads;
    }

    public void setCoreInternalQueueSize(int coreInternalQueueSize) {
        minCheck(coreInternalQueueSize, 1, "coreInternalQueueSize");
        this.coreInternalQueueSize = coreInternalQueueSize;
    }

    public void setScheduledServiceThreads(int scheduledServiceThreads) {
        minCheck(scheduledServiceThreads, 1, "scheduledServiceThreads");
        this.scheduledServiceThreads = scheduledServiceThreads;
    }

    public void setAgeOffDays(int ageOffDays) {
        minCheck(ageOffDays, 1, "ageOffDays");
        this.ageOffDays = ageOffDays;
    }

    public void setParquetAnalyticsAgeOffDays(int parquetAnalyticsAgeOffDays) {
        minCheck(parquetAnalyticsAgeOffDays, 1, "parquetAnalyticsAgeOffDays");
        this.parquetAnalyticsAgeOffDays = parquetAnalyticsAgeOffDays;
    }

    public void setDiskSpacePercentThreshold(double diskSpacePercentThreshold) {
        if (diskSpacePercentThreshold < 0.0 || diskSpacePercentThreshold > 100.0) {
            throw new IllegalArgumentException("The diskSpacePercentThreshold property must be between 0 and 100");
        }
        this.diskSpacePercentThreshold = diskSpacePercentThreshold;
    }

    public void setMetadataDiskSpacePercentThreshold(double metadataDiskSpacePercentThreshold) {
        if (metadataDiskSpacePercentThreshold < 0.0 || metadataDiskSpacePercentThreshold > 100.0) {
            throw new IllegalArgumentException("The metadataDiskSpacePercentThreshold property must be between 0 and 100");
        }
        this.metadataDiskSpacePercentThreshold = metadataDiskSpacePercentThreshold;
    }

    public void setDeleteFrequency(Duration deleteFrequency) {
        positiveDurationCheck(deleteFrequency, "deleteFrequency");
        this.deleteFrequency = deleteFrequency;
    }

    public void setDeletePolicyBatchSize(int deletePolicyBatchSize) {
        minCheck(deletePolicyBatchSize, 1, "deletePolicyBatchSize");
        this.deletePolicyBatchSize = deletePolicyBatchSize;
    }

    public void setInsertBatchSize(int insertBatchSize) {
        minCheck(insertBatchSize, 1, "insertBatchSize");
        this.insertBatchSize = insertBatchSize;
    }

    public void setCacheSyncDuration(Duration cacheSyncDuration) {
        positiveDurationCheck(cacheSyncDuration, "cacheSyncDuration");
        this.cacheSyncDuration = cacheSyncDuration;
    }

    public void setIngressDiskSpaceRequirementInMb(long ingressDiskSpaceRequirementInMb) {
        positiveLongCheck(ingressDiskSpaceRequirementInMb, "ingressDiskSpaceRequirementInMb");
        this.ingressDiskSpaceRequirementInMb = ingressDiskSpaceRequirementInMb;
    }

    public void setPluginDeployTimeout(Duration pluginDeployTimeout) {
        positiveDurationCheck(pluginDeployTimeout, "pluginDeployTimeout");
        this.pluginDeployTimeout = pluginDeployTimeout;
    }

    public void setActionExecutionTimeout(Duration actionExecutionTimeout) {
        long timeout = actionExecutionTimeout != null ? actionExecutionTimeout.toMillis() : 0;
        if (timeout != 0 && timeout <= 30_000L) {
            throw new IllegalArgumentException("The actionExecutionTimeout property must be larger than 30 seconds or set to 0 to disable this timeout but was " + actionExecutionTimeout);
        }
        this.actionExecutionTimeout = actionExecutionTimeout;
    }

    public void setActionExecutionWarning(Duration actionExecutionWarning) {
        long timeout = actionExecutionWarning != null ? actionExecutionWarning.toMillis() : 0L;
        if (timeout != 0L) {
            positiveLongCheck(timeout, "actionExecutionWarning");
        }
        this.actionExecutionWarning = actionExecutionWarning;
    }

    public void setCheckActionQueueSizeThreshold(int checkActionQueueSizeThreshold) {
        minCheck(checkActionQueueSizeThreshold, 0, "checkActionQueueSizeThreshold");
        this.checkActionQueueSizeThreshold = checkActionQueueSizeThreshold;
    }

    public void setCheckContentStoragePercentThreshold(int checkContentStoragePercentThreshold) {
        if (checkContentStoragePercentThreshold < 0 || checkContentStoragePercentThreshold > 100) {
            throw new IllegalArgumentException("The checkContentStoragePercentThreshold property must be between 0 and 100");
        }
        this.checkContentStoragePercentThreshold = checkContentStoragePercentThreshold;
    }

    public void setInMemoryQueueSize(int inMemoryQueueSize) {
        minCheck(inMemoryQueueSize, 10, "inMemoryQueueSize");
        this.inMemoryQueueSize = inMemoryQueueSize;
    }

    public void setMaxFlowDepth(int maxFlowDepth) {
        minCheck(maxFlowDepth, 1, "maxFlowDepth");
        this.maxFlowDepth = maxFlowDepth;
    }

    public void setJoinAcquireLockTimeoutMs(long joinAcquireLockTimeoutMs) {
        positiveLongCheck(joinAcquireLockTimeoutMs, "joinAcquireLockTimeoutMs");
        this.joinAcquireLockTimeoutMs = joinAcquireLockTimeoutMs;
    }

    public void setJoinMaxLockDuration(Duration joinMaxLockDuration) {
        positiveDurationCheck(joinMaxLockDuration, "joinMaxLockDuration");
        this.joinMaxLockDuration = joinMaxLockDuration;
    }

    public void setJoinLockCheckInterval(Duration joinLockCheckInterval) {
        positiveDurationCheck(joinLockCheckInterval, "joinLockCheckInterval");
        this.joinLockCheckInterval = joinLockCheckInterval;
    }

    public void setCheckSslExpirationErrorThreshold(int checkSslExpirationErrorThreshold) {
        minCheck(checkSslExpirationErrorThreshold, 0, "checkSslExpirationErrorThreshold");
        this.checkSslExpirationErrorThreshold = checkSslExpirationErrorThreshold;
    }

    public void setCheckSslExpirationWarningThreshold(int checkSslExpirationWarningThreshold) {
        minCheck(checkSslExpirationWarningThreshold, 0, "checkSslExpirationWarningThreshold");
        this.checkSslExpirationWarningThreshold = checkSslExpirationWarningThreshold;
    }

    public void setCheckDeleteLagWarningThreshold(int checkDeleteLagWarningThreshold) {
        minCheck(checkDeleteLagWarningThreshold, 0, "checkDeleteLagWarningThreshold");
        this.checkDeleteLagWarningThreshold = checkDeleteLagWarningThreshold;
    }

    public void setCheckDeleteLagErrorThreshold(int checkDeleteLagErrorThreshold) {
        minCheck(checkDeleteLagErrorThreshold, 0, "checkDeleteLagErrorThreshold");
        this.checkDeleteLagErrorThreshold = checkDeleteLagErrorThreshold;
    }

    /**
     * Returns the allowed analytics annotations as a list of trimmed strings.
     * If the property is null or empty, returns an empty list.
     *
     * @return a List of allowed analytics annotation keys
     */
    public List<String> allowedAnalyticsAnnotationsList() {
        if (allowedAnalyticsAnnotations == null || allowedAnalyticsAnnotations.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(allowedAnalyticsAnnotations.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private void minCheck(int value, int min, String name) {
        if (value < min) {
            throw new IllegalArgumentException("The " + name + " property must be greater than or equal to " + min + " but was " + value);
        }
    }

    private void positiveLongCheck(long value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException("The " + name + " property must be greater than 0 but was " + value);
        }
    }

    private void positiveDurationCheck(Duration value, String name) {
        if (value == null || !value.isPositive()) {
            throw new IllegalArgumentException("The " + name + " property must have a positive duration but was " + value);
        }
    }

    private void notBlankCheck(String str, String name) {
        if (StringUtils.isBlank(str)) {
            throw new IllegalArgumentException("The " + name + " property must not be blank");
        }
    }

    public List<MemberConfig> getMemberConfigs() {
        if (cachedMemberConfigs == null) {
            // Parse on first access if not already cached (e.g., default value)
            cachedMemberConfigs = parseAndValidateLeaderConfig(leaderConfig);
        }
        return cachedMemberConfigs;
    }

    public boolean hasMembers() {
        return !getMemberConfigs().isEmpty();
    }
}
