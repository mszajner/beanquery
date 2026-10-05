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
package io.github.mszajner.beanquery.starter.refit;

import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import io.github.mszajner.beanquery.core.reference.ReferenceResolver;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reads the {@code customers} table directly - no JPA association from {@code ref_order}. */
public class CountingCustomerResolver implements ReferenceResolver {

    public final AtomicInteger resolveCalls = new AtomicInteger();
    public final AtomicInteger resolveFilterCalls = new AtomicInteger();

    private final JdbcTemplate jdbc;

    public CountingCustomerResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String referenceName() {
        return "customer";
    }

    @Override
    public Map<Object, Map<String, Object>> resolve(Set<Object> ids, Set<String> fields) {
        resolveCalls.incrementAndGet();
        Map<Object, Map<String, Object>> out = new HashMap<>();
        String placeholders = String.join(",", ids.stream().map(i -> "?").toList());
        jdbc.query("SELECT id, name, tier FROM customers WHERE id IN (" + placeholders + ")",
                ids.toArray(),
                rs -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("name", rs.getString("name"));
                    row.put("tier", rs.getString("tier"));
                    out.put(rs.getLong("id"), row);
                });
        return out;
    }

    @Override
    public Optional<Set<Object>> resolveFilter(String field, FilterOperator op, Object value) {
        resolveFilterCalls.incrementAndGet();
        if (!"name".equals(field) || op != FilterOperator.ILIKE) {
            return Optional.empty();
        }
        String needle = "%" + ((String) value).toLowerCase(Locale.ROOT) + "%";
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM customers WHERE LOWER(name) LIKE ?", Long.class, needle);
        return Optional.of(new HashSet<>(ids));
    }
}
