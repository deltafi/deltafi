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
package org.deltafi.core.services.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder(builderMethodName = "hiddenBuilder")
@Getter
@AllArgsConstructor
public class Event {

    public enum Severity {
        ERROR("error"),
        WARN("warn"),
        INFO("info"),
        SUCCESS("success");

        public final String label;

        Severity(String label) {
            this.label = label;
        }
    }

    public final String summary;
    @Builder.Default
    public final Severity severity = Severity.INFO;
    @Builder.Default
    public final String source = "core";
    @Builder.Default
    public final boolean notification = false;
    @Builder.Default
    public final String content = null;

    // Just to make javadocs happy
    public static class EventBuilder {
        public EventBuilder addList(String title, List<String> items) {
            if(items != null && !items.isEmpty()) {
                String blob = title + "\\n* " + String.join("\\n* ", items) + "\\n";
                if (content$set) {
                    content(content$value + "\\n" + blob);
                } else {
                    content(blob);
                }
            }
            return this;
        }
    }

    public static EventBuilder builder(String summary) {
        return hiddenBuilder().summary(summary);
    }

    public String asJson() {
        if (content == null) {
            return String.format("""
                    {
                        "summary": "%s",
                        "source": "%s",
                        "severity": "%s",
                        "notification": %s
                    }""", summary, source, severity.label, notification ? "true" : "false");
        } else {
            return String.format("""
                    {
                        "summary": "%s",
                        "content": "%s",
                        "source": "%s",
                        "severity": "%s",
                        "notification": %s
                    }""", summary, content, source, severity.label, notification ? "true" : "false");
        }
    }

}
