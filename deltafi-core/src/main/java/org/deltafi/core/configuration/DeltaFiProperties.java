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

import com.networknt.schema.utils.StringUtils;
import lombok.Data;

import java.time.Duration;

/**
 * Holds the system properties. To add a new property that will be exposed
 * add the field to the class with PropertyInfo annotation. If the field
 * requires validation add a custom setter with the validation logic.
 */
@Data
@SuppressWarnings("unused")
public class DeltaFiProperties {

    @PropertyInfo(description = "Name of the DeltaFi system", defaultValue = "DeltaFi")
    private String systemName = "DeltaFi";

    @PropertyInfo(description = "[Duration or ISO 8601] Time to wait for an action to finish processing a DeltaFile before requeuing the action", defaultValue = "PT5M")
    private Duration requeueDuration = Duration.ofMinutes(5);

    @PropertyInfo(description = "[Duration or ISO 8601] Frequency that the auto-resume check is triggered", defaultValue = "PT1M")
    private Duration autoResumeCheckFrequency = Duration.ofMinutes(1);

    @PropertyInfo(description = "The number of threads used in core processing", defaultValue = "16")
    private int coreServiceThreads = 16;

    @PropertyInfo(description = "The number of incoming events for core to queue internally for processing", defaultValue = "64")
    private int coreInternalQueueSize = 64;

    @PropertyInfo(description = "Maximum allowed number of threads", defaultValue = "8")
    private int scheduledServiceThreads = 8;

    @PropertyInfo(description = "Enable reporting of metrics", defaultValue = "true", refreshable = false)
    private boolean metricsEnabled = true;

    @PropertyInfo(description = "Number of days that a DeltaFile should live, any records older will be removed", defaultValue = "13")
    private int ageOffDays = 13;

    @PropertyInfo(description = "[Duration or ISO 8601] Frequency that the delete action is triggered", defaultValue = "PT5M")
    private Duration deleteFrequency = Duration.ofMinutes(5);

    @PropertyInfo(description = "Maximum deletes per policy iteration loop", defaultValue = "1000")
    private int deletePolicyBatchSize = 1000;

    @PropertyInfo(description = "Maximum DeltaFiles to insert in a batch", defaultValue = "1000")
    private int insertBatchSize = 1000;

    @PropertyInfo(description = "[Duration or ISO 8601] Sync all DeltaFiles that have not been modified for this duration", defaultValue = "PT30S")
    private Duration cacheSyncDuration = Duration.ofSeconds(30);

    @PropertyInfo(description = "Enables or disables all ingress", defaultValue = "true")
    private boolean ingressEnabled = true;

    @PropertyInfo(description = "The threshold for automatic disable of ingress.  If the available storage for ingress drops below this requirement, ingress will be temporarily disabled until the system frees up storage.", defaultValue = "1000")
    private long ingressDiskSpaceRequirementInMb = 1000;

    @PropertyInfo(description = "Default imagePullSecret used in plugin deployments")
    private String pluginImagePullSecret;

    @PropertyInfo(description = "Rollback failed plugin deployments", defaultValue = "false")
    private boolean pluginAutoRollback = false;

    @PropertyInfo(description = "[Duration or ISO 8601] Max time to wait for a plugin deployment to succeed", defaultValue = "PT1M")
    private Duration pluginDeployTimeout = Duration.ofMinutes(1);

    @PropertyInfo(description = "[Duration or ISO 8601] Max time to allow an action to run before restarting the plugin. This must be greater than 30 seconds (or 0 to turn it off) and should be less than the requeueDuration.  By default, there is no timeout set. To turn off this feature set the value to null or 0s")
    private Duration actionExecutionTimeout;

    @PropertyInfo(description = "[Duration or ISO 8601] Minimum time to allow an action to remain running before a warning is generated (or 0 to disable).  Disabled by default. To disable this feature set the value to null or 0s")
    private Duration actionExecutionWarning;

    @PropertyInfo(description = "Threshold for Action Queue size check", defaultValue = "10")
    private int checkActionQueueSizeThreshold = 10;

    @PropertyInfo(description = "Threshold for content storage usage check", defaultValue = "90")
    private int checkContentStoragePercentThreshold = 90;

    @PropertyInfo(description = "Display times in UTC", defaultValue = "true")
    private boolean uiUseUTC = true;

    @PropertyInfo(description = "Background color of the top bar")
    private String topBarBackgroundColor;

    @PropertyInfo(description = "Text color of the top bar")
    private String topBarTextColor;

    @PropertyInfo(description = "Text to display in the security banner")
    private String securityBannerText;

    @PropertyInfo(description = "Background color of the security banner")
    private String securityBannerBackgroundColor;

    @PropertyInfo(description = "Color of the text in the security banner")
    private String securityBannerTextColor;

    @PropertyInfo(description = "Toggles the security banner display", defaultValue = "false")
    private boolean securityBannerEnabled = false;

    @PropertyInfo(description = "Maximum size for in memory action queues before tasks are moved to on-disk queues", defaultValue = "5000")
    private int inMemoryQueueSize = 5000;

    @PropertyInfo(description = "The maximum number of flows a DeltaFile may traverse", defaultValue = "32")
    private int maxFlowDepth = 32;

    @PropertyInfo(description = "The amount of time to wait before timing out while waiting to acquire the join lock", defaultValue = "30000")
    private long joinAcquireLockTimeoutMs = 30000;

    @PropertyInfo(description = "[Duration or ISO 8601] Maximum duration a database lock can be held on a " +
            "join entry before it is automatically unlocked", defaultValue = "PT1M")
    private Duration joinMaxLockDuration = Duration.ofMinutes(1);

    @PropertyInfo(description = "[Duration or ISO 8601] Frequency that database locks on join entries are " +
            "checked against the join.maxLockDuration", defaultValue = "PT1M")
    private Duration joinLockCheckInterval = Duration.ofMinutes(1);

    @PropertyInfo(description = "Enable pg_squeeze extension. Postgres must be manually restarted if this is changed from true to false", defaultValue = "false")
    private boolean autoCleanPostgres = false;

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
        minCheck(maxFlowDepth,  1,"maxFlowDepth");
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
}
