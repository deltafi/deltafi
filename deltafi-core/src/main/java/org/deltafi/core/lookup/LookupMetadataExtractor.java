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
package org.deltafi.core.lookup;

import org.deltafi.core.lookup.LookupMetadataExtractor.LookupMetadata;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

public class LookupMetadataExtractor implements ResultSetExtractor<LookupMetadata> {

    @Override
    public @Nullable LookupMetadata extractData(ResultSet rs) throws SQLException, DataAccessException {
        if (rs.next()) {
            return new LookupMetadata(rs.getObject("created", OffsetDateTime.class), rs.getObject("modified", OffsetDateTime.class));
        }
        return null;
    }

    public record LookupMetadata(OffsetDateTime created, OffsetDateTime modified) {}
}
