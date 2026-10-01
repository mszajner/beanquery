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
package io.github.mszajner.beanquery.core.executor;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.FieldKind;
import io.github.mszajner.beanquery.core.metadata.FieldMetadata;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import io.github.mszajner.beanquery.core.metadata.ReferenceMetadata;
import io.github.mszajner.beanquery.core.query.DynamicQueryExecutor;
import io.github.mszajner.beanquery.core.query.FilterValueConverter;
import io.github.mszajner.beanquery.core.query.Page;
import io.github.mszajner.beanquery.core.query.QueryRequest;
import io.github.mszajner.beanquery.core.query.QueryResult;
import io.github.mszajner.beanquery.core.reference.ReferenceResolver;
import io.github.mszajner.beanquery.core.reference.ReferenceResolvers;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class ReferenceExecutorIT {

    @Autowired
    private EntityManager em;

    private final AtomicInteger resolveCalls = new AtomicInteger();
    private DynamicQueryExecutor executor;

    // -- fixture metadata ------------------------------------------------
    private final FieldMetadata id = new FieldMetadata("id", "id", Long.class,
            true, true, true, Set.of(FilterOperator.EQ), FieldKind.COLUMN);
    private final FieldMetadata customerId = new FieldMetadata("customerId", "customerId", Long.class,
            true, true, true, Set.of(FilterOperator.EQ, FilterOperator.IN, FilterOperator.IS_NULL), FieldKind.COLUMN);
    private final FieldMetadata custName = new FieldMetadata("customer.name", "", String.class,
            true, true, false, Set.of(FilterOperator.ILIKE, FilterOperator.IS_NULL), FieldKind.REFERENCE);
    private final EntityMetadata meta = new EntityMetadata("refOrder", RefOrder.class,
            List.of(id, customerId, custName),
            List.of(new ReferenceMetadata("customer", "customerId", List.of("name"))));

    @BeforeEach
    void setUp() {
        em.persist(new RefOrder(1L, 10L));
        em.persist(new RefOrder(2L, 11L));
        em.persist(new RefOrder(3L, null));
        em.flush();

        ReferenceResolver resolver = new ReferenceResolver() {
            public String referenceName() { return "customer"; }
            public Map<Object, Map<String, Object>> resolve(Set<Object> ids, Set<String> fields) {
                resolveCalls.incrementAndGet();
                Map<Object, Map<String, Object>> data = Map.of(
                        10L, Map.of("name", "Acme"),
                        11L, Map.of("name", "Beta"));
                Map<Object, Map<String, Object>> out = new java.util.HashMap<>();
                ids.forEach(i -> { if (data.containsKey(i)) out.put(i, data.get(i)); });
                return out;
            }
            public Optional<Set<Object>> resolveFilter(String f, FilterOperator o, Object v) {
                if (o != FilterOperator.ILIKE) return Optional.empty();
                String needle = ((String) v).toLowerCase();
                Set<Object> ids = new java.util.HashSet<>();
                if ("acme".contains(needle)) ids.add(10L);
                if ("beta".contains(needle)) ids.add(11L);
                return Optional.of(ids);
            }
        };
        executor = new DynamicQueryExecutor(em, FilterValueConverter.withDefaultConversionService(),
                new ReferenceResolvers(List.of(resolver)), 1000);
    }

    @Test
    void selectEnrichesReferenceFieldsWithOneResolveCall() {
        QueryResult result = executor.execute(meta, new QueryRequest(
                List.of("id", "customer.name"), null, List.of(), new Page(0, 10)));

        assertThat(resolveCalls.get()).isEqualTo(1);
        assertThat(result.rows()).extracting(r -> r.get("customer.name"))
                .containsExactlyInAnyOrder("Acme", "Beta", null);
        assertThat(result.rows().get(0)).doesNotContainKey("customerId"); // id column stays internal
        assertThat(result.rows().get(0).keySet()).containsExactly("id", "customer.name"); // select order
    }

    @Test
    void filterOnReferenceFieldNarrowsRowsAndCount() {
        QueryResult result = executor.execute(meta, new QueryRequest(
                List.of("id"),
                new io.github.mszajner.beanquery.core.query.ConditionNode(
                        "customer.name", FilterOperator.ILIKE,
                        new tools.jackson.databind.node.StringNode("acme")),
                List.of(), new Page(0, 10)));

        assertThat(result.page().totalElements()).isEqualTo(1);
        assertThat(result.rows()).extracting(r -> r.get("id")).containsExactly(1L);
    }

    @Test
    void alwaysFalseFromEmptyReferenceBranchDoesNotSuppressAnOrSibling() {
        QueryResult result = executor.execute(meta, new QueryRequest(
                List.of("id"),
                new io.github.mszajner.beanquery.core.query.GroupNode(
                        io.github.mszajner.beanquery.core.query.LogicalOperator.OR,
                        List.of(
                                new io.github.mszajner.beanquery.core.query.ConditionNode(
                                        "customer.name", FilterOperator.ILIKE,
                                        new tools.jackson.databind.node.StringNode("no-such-customer")),
                                new io.github.mszajner.beanquery.core.query.ConditionNode(
                                        "id", FilterOperator.EQ,
                                        tools.jackson.databind.node.IntNode.valueOf(1)))),
                List.of(), new Page(0, 10)));

        assertThat(result.rows()).extracting(r -> r.get("id")).containsExactly(1L);
        assertThat(result.page().totalElements()).isEqualTo(1);
    }

    @Test
    void fkIdColumnExplicitlyInSelectIsReturnedOnceAlongsideEnrichedReferenceField() {
        QueryResult result = executor.execute(meta, new QueryRequest(
                List.of("customerId", "customer.name"), null, List.of(), new Page(0, 10)));

        assertThat(resolveCalls.get()).isEqualTo(1);

        Map<Object, Object> nameByCustomerId = new java.util.HashMap<>();
        result.rows().forEach(r -> nameByCustomerId.put(r.get("customerId"), r.get("customer.name")));
        assertThat(nameByCustomerId)
                .containsEntry(10L, "Acme")
                .containsEntry(11L, "Beta")
                .containsEntry(null, null);
        assertThat(result.rows().get(0).keySet()).containsExactly("customerId", "customer.name");
    }

    @Test
    void filterResolvingToNoIdsReturnsZeroRowsAndZeroTotal() {
        QueryResult result = executor.execute(meta, new QueryRequest(
                List.of("id"),
                new io.github.mszajner.beanquery.core.query.ConditionNode(
                        "customer.name", FilterOperator.ILIKE,
                        new tools.jackson.databind.node.StringNode("nomatch")),
                List.of(), new Page(0, 10)));
        assertThat(result.rows()).isEmpty();
        assertThat(result.page().totalElements()).isZero();
    }
}
