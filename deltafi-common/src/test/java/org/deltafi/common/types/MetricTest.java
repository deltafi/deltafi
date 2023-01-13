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
package org.deltafi.common.types;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

class MetricTest {

    @Test
    void metricName() {
        Metric sut = new Metric("myName", 42);
        assertThat(sut.metricName(), equalTo("myName"));
        assertThat(sut.getTags(), equalTo(Map.of()));
    }

    @Test
    void taggyMetricName() {
        Metric sut = new Metric("myName", 42);
        sut.addTag("z", "1");
        assertThat(sut.metricName(), equalTo("myName;z=1"));
        sut.addTag("a", "2");
        assertThat(sut.metricName(), equalTo("myName;a=2;z=1"));
        sut.addTag("f", "3");
        assertThat(sut.metricName(), equalTo("myName;a=2;f=3;z=1"));
        assertThat(sut.getTags(), equalTo(Map.of("a","2","z","1","f","3")));
    }

    @Test
    void addTags() {
        Metric sut = new Metric("my.name.is", 42)
                .addTags(Map.of("a","2","z","1","f","3"))
                .addTag("foo", "bar");
        assertThat(sut.metricName(), equalTo("my.name.is;a=2;f=3;foo=bar;z=1"));
    }
}