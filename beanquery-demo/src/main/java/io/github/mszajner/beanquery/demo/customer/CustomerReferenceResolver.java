/*
 * Copyright 2026 Mirosław Szajner
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.mszajner.beanquery.demo.customer;

import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import io.github.mszajner.beanquery.core.reference.ReferenceResolver;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Serves {@code order.customer.*} from the {@code customer} table. The {@code order}
 * module never sees this class or {@link Customer}.
 */
@Component
public class CustomerReferenceResolver implements ReferenceResolver {

    private final JdbcTemplate jdbc;

    public CustomerReferenceResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String referenceName() {
        return "customer";
    }

    @Override
    public Map<Object, Map<String, Object>> resolve(Set<Object> ids, Set<String> fields) {
        String placeholders = ids.stream().map(i -> "?").collect(Collectors.joining(","));
        Map<Object, Map<String, Object>> out = new HashMap<>();
        jdbc.query("SELECT id, name, tier FROM customer WHERE id IN (" + placeholders + ")",
                rs -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("name", rs.getString("name"));
                    row.put("tier", rs.getString("tier"));
                    out.put(rs.getLong("id"), row);
                },
                ids.toArray());
        return out;
    }

    @Override
    public Optional<Set<Object>> resolveFilter(String field, FilterOperator op, Object value) {
        String column = switch (field) {
            case "name" -> "name";
            case "tier" -> "tier";
            default -> null;
        };
        if (column == null) {
            return Optional.empty();
        }
        List<Long> ids = switch (op) {
            case EQ -> jdbc.queryForList(
                    "SELECT id FROM customer WHERE " + column + " = ?", Long.class, value);
            case ILIKE -> jdbc.queryForList(
                    "SELECT id FROM customer WHERE LOWER(" + column + ") LIKE ?",
                    Long.class, "%" + ((String) value).toLowerCase(Locale.ROOT) + "%");
            default -> null;
        };
        return ids == null ? Optional.empty() : Optional.of(new HashSet<>(ids));
    }
}
