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
package org.deltafi.core.services;

import org.springframework.stereotype.Service;
import org.springframework.util.ConcurrentReferenceHashMap;

@Service
public class DidMutexService {
    private ConcurrentReferenceHashMap<String, Object> map;

    public DidMutexService() {
        this.map = new ConcurrentReferenceHashMap<>();
    }

    public Object getMutex(String did) {
        return this.map.compute(did, (k, v) -> v == null ? new Object() : v);
    }
}