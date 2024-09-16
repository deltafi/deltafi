/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.deltafi.common.constant.DeltaFiConstants.*;

@Slf4j
@RestController
public class MeRest {

    @GetMapping("me")
    public Object me(@RequestHeader(value = USER_ID_HEADER, required = false, defaultValue = "-1") String userId,
                     @RequestHeader(value = USER_NAME_HEADER, required = false, defaultValue = "Unknown") String userName,
                     @RequestHeader(value = PERMISSIONS_HEADER, required = false, defaultValue = "") String permissions) {
        return new Me(userId, userName, List.of(permissions.split(",")));
    }

    private record Me(String id, String name, List<String> permissions) {}
}
