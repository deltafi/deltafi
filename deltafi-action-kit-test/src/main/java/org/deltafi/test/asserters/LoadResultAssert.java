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
package org.deltafi.test.asserters;

import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.load.LoadResult;
import org.deltafi.common.types.Domain;

import java.util.function.Predicate;

/**
 * Assertions for LoadResults
 */
public class LoadResultAssert extends ContentResultAssert<LoadResultAssert, LoadResult> {

    public LoadResultAssert(LoadResult loadResult) {
        super(loadResult, LoadResultAssert.class);
    }

    /**
     * Create a new LoadResultAssert with the given result
     * @param loadResult to validate
     * @return new LoadResultAssert
     */
    public static LoadResultAssert assertThat(LoadResult loadResult) {
        return new LoadResultAssert(loadResult);
    }

    /**
     * Verify that at least one Domain in the results domain list
     * satisfies the given predicate
     * @param domainMatcher predicate used to find the matching domain
     * @return this
     */
    public LoadResultAssert hasDomainMatching(Predicate<Domain> domainMatcher) {
        isNotNull();
        Assertions.assertThat(actual.getDomains()).anyMatch(domainMatcher);
        return this;
    }

    /**
     * Verify that the load result contains a domain with the given
     * name, value and mediaType
     * @param name of the expected domain
     * @param value of the expected domain
     * @param mediaType of the expected domain
     * @return this
     */
    public LoadResultAssert hasDomain(String name, String value, String mediaType) {
        return hasDomain(new Domain(name, value, mediaType));
    }

    /**
     * Verify that the load result contains the given domain
     * @param domain the expected domain
     * @return this
     */
    public LoadResultAssert hasDomain(Domain domain) {
        isNotNull();
        Assertions.assertThat(actual.getDomains()).contains(domain);
        return this;
    }

    /**
     * Verify that the load results contains exactly the given
     * list of domains
     * @param domains zero or more expected domains for the result
     * @return this
     */
    public LoadResultAssert domainsIsEqualTo(Domain ... domains) {
        isNotNull();
        Assertions.assertThat(actual.getDomains()).containsExactly(domains);
        return this;
    }
}
