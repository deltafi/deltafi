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
package org.deltafi.test.asserters;

import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.load.LoadResult;
import org.deltafi.common.types.Domain;

import java.util.function.Predicate;

public class LoadResultAssert extends ContentResultAssert<LoadResultAssert, LoadResult> {

    public LoadResultAssert(LoadResult loadResult) {
        super(loadResult, LoadResultAssert.class);
    }

    public static LoadResultAssert assertThat(LoadResult loadResult) {
        return new LoadResultAssert(loadResult);
    }

    public LoadResultAssert hasDomainMatching(Predicate<Domain> domainMatcher) {
        isNotNull();
        Assertions.assertThat(actual.getDomains()).anyMatch(domainMatcher);
        return this;
    }

    public LoadResultAssert hasDomain(String name, String value, String mediaType) {
        return hasDomain(new Domain(name, value, mediaType));
    }

    public LoadResultAssert hasDomain(Domain domain) {
        isNotNull();
        Assertions.assertThat(actual.getDomains()).contains(domain);
        return this;
    }

    public LoadResultAssert domainsIsEqualTo(Domain ... domains) {
        isNotNull();
        Assertions.assertThat(actual.getDomains()).containsExactly(domains);
        return this;
    }
}
