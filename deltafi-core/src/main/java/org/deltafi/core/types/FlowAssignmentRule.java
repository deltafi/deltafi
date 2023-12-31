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

import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.converters.KeyValueConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FlowAssignmentRule extends org.deltafi.core.generated.types.FlowAssignmentRule {
    public static final String INVALID_PRIORITY = "invalid priority";
    public static final String MISSING_CRITERIA = "missing match criteria";
    public static final String MISSING_FLOW_NAME = "missing flow name";
    public static final String MISSING_ID = "missing id";
    public static final String MISSING_RULE_NAME = "missing rule name";

    /**
     * Compares the SourceInfo to the rule's filenameRegex and metadata.
     *
     * @param filename input filename to match
     * @param metadata input metadata to match
     * @return true if matched, else false
     */
    public boolean matches(String filename, Map<String, String> metadata) {
        return matchesRegex(filename) && matchesRequiredMetadata(metadata);
    }

    /**
     * Validate the rule for any errors.
     *
     * @return a list of errors, or an empty list if none found
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (StringUtils.isBlank(super.getId())) {
            errors.add(MISSING_ID);
        }

        if (StringUtils.isBlank(super.getName())) {
            errors.add(MISSING_RULE_NAME);
        }

        if (StringUtils.isBlank(super.getFlow())) {
            errors.add(MISSING_FLOW_NAME);
        }

        if (super.getPriority() <= 0) {
            errors.add(INVALID_PRIORITY);
        }

        boolean noRegex = StringUtils.isBlank(getFilenameRegex());
        boolean noMeta = null == getRequiredMetadata() || getRequiredMetadata().isEmpty();
        if (noRegex && noMeta) {
            errors.add(MISSING_CRITERIA);
        }
        return errors;
    }

    boolean matchesRegex(String filename) {
        if (!StringUtils.isBlank(getFilenameRegex())) {
            Pattern pattern = Pattern.compile(getFilenameRegex());
            Matcher matcher = pattern.matcher(filename);
            return matcher.matches();
        }
        return true;
    }

    boolean matchesRequiredMetadata(Map<String, String> sourceMetadata) {
        if (getRequiredMetadata() == null || getRequiredMetadata().isEmpty()) {
            return true;
        } else if (sourceMetadata == null || sourceMetadata.isEmpty()) {
            return false;
        } else {
            return sourceMetadata.entrySet().containsAll(KeyValueConverter.convertKeyValues(getRequiredMetadata()).entrySet());
        }
    }
}
