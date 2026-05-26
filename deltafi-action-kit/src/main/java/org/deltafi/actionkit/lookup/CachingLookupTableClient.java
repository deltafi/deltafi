/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
// ABOUTME: LookupTableClient with Spring-managed caching layered over lookup queries.
// ABOUTME: Overrides only lookup() and clearCache() with cache annotations; everything else delegates.
package org.deltafi.actionkit.lookup;

import com.netflix.graphql.dgs.client.GraphQLClient;
import org.deltafi.common.http.HttpService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

/**
 * {@link LookupTableClient} with Spring-managed caching on lookup queries. The cache annotations are
 * the only Spring-coupled part of the lookup client, isolated here so the base client stays Spring-free.
 */
public class CachingLookupTableClient extends LookupTableClient {

    public CachingLookupTableClient(String lookupUrl, GraphQLClient graphQLClient, HttpService httpService) {
        super(lookupUrl, graphQLClient, httpService);
    }

    @Cacheable("lookup-table-client-cache")
    @Override
    public LookupResults lookup(String lookupTableName, LookupOptions lookupOptions) throws Exception {
        return super.lookup(lookupTableName, lookupOptions);
    }

    @CacheEvict(value = "lookup-table-client-cache", allEntries = true)
    @Override
    public void clearCache() {
        super.clearCache();
    }
}
