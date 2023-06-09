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
package org.deltafi.core.services.api.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class EventTest {

    @Test
    void defaultToJson() {
        Event sut = Event.builder("My summary").build();
        assertThat(sut.asJson(), equalTo("""
                {
                    "summary": "My summary",
                    "source": "core",
                    "severity": "info",
                    "notification": false
                }"""));
    }

    @Test
    void contentToJson() {
        Event sut = Event.builder("My summary")
                .content("My content")
                .source("My source")
                .notification(true)
                .severity(Event.Severity.SUCCESS).build();
        assertThat(sut.asJson(), equalTo("""
                {
                    "summary": "My summary",
                    "content": "My content",
                    "source": "My source",
                    "severity": "success",
                    "notification": true
                }"""));
    }

    @Test
    void noContentToJson() {
        Event sut = Event.builder("My summary")
                .source("My source")
                .notification(true)
                .severity(Event.Severity.SUCCESS).build();
        assertThat(sut.asJson(), equalTo("""
                {
                    "summary": "My summary",
                    "source": "My source",
                    "severity": "success",
                    "notification": true
                }"""));
    }

    @Test
    void listToJson() {
        Event sut = Event.builder("My summary")
                .source("My source")
                .notification(true)
                .addList("Things", List.of("thing1","thing2"))
                .severity(Event.Severity.SUCCESS).build();
        assertThat(sut.asJson(), equalTo("""
                {
                    "summary": "My summary",
                    "content": "Things\\n* thing1\\n* thing2\\n",
                    "source": "My source",
                    "severity": "success",
                    "notification": true
                }"""));
    }
}