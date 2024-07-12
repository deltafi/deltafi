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

import lombok.Getter;
import org.deltafi.common.types.Property;
import org.deltafi.common.types.PropertySource;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.converters.DurationReadConverter;

import java.time.Duration;
import java.util.function.Function;

public enum PropertyType {
    AUTO_RESUME_CHECK_FREQUENCY("autoResumeCheckFrequency", "Frequency that the auto-resume check is triggered",
            DeltaFiProperties::getAutoResumeCheckFrequency) {
        @Override
        public Object convertValue(String value) {
            Duration duration = DurationReadConverter.doConvert(value);
            if (duration.toMillis() < 0) {
                throw new IllegalArgumentException("The auto-resume check frequency must be greater than 0");
            }
            return value; // store the original string value so a simple duration isn't converted to ISO-8601
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.setAutoResumeCheckFrequency(source.getAutoResumeCheckFrequency());
        }
    },
    CHECKS_ACTION_QUEUE_SIZE_THRESHOLD("checks.actionQueueSizeThreshold", "Threshold for Action Queue size check",
            props -> props.getChecks().getActionQueueSizeThreshold()) {
        @Override
        public Object convertValue(String value) {
            return convertInt(value, 0);
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getChecks().setActionQueueSizeThreshold(source.getChecks().getActionQueueSizeThreshold());
        }
    },
    CHECKS_CONTENT_STORAGE_PERCENT_THRESHOLD("checks.contentStoragePercentThreshold",
            "Threshold for content storage usage check", props -> props.getChecks().getContentStoragePercentThreshold()) {
        @Override
        public Object convertValue(String value) {
            return convertInt(value, 0, 100);
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getChecks().setContentStoragePercentThreshold(source.getChecks().getContentStoragePercentThreshold());
        }
    },
    JOIN_LOCK_CHECK_INTERVAL("join.lockCheckInterval", "Frequency that database locks on join entries are " +
            "checked against the join.maxLockDuration", props -> props.getJoin().getLockCheckInterval()) {
        @Override
        public Object convertValue(String value) {
            return convertDuration(value, "The join lock check interval must be greater than 0");
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getJoin().setLockCheckInterval(source.getJoin().getLockCheckInterval());
        }
    },
    JOIN_MAX_LOCK_DURATION("join.maxLockDuration", "Maximum duration a database lock can be held on a " +
            "join entry before it is automatically unlocked", props -> props.getJoin().getMaxLockDuration()) {
        @Override
        public Object convertValue(String value) {
            return convertDuration(value, "The join maximum lock duration must be greater than 0");
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getJoin().setMaxLockDuration(source.getJoin().getMaxLockDuration());
        }
    },
    CORE_SERVICE_THREADS("coreServiceThreads", "The number of threads used in core processing",
            DeltaFiProperties::getCoreServiceThreads) {
        @Override
        public Object convertValue(String value) {
            return convertInt(value, 1);
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.setCoreServiceThreads(source.getCoreServiceThreads());
        }
    },
    CORE_INTERNAL_QUEUE_SIZE("coreInternalQueueSize", "The number of incoming events for core to queue internally for processing",
            DeltaFiProperties::getCoreInternalQueueSize) {
        @Override
        public Object convertValue(String value) {
            return convertInt(value, 1);
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.setCoreInternalQueueSize(source.getCoreInternalQueueSize());
        }
    },
    DELETE_AGE_OFF_DAYS("delete.ageOffDays", "Number of days that a DeltaFile should live, any records older will be removed",
            props -> props.getDelete().getAgeOffDays()) {
        @Override
        public Object convertValue(String value) {
            return convertInt(value, 1);
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getDelete().setAgeOffDays(source.getDelete().getAgeOffDays());
        }
    },
    DELETE_FREQUENCY("delete.frequency", "Frequency that the delete action is triggered",
            props -> props.getDelete().getFrequency()) {
        @Override
        public Object convertValue(String value) {
            return convertDuration(value, "The delete frequency must be greater than 0");
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getDelete().setFrequency(source.getDelete().getFrequency());
        }
    },
    DELETE_POLICY_BATCH_SIZE("delete.policyBatchSize", "Maximum deletes per policy iteration loop",
            props -> props.getDelete().getPolicyBatchSize()) {
        @Override
        public Object convertValue(String value) {
            return convertInt(value, 1);
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getDelete().setPolicyBatchSize(source.getDelete().getPolicyBatchSize());
        }
    },
    DELTA_FILE_CACHE_ENABLED("deltaFileCache.enabled", "Enables or disables local caching of DeltaFiles",
            props -> props.getDeltaFileCache().isEnabled()) {
        @Override
        public Object convertValue(String value) {
            return convertBoolean(value);
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getDeltaFileCache().setEnabled(source.getDeltaFileCache().isEnabled());
        }
    },
    DELTA_FILE_CACHE_SYNC_DURATION("deltaFileCache.syncDuration", "Sync all DeltaFiles that have not been modified for this duration",
            props -> props.getDeltaFileCache().getSyncDuration()) {
        @Override
        public Object convertValue(String value) {
            return convertDuration(value, "The DeltaFile cache sync duration must be greater than 0");
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getDeltaFileCache().setSyncDuration(source.getDeltaFileCache().getSyncDuration());
        }
    },
    INGRESS_DISK_SPACE_REQUIREMENT_IN_MB("ingress.diskSpaceRequirementInMb", "The threshold for automatic disable of ingress.  If the available storage for ingress drops below this requirement, ingress will be temporarily disabled until the system frees up storage.",
            props -> props.getIngress().getDiskSpaceRequirementInMb()) {
        @Override
        public Object convertValue(String value) {
            return convertLong(value, 1);
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getIngress().setDiskSpaceRequirementInMb(source.getIngress().getDiskSpaceRequirementInMb());
        }
    },
    INGRESS_ENABLED("ingress.enabled", "Enables or disables all ingress", props -> props.getIngress().isEnabled()) {
        @Override
        public Object convertValue(String value) {
            return convertBoolean(value);
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getIngress().setEnabled(source.getIngress().isEnabled());
        }
    },
    METRICS_ENABLED("metrics.enabled", "Enable reporting of metrics to statsd/graphite",
            props -> props.getMetrics().isEnabled(), false) {
        @Override
        public Object convertValue(String value) {
            return convertBoolean(value);
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
             target.getMetrics().setEnabled(source.getMetrics().isEnabled());
        }
    },
    METRICS_ERROR_ANALYTICS_ENABLED("metrics.errorAnalyticsEnabled", "Enable reporting of error analytic metrics to Clickhouse",
            props -> props.getMetrics().isErrorAnalyticsEnabled(), false) {
        @Override
        public Object convertValue(String value) {
            return convertBoolean(value);
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
             target.getMetrics().setErrorAnalyticsEnabled(source.getMetrics().isErrorAnalyticsEnabled());
        }
    },
    PLUGINS_AUTO_ROLLBACK("plugins.autoRollback", "Rollback failed plugin deployments",
            props -> props.getPlugins().isAutoRollback()) {
        @Override
        public Object convertValue(String value) {
            return convertBoolean(value);
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getPlugins().setAutoRollback(source.getPlugins().isAutoRollback());
        }
    },
    PLUGINS_DEPLOY_TIMEOUT("plugins.deployTimeout", "Max time to wait for a plugin deployment to succeed",
            props -> props.getPlugins().getDeployTimeout()) {
        @Override
        public Object convertValue(String value) {
            return convertDuration(value, "The plugin deploy timeout must be greater than 0");
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getPlugins().setDeployTimeout(source.getPlugins().getDeployTimeout());
        }
    },
    PLUGINS_IMAGE_PULL_SECRET("plugins.imagePullSecret", "Default imagePullSecret used in plugin deployments",
            props -> props.getPlugins().getImagePullSecret()) {
        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getPlugins().setImagePullSecret(source.getPlugins().getImagePullSecret());
        }
    },
    PLUGINS_IMAGE_REPOSITORY_BASE("plugins.imageRepositoryBase", "Base of the default image repository used for plugins",
            props -> props.getPlugins().getImageRepositoryBase()) {
        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getPlugins().setImageRepositoryBase(source.getPlugins().getImageRepositoryBase());
        }
    },
    REQUEUE_DURATION("requeueDuration", "Time to wait for an action to finish processing a DeltaFile before requeuing the action",
            DeltaFiProperties::getRequeueDuration) {
        @Override
        public Object convertValue(String value) {
            return convertDuration(value, "The requeue duration must be greater than 0");
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.setRequeueDuration(source.getRequeueDuration());
        }
    },
    SCHEDULED_SERVICE_THREADS("scheduledServiceThreads", "Maximum allowed number of threads",
            DeltaFiProperties::getScheduledServiceThreads) {
        @Override
        public Object convertValue(String value) {
            return convertInt(value, 1);
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.setScheduledServiceThreads(source.getScheduledServiceThreads());
        }
    },
    SYSTEM_NAME("systemName", "Name of the DeltaFi cluster", DeltaFiProperties::getSystemName) {
        @Override
        public Object convertValue(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("The system name cannot be null or empty");
            }
            return value;
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.setSystemName(source.getSystemName());
        }
    },
    UI_SECURITY_BANNER_BACKGROUND_COLOR("ui.securityBanner.backgroundColor", "Background color of the security banner",
            props -> props.getUi().getSecurityBanner().getBackgroundColor()) {
        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getUi().getSecurityBanner().setBackgroundColor(source.getUi().getSecurityBanner().getBackgroundColor());
        }
    },
    UI_SECURITY_BANNER_ENABLED("ui.securityBanner.enabled", "Toggles the security banner display",
            props -> props.getUi().getSecurityBanner().isEnabled()) {
        @Override
        public Object convertValue(String value) {
            return convertBoolean(value);
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getUi().getSecurityBanner().setEnabled(source.getUi().getSecurityBanner().isEnabled());
        }
    },
    UI_SECURITY_BANNER_TEXT("ui.securityBanner.text", "Text to display in the security banner",
            props -> props.getUi().getSecurityBanner().getText()) {
        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getUi().getSecurityBanner().setText(source.getUi().getSecurityBanner().getText());
        }
    },
    UI_SECURITY_BANNER_TEXT_COLOR("ui.securityBanner.textColor", "Color of the text in the security banner",
            props -> props.getUi().getSecurityBanner().getTextColor()) {
        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getUi().getSecurityBanner().setTextColor(source.getUi().getSecurityBanner().getTextColor());
        }
    },
    UI_TOP_BAR_BACKGROUND_COLOR("ui.topBar.backgroundColor", "Background color of the top bar",
            props -> props.getUi().getTopBar().getBackgroundColor()) {
        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getUi().getTopBar().setBackgroundColor(source.getUi().getTopBar().getBackgroundColor());
        }
    },
    UI_TOP_BAR_TEXT_COLOR("ui.topBar.textColor", "Text color of the top bar",
            props -> props.getUi().getTopBar().getTextColor()) {
        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getUi().getTopBar().setTextColor(source.getUi().getTopBar().getTextColor());
        }
    },
    UI_USE_UTC("ui.useUTC", "Display times in UTC", props -> props.getUi().isUseUTC()) {
        @Override
        public Object convertValue(String value) {
            return convertBoolean(value);
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.getUi().setUseUTC(source.getUi().isUseUTC());
        }
    },
    IN_MEMORY_QUEUE_SIZE("inMemoryQueueSize", "Maximum size for in memory action queues before tasks are moved to on-disk queues", DeltaFiProperties::getInMemoryQueueSize) {
        @Override
        public Object convertValue(String value) {
            return convertInt(value, 10);
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.setInMemoryQueueSize(source.getInMemoryQueueSize());
        }
    },
    MAX_FLOW_DEPTH("maxFlowDepth", "The maximum number of flows a DeltaFile may traverse",
            DeltaFiProperties::getMaxFlowDepth) {
        @Override
        public Object convertValue(String value) {
            return convertInt(value, 1);
        }

        @Override
        public void copyValue(DeltaFiProperties target, DeltaFiProperties source) {
            target.setMaxFlowDepth(source.getMaxFlowDepth());
        }
    };

    private static final DeltaFiProperties DEFAULT = new DeltaFiProperties();
    public static final String INVALID_VALUE = "Invalid value: ";
    @Getter
    private final String key;
    private final String description;
    private final boolean refreshable;
    private final Function<DeltaFiProperties, Object> valueFunc;

    PropertyType(String key, String description, Function<DeltaFiProperties, Object> valueFunc) {
        this(key, description, valueFunc, true);
    }

    PropertyType(String key, String description, Function<DeltaFiProperties, Object> valueFunc, boolean refreshable) {
        this.key = key;
        this.description = description;
        this.refreshable = refreshable;
        this.valueFunc = valueFunc;
    }

    /**
     * Copy the values from the source properties into the target properties
     * @param target to copy the properties into
     * @param source to copy the properties from
     */
    public abstract void copyValue(DeltaFiProperties target, DeltaFiProperties source);

    /**
     * Take the string value and convert it to the correct type
     * @param value raw string value
     * @throws IllegalArgumentException if the value cannot be converted to the proper type or fails validation
     */
    public Object convertValue(String value) {
        return value;
    }

    public Object getProperty(DeltaFiProperties deltaFiProperties) {
        return valueFunc.apply(deltaFiProperties);
    }

    public Property toProperty(DeltaFiProperties deltaFiProperties) {
        Property property = new Property();
        property.setKey(this.key);
        property.setRefreshable(this.refreshable);
        property.setDescription(this.description);
        property.setRefreshable(refreshable);

        Object setValue = getProperty(deltaFiProperties);
        Object defaultValue = getProperty(DEFAULT);
        property.setValue(setValue != null ? setValue.toString() : null);
        property.setDefaultValue(defaultValue != null ? defaultValue.toString() : null);
        property.setPropertySource(deltaFiProperties.getSetProperties().contains(this.name()) ? PropertySource.MONGO : PropertySource.DEFAULT);
        return property;
    }

    static boolean convertBoolean(String value) {
        String downcase = value.toLowerCase();
        if (!(downcase.equals("true") || downcase.equals("false"))) {
            throw new IllegalArgumentException(INVALID_VALUE + value + ", boolean values must be true or false");
        }
        return downcase.equals("true");
    }

    static int convertInt(String value, int min) {
        int intValue = Integer.parseInt(value);
        if (intValue < min) {
            throw new IllegalArgumentException(INVALID_VALUE + value + ", integer must be greater than or equal to " + min);
        }
        return intValue;
    }

    @SuppressWarnings("SameParameterValue")
    static long convertLong(String value, int min) {
        long longValue = Long.parseLong(value);
        if (longValue < min) {
            throw new IllegalArgumentException(INVALID_VALUE + value + ", long must be greater than or equal to " + min);
        }
        return longValue;
    }

    @SuppressWarnings("SameParameterValue")
    static int convertInt(String value, int min, int max) {
        int intValue = Integer.parseInt(value);
        if (intValue < min || intValue > max) {
            throw new IllegalArgumentException(INVALID_VALUE + value + ", integer must be greater than or equal to " + min + " and less than or equal to " + max);
        }
        return intValue;
    }

    static String convertDuration(String value, String errorMessage) {
        Duration duration = DurationReadConverter.doConvert(value);
        if (duration.toMillis() < 0) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value; // store the original string value so a simple duration isn't converted to ISO-8601
    }
}
