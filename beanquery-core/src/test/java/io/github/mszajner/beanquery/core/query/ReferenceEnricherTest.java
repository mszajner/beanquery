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
package io.github.mszajner.beanquery.core.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import io.github.mszajner.beanquery.core.metadata.ReferenceMetadata;
import io.github.mszajner.beanquery.core.reference.ReferenceResolver;
import io.github.mszajner.beanquery.core.reference.ReferenceResolvers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ReferenceEnricherTest {

    private final AtomicInteger resolveCalls = new AtomicInteger();

    private ReferenceResolvers resolvers(Map<Object, Map<String, Object>> data) {
        ReferenceResolver r = new ReferenceResolver() {
            public String referenceName() { return "customer"; }
            public Map<Object, Map<String, Object>> resolve(Set<Object> ids, Set<String> fields) {
                resolveCalls.incrementAndGet();
                Map<Object, Map<String, Object>> out = new java.util.HashMap<>();
                ids.forEach(id -> { if (data.containsKey(id)) out.put(id, data.get(id)); });
                return out;
            }
            public Optional<Set<Object>> resolveFilter(String f, FilterOperator o, Object v) { return Optional.empty(); }
        };
        return new ReferenceResolvers(List.of(r));
    }

    @Test
    void oneResolveCallSplicesValuesAndNullsMissing() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("id", 1, "customer.name", null));
        rows.add(row("id", 2, "customer.name", null));
        rows.add(row("id", 3, "customer.name", null)); // id 3 -> customerId null
        rows.add(row("id", 4, "customer.name", null)); // id 4 -> customerId 99, absent from resolver

        ReferenceEnricher enricher = new ReferenceEnricher(
                resolvers(Map.of(10L, Map.of("name", "Acme"), 11L, Map.of("name", "Beta"))));

        enricher.enrich(rows, List.of(new ReferenceEnricher.Selection(
                new ReferenceMetadata("customer", "customerId", List.of("name")),
                List.of("name"),
                java.util.Arrays.asList(10L, 11L, null, 99L))));

        assertThat(resolveCalls.get()).isEqualTo(1);
        assertThat(rows.get(0)).containsEntry("customer.name", "Acme");
        assertThat(rows.get(1)).containsEntry("customer.name", "Beta");
        assertThat(rows.get(2)).containsEntry("customer.name", null);
        assertThat(rows.get(3)).containsEntry("customer.name", null);
    }

    @Test
    void noNonNullIdsMeansNoResolveCall() {
        List<Map<String, Object>> rows = new ArrayList<>(List.of(row("id", 1, "customer.name", null)));
        new ReferenceEnricher(resolvers(Map.of())).enrich(rows, List.of(new ReferenceEnricher.Selection(
                new ReferenceMetadata("customer", "customerId", List.of("name")),
                List.of("name"), java.util.Collections.singletonList(null))));
        assertThat(resolveCalls.get()).isZero();
        assertThat(rows.get(0)).containsEntry("customer.name", null);
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
