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
package org.deltafi.core.converters;

import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.time.Duration;

/**
 * Custom Duration reader that supports simple durations.
 *
 * The supported simple units are as follows
 *   ns for nanoseconds
 *   us for microseconds
 *   ms for milliseconds
 *   s for seconds
 *   m for minutes
 *   h for hours
 *   d for days
 */
@ReadingConverter
public class DurationReadConverter implements Converter<String, Duration> {

    @Override
    public Duration convert(String source) {
        return DurationReadConverter.doConvert(source);
    }

    public static Duration doConvert(String source) {
        DurationStyle durationStyle = DurationStyle.detect(source);
        return durationStyle.parse(source);
    }
}
