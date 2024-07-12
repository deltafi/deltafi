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
package org.deltafi.actionkit.action.egress;

import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Metric;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class EgressResultTest {

    final String DESTINATION = "overThere";
    final long BYTES_EGRESSED = 42;

    final ActionContext actionContext = new ActionContext(UUID.randomUUID(), "mySourceFilename", "dataSource", "myFlow", 0, "myName", 0,
            "myHostName", "myActionVersion", null, null, null, null, null, null);
    final EgressResult sut = new EgressResult(actionContext, DESTINATION, BYTES_EGRESSED);
    
    @Test
    void testDefaultCustomMetrics() {
        List<Metric> metrics = sut.getCustomMetrics();

        assertThat(metrics, containsInAnyOrder(
                Metric.builder().name("files_out").value(1).build().addTag("endpoint", DESTINATION),
                Metric.builder().name("bytes_out").value(BYTES_EGRESSED).build().addTag("endpoint", DESTINATION)
        ));
    }

    @Test
    void testAdditionalCustomMetrics() {
        Metric additionalMetric = new Metric("wig_out", 9).addTag("starlord", "was here");
        sut.add(additionalMetric);
        List<Metric> metrics = sut.getCustomMetrics();

        assertThat(metrics, containsInAnyOrder(
                Metric.builder().name("files_out").value(1).build().addTag("endpoint", DESTINATION),
                Metric.builder().name("bytes_out").value(BYTES_EGRESSED).build().addTag("endpoint", DESTINATION),
                additionalMetric
        ));
    }

}