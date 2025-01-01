/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.graphql.dgs.internal.DefaultInputObjectMapper;
import com.netflix.graphql.dgs.internal.InputObjectMapper;
import kotlin.reflect.KClass;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class CustomInputObjectMapper implements InputObjectMapper {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final InputObjectMapper defaultInputObjectMapper = new DefaultInputObjectMapper();

    @NotNull
    @Override
    public <T> T mapToKotlinObject(@NotNull Map<String, ?> map, @NotNull KClass<T> kClass) {
        return defaultInputObjectMapper.mapToKotlinObject(map, kClass);
    }

    @Override
    public <T> T mapToJavaObject(@NotNull Map<String, ?> map, @NotNull Class<T> aClass) {
        return Objects.equals(aClass, SystemSnapshot.class) ?
                objectMapper.convertValue(map, aClass):
                defaultInputObjectMapper.mapToJavaObject(map, aClass);
    }

}
