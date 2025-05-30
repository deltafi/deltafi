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
package org.deltafi.core.types;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    @Builder.Default
    private boolean success = true;
    @Builder.Default
    private List<String> info = new ArrayList<>();
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public static Result successResult() {
        return new Result();
    }

    public static Result combine(Stream<Result> results) {
        Result result = new Result();
        return results.reduce(result, Result::combine);
    }

    public static Result combine(Result a, Result b) {
        return Result.builder()
                .success(a.isSuccess() && b.isSuccess())
                .info(combineLists(a.getInfo(), b.getInfo()))
                .errors(combineLists(a.getErrors(), b.getErrors())).build();
    }

    private static List<String> combineLists(List<String> a, List<String> b) {
        List<String> combinedList = new ArrayList<>();
        if (blankList(a) && blankList(b)) {
            return combinedList;
        }

        if (null != a) {
            combinedList.addAll(a);
        }

        if (null != b) {
            combinedList.addAll(b);
        }

        return combinedList;
    }

    private static boolean blankList(List<String> value) {
        return null == value || value.isEmpty();
    }
}
