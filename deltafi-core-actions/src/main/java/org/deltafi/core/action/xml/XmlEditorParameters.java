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
package org.deltafi.core.action.xml;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.deltafi.core.action.ContentSelectionParameters;

import java.util.List;


/**
 * Defines the parameters for the XmlEditor action.
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class XmlEditorParameters extends ContentSelectionParameters {
    @JsonProperty(required = true)
    @JsonPropertyDescription("""    
            List of XML editor commands provided as strings.  Separate arguments with (1) one or more spaces or (2) a comma and zero or
            more spaces.  Don't escape content.  See documentation on the Internet at https://docs.deltafi.org/#/core-actions/xml-editor
            or on your local DeltaFi at http://local.deltafi.org/docs/#/core-actions/xml-editor.
            Commands:
               - Add and Replace commands: appendChild|prependChild|replaceTag|replaceTagContent <search pattern> <new content>
               - Remove commands: removeTag <search pattern>
               - Rename commands: renameTag <search pattern> <new tag name>
               - Filter and Error commands, with message length more than two characters:
                  - filterOnTag|errorOnTag <search xpath> "<message>"
                  - filterOnTag|errorOnTag not <search xpath> "<message>"
                  - filterOnTag|errorOnTag and|nand|or|nor|xor|xnor <search xpath 1> ... <search xpath n> "<message>"
            """)
    public List<String> xmlEditingCommands;

    @JsonProperty(defaultValue = "[\"*/xml\"]")
    @JsonPropertyDescription("List of allowed media types. Supports wildcards (*) and defaults to */xml.")
    @Override
    public List<String> getMediaTypes() {
        return super.getMediaTypes();
    }

    @JsonCreator
    public XmlEditorParameters(List<String> mediaTypes) {
        super(mediaTypes);
    }
}
