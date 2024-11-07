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
package org.deltafi.core.types.hibernate;

import java.io.Serializable;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.usertype.UserType;

public class UUIDArrayType implements UserType<List<UUID>> {

    @Override
    public int getSqlType() {
        return Types.ARRAY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<List<UUID>> returnedClass() {
        return (Class<List<UUID>>) (Class<?>) List.class;
    }

    @Override
    public boolean equals(List<UUID> x, List<UUID> y) {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(List<UUID> x) {
        return x == null ? 0 : x.hashCode();
    }

    @Override
    public List<UUID> nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        Array array = rs.getArray(position);
        if (array == null) {
            return null;
        }
        Object[] objects = (Object[]) array.getArray();
        return Arrays.stream(objects)
                .map(Object::toString)
                .map(UUID::fromString)
                .toList();
    }

    @Override
    public void nullSafeSet(PreparedStatement st, List<UUID> value, int index, SharedSessionContractImplementor session) throws SQLException {
        if (st != null) {
            if (value != null) {
                try (var connection = session.getJdbcConnectionAccess().obtainConnection()) {
                    Array array = connection.createArrayOf("uuid", value.toArray());
                    st.setArray(index, array);
                }
            } else {
                st.setNull(index, Types.ARRAY);
            }
        }
    }

    @Override
    public List<UUID> deepCopy(List<UUID> value) {
        return value == null ? null : List.copyOf(value);
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(List<UUID> value) {
        return (Serializable) value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<UUID> assemble(Serializable cached, Object owner) {
        return (List<UUID>) cached;
    }

    @Override
    public List<UUID> replace(List<UUID> detached, List<UUID> managed, Object owner) {
        return deepCopy(detached);
    }

    @Override
    public JdbcType getJdbcType(TypeConfiguration typeConfiguration) {
        return typeConfiguration.getJdbcTypeRegistry().getDescriptor(getSqlType());
    }

    @Override
    public long getDefaultSqlLength(Dialect dialect, JdbcType jdbcType) {
        return 255L;
    }

    @Override
    public int getDefaultSqlPrecision(Dialect dialect, JdbcType jdbcType) {
        return 0;
    }

    @Override
    public int getDefaultSqlScale(Dialect dialect, JdbcType jdbcType) {
        return 0;
    }
}
