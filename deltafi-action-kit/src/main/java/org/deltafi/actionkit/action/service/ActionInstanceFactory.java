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
// ABOUTME: Creates per-thread Action instances for ActionRunner; framework starters provide their own impl.
// ABOUTME: Spring uses bean-factory cloning so @Autowired and @PostConstruct fire; plain-Java users provide a reflective impl.
package org.deltafi.actionkit.action.service;

import org.deltafi.actionkit.action.Action;

/**
 * Strategy for creating per-thread Action instances. Each DI framework starter wires its own
 * implementation — Spring uses the bean factory so cloned instances get full {@code @Autowired}
 * injection and {@code @PostConstruct} lifecycle; a plain-Java starter would supply a reflective
 * no-arg factory.
 */
@FunctionalInterface
public interface ActionInstanceFactory {

    /**
     * Build a new instance of the given action class for the specified worker thread.
     *
     * @param actionClass the action subclass to instantiate
     * @param threadIndex worker thread index (1..N-1; the singleton itself is index 0)
     * @return a fully-initialized action instance
     */
    Action<?, ?, ?> createInstance(Class<? extends Action<?, ?, ?>> actionClass, int threadIndex);
}
