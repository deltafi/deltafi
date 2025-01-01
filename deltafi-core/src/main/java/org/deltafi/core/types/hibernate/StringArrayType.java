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

import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.usertype.UserType;

public class StringArrayType implements UserType<List<String>> {

    @Override
    public int getSqlType() {
        return Types.ARRAY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<List<String>> returnedClass() {
        return (Class<List<String>>) (Class<?>) List.class;
    }

    @Override
    public boolean equals(List<String> x, List<String> y) {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(List<String> x) {
        return x == null ? 0 : x.hashCode();
    }

    @Override
    public List<String> nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        Array array = rs.getArray(position);
        if (array == null) {
            return null;
        }
        return Arrays.asList((String[]) array.getArray());
    }

    @Override
    public void nullSafeSet(PreparedStatement st, List<String> value, int index, SharedSessionContractImplementor session) throws SQLException {
        if (st != null) {
            if (value != null) {
                try (var connection = session.getJdbcConnectionAccess().obtainConnection()) {
                    Array array = connection.createArrayOf("text", value.toArray());
                    st.setArray(index, array);
                }
            } else {
                st.setNull(index, Types.ARRAY);
            }
        }
    }

    @Override
    public List<String> deepCopy(List<String> value) {
        return value == null ? null : List.copyOf(value);
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(List<String> value) {
        return (Serializable) value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> assemble(Serializable cached, Object owner) {
        return (List<String>) cached;
    }

    @Override
    public List<String> replace(List<String> detached, List<String> managed, Object owner) {
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
