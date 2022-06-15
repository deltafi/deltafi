/*
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

package org.deltafi.core.domain.api.converters;

import org.deltafi.core.domain.api.types.KeyValue;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class KeyValueConverterTest {

    @Test
    void testConvertKeyValues() {
        List<KeyValue> input = List.of(
                new KeyValue("stooge1", "Larry"),
                new KeyValue("stooge2", "Curly"),
                new KeyValue("stooge3", "Moe"));

        Map<String, String> expected = Map.of(
                "stooge1", "Larry",
                "stooge2", "Curly",
                "stooge3", "Moe");

        Map<String, String> actual = KeyValueConverter.convertKeyValues(input);

        assertThat(actual, is(expected));
    }

    @Test
    void testConvertKeyValuesWithNull() {
        List<KeyValue> input = List.of(
                new KeyValue("stooge1", "Larry"),
                new KeyValue("stooge2", "Curly"),
                new KeyValue("stooge3", null));

        Map<String, String> expected = new HashMap<>();
        expected.put("stooge1", "Larry");
        expected.put("stooge2", "Curly");
        expected.put("stooge3", null);

        Map<String, String> actual = KeyValueConverter.convertKeyValues(input);

        assertThat(actual, is(expected));
    }

    @Test
    void testFromMap() {
        Map<String, String> input = Map.of(
                "stooge1", "Larry",
                "stooge2", "Curly",
                "stooge3", "Moe");

        List<KeyValue> actual = KeyValueConverter.fromMap(input);

        assertThat(actual, containsInAnyOrder(
                new KeyValue("stooge1", "Larry"),
                new KeyValue("stooge2", "Curly"),
                new KeyValue("stooge3", "Moe")
        ));
    }

    @Test
    void testFromMapWithNull() {
        Map<String, String> input = new HashMap<>();
        input.put("stooge1", "Larry");
        input.put("stooge2", "Curly");
        input.put("stooge3", null);

        List<KeyValue> actual = KeyValueConverter.fromMap(input);

        assertThat(actual, containsInAnyOrder(
                new KeyValue("stooge1", "Larry"),
                new KeyValue("stooge2", "Curly"),
                new KeyValue("stooge3", null)
        ));
    }

}