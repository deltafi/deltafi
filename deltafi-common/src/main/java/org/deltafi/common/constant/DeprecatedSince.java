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
package org.deltafi.common.constant;

/**
 * Single source of truth for the {@code @Deprecated(since = ...)} value used by the Spring-removal
 * (springless) bridge classes. These are all scheduled for removal in 3.0. While this branch is in
 * flight a new 2.x release may cut before it merges; bump {@link #VERSION_2_55_1} here in one place rather
 * than editing every annotation. Delete this class along with the bridges in 3.0.
 */
public class DeprecatedSince {

    private DeprecatedSince() {}

    /** Release in which the springless bridge classes were deprecated for removal in 3.0. */
    public static final String VERSION_2_55_1 = "2.55.1";
}
