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
package org.deltafi.core.domain.api.types;

import org.springframework.data.annotation.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class FlowAssignmentRule extends org.deltafi.core.domain.generated.types.FlowAssignmentRule {
    public static final String INVALID_PRIORITY = "invalid priority";
    public static final String MISSING_CRITERIA = "missing match criteria";
    public static final String MISSING_FLOW_NAME = "missing flow name";
    public static final String MISSING_RULE_NAME = "missing rule name";

    @Id
    @Override
    public String getName() {
        return super.getName();
    }

    /**
     * Compares the SourceInfo to the rule's filenameRegex and metadata.
     *
     * @param sourceInfo input source info to match
     * @return true if matched, else false
     */
    public boolean matches(SourceInfo sourceInfo) {
        return matchesRegex(sourceInfo.getFilename()) &&
                matchesRequiredMetadata(sourceInfo.getMetadata());
    }

    /**
     * Validate the rule for any errors.
     *
     * @return a list of errors, or an empty list if none found
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (isBlank(super.getName())) {
            errors.add(MISSING_RULE_NAME);
        }

        if (isBlank(super.getFlow())) {
            errors.add(MISSING_FLOW_NAME);
        }

        if (super.getPriority() <= 0) {
            errors.add(INVALID_PRIORITY);
        }

        boolean noRegex = isBlank(getFilenameRegex());
        boolean noMeta = null == getRequiredMetadata() || getRequiredMetadata().isEmpty();
        if (noRegex && noMeta) {
            errors.add(MISSING_CRITERIA);
        }
        return errors;
    }

    boolean matchesRegex(String filename) {
        if (!isBlank(getFilenameRegex())) {
            Pattern pattern = Pattern.compile(getFilenameRegex());
            Matcher matcher = pattern.matcher(filename);
            return matcher.matches();
        }
        return true;
    }

    boolean matchesRequiredMetadata(List<KeyValue> sourceMetadata) {
        if ((null == getRequiredMetadata()) || (getRequiredMetadata().isEmpty())) {
            return true;
        } else if ((null == sourceMetadata) || (sourceMetadata.isEmpty())) {
            return false;
        } else {
            return sourceMetadata.containsAll(getRequiredMetadata());
        }
    }

}
