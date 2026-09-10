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

import static io.github.mszajner.beanquery.core.metadata.FilterOperator.BETWEEN;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.EQ;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.GT;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.GTE;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.ILIKE;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.IN;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.IS_NOT_NULL;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.IS_NULL;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.LIKE;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.LT;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.LTE;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.NE;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.NOT_IN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.github.mszajner.beanquery.core.metadata.DefaultOperators;
import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.FieldKind;
import io.github.mszajner.beanquery.core.metadata.FieldMetadata;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import io.github.mszajner.beanquery.core.query.DynamicQueryExecutor;
import io.github.mszajner.beanquery.core.query.Direction;
import io.github.mszajner.beanquery.core.query.ConditionNode;
import io.github.mszajner.beanquery.core.query.FilterNode;
import io.github.mszajner.beanquery.core.query.GroupNode;
import io.github.mszajner.beanquery.core.query.LogicalOperator;
import io.github.mszajner.beanquery.core.query.FilterValueConverter;
import io.github.mszajner.beanquery.core.query.Page;
import io.github.mszajner.beanquery.core.query.PageInfo;
import io.github.mszajner.beanquery.core.query.QueryRequest;
import io.github.mszajner.beanquery.core.query.QueryResult;
import io.github.mszajner.beanquery.core.query.ResolvedFilterNode;
import io.github.mszajner.beanquery.core.query.Sort;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@Import(DynamicQueryExecutorIT.Config.class)
@TestPropertySource(properties =
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "io.github.mszajner.beanquery.core.executor.RecordingStatementInspector")
class DynamicQueryExecutorIT {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final EntityMetadata ORDER_META = new EntityMetadata("order", Order.class, List.of(
            fld("id", "id", Long.class),
            fld("reference", "reference", String.class),
            fld("amount", "amount", BigDecimal.class),
            fld("status", "status", Order.Status.class),
            fld("placedOn", "placedOn", LocalDate.class),
            fld("customer.id", "customer.id", Long.class),
            fld("customer.name", "customer.name", String.class),
            fld("customer.tier", "customer.tier", Customer.Tier.class)), List.of());

    @Autowired
    private TestEntityManager em;

    @Autowired
    private DynamicQueryExecutor executor;

    @BeforeEach
    void seed() {
        Customer c1 = new Customer(1L, "Acme Corp", Customer.Tier.GOLD);
        Customer c2 = new Customer(2L, "Acme_Corp", Customer.Tier.STANDARD);
        Customer c3 = new Customer(3L, "Acme%Corp", Customer.Tier.PLATINUM);
        em.persist(c1);
        em.persist(c2);
        em.persist(c3);

        em.persist(new Order(1L, "REF-001", new BigDecimal("100.00"), Order.Status.PAID,
                LocalDate.parse("2026-01-10"), c1));
        em.persist(new Order(2L, "REF-002", new BigDecimal("250.00"), Order.Status.NEW,
                LocalDate.parse("2026-02-15"), c1));
        em.persist(new Order(3L, "REF-003", new BigDecimal("50.00"), Order.Status.SHIPPED,
                LocalDate.parse("2026-03-20"), c2));
        em.persist(new Order(4L, "REF-004", new BigDecimal("999.99"), Order.Status.CANCELLED,
                null, c3));
        em.persist(new Order(5L, "SPECIAL", new BigDecimal("250.00"), Order.Status.PAID,
                LocalDate.parse("2026-05-01"), null));
        em.flush();
        em.clear();
    }

    // ================================================================
    // select
    // ================================================================

    @Test
    void selectReturnsOrderedMapsKeyedByRequestNames() {
        QueryResult result = executor.execute(ORDER_META,
                req(List.of("reference", "id", "amount"), List.of(), List.of(sortAsc("id")), page(0, 10)));

        assertThat(result.rows()).hasSize(5);
        assertThat(result.rows().get(0)).isInstanceOf(LinkedHashMap.class);
        assertThat(result.rows().get(0).keySet()).containsExactly("reference", "id", "amount");
        assertThat(result.rows().get(0))
                .containsExactly(entry("reference", "REF-001"), entry("id", 1L), entry("amount", new BigDecimal("100.00")));
        assertThat(result.page()).isEqualTo(new PageInfo(0, 10, 5, 1));
    }

    @Test
    void selectOfNestedPathProducesDottedKeys() {
        QueryResult result = executor.execute(ORDER_META,
                req(List.of("id", "customer.name"), List.of(), List.of(sortAsc("id")), page(0, 10)));

        assertThat(result.rows().get(0)).containsExactly(entry("id", 1L), entry("customer.name", "Acme Corp"));
        assertThat(result.rows().get(4)).containsExactly(entry("id", 5L), entry("customer.name", null));
    }

    // ================================================================
    // operators
    // ================================================================

    @Nested
    class Operators {

        @Test
        void eq() {
            assertThat(ids(filter("status", EQ, "\"PAID\""))).containsExactly(1L, 5L);
        }

        @Test
        void ne() {
            assertThat(ids(filter("status", NE, "\"PAID\""))).containsExactlyInAnyOrder(2L, 3L, 4L);
        }

        @Test
        void gt() {
            assertThat(ids(filter("amount", GT, "250"))).containsExactly(4L);
        }

        @Test
        void gte() {
            assertThat(ids(filter("amount", GTE, "250"))).containsExactlyInAnyOrder(2L, 4L, 5L);
        }

        @Test
        void lt() {
            assertThat(ids(filter("amount", LT, "100"))).containsExactly(3L);
        }

        @Test
        void lte() {
            assertThat(ids(filter("amount", LTE, "100"))).containsExactlyInAnyOrder(1L, 3L);
        }

        @Test
        void between() {
            assertThat(ids(filter("amount", BETWEEN, "[100, 250]"))).containsExactlyInAnyOrder(1L, 2L, 5L);
        }

        @Test
        void betweenOnDates() {
            assertThat(ids(filter("placedOn", BETWEEN, "[\"2026-02-01\", \"2026-04-01\"]")))
                    .containsExactlyInAnyOrder(2L, 3L);
        }

        @Test
        void in() {
            assertThat(ids(filter("status", IN, "[\"NEW\", \"SHIPPED\"]"))).containsExactlyInAnyOrder(2L, 3L);
        }

        @Test
        void notIn() {
            assertThat(ids(filter("status", NOT_IN, "[\"NEW\", \"SHIPPED\", \"CANCELLED\"]")))
                    .containsExactlyInAnyOrder(1L, 5L);
        }

        @Test
        void like() {
            assertThat(ids(filter("reference", LIKE, "\"REF\""))).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
        }

        @Test
        void likeEscapesUnderscore() {
            // "Acme_Corp" literally; must not match "Acme Corp" via the _ wildcard
            assertThat(ids(filter("customer.name", LIKE, "\"Acme_Corp\""))).containsExactly(3L);
        }

        @Test
        void likeEscapesPercent() {
            // "Acme%Corp" literally; must not match every "Acme...Corp" via the % wildcard
            assertThat(ids(filter("customer.name", LIKE, "\"Acme%Corp\""))).containsExactly(4L);
        }

        @Test
        void ilikeIsCaseInsensitive() {
            assertThat(ids(filter("customer.name", ILIKE, "\"acme corp\""))).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        void isNull() {
            assertThat(ids(filter("placedOn", IS_NULL, null))).containsExactly(4L);
        }

        @Test
        void isNotNull() {
            assertThat(ids(filter("placedOn", IS_NOT_NULL, null))).containsExactlyInAnyOrder(1L, 2L, 3L, 5L);
        }

        @Test
        void isNullOnAssociationUsesLeftJoin() {
            assertThat(ids(filter("customer.id", IS_NULL, null))).containsExactly(5L);
        }
    }

    // ================================================================
    // joins
    // ================================================================

    @Test
    void sameAssociationInSelectAndFilterIsJoinedOnce() {
        QueryResult result = executor.execute(ORDER_META, req(
                List.of("id", "customer.name"),
                List.of(f("customer.name", ILIKE, "\"acme corp\"")),
                List.of(sortAsc("id")),
                page(0, 10)));

        assertThat(result.rows()).hasSize(2);
        assertThat(ids(result)).containsExactly(1L, 2L);
        assertThat(result.rows().get(0).get("customer.name")).isEqualTo("Acme Corp");
        assertThat(result.page()).isEqualTo(new PageInfo(0, 10, 2, 1));
    }

    // ================================================================
    // sorting
    // ================================================================

    @Test
    void sortsByNestedFieldThenStabilisesById() {
        QueryResult result = executor.execute(ORDER_META, req(
                List.of("id", "customer.name"),
                List.of(f("customer.id", IS_NOT_NULL, null)),
                List.of(sortAsc("customer.name")),
                page(0, 10)));

        // "Acme Corp" (c1: orders 1,2) sorts before "Acme%Corp"/"Acme_Corp";
        // the 1,2 tie is broken by the id stabiliser
        assertThat(ids(result).subList(0, 2)).containsExactly(1L, 2L);
        assertThat(ids(result)).hasSize(4).contains(3L, 4L);
    }

    @Test
    void sortDescendingWithIdStabiliserOnTies() {
        QueryResult result = executor.execute(ORDER_META,
                req(List.of("id"), List.of(), List.of(sort("amount", Direction.DESC)), page(0, 10)));

        // 999.99, then the 250.00 tie (orders 2 and 5, id asc), then 100.00, then 50.00
        assertThat(ids(result)).containsExactly(4L, 2L, 5L, 1L, 3L);
    }

    // ================================================================
    // pagination
    // ================================================================

    @Test
    void paginatesWithTotals() {
        List<Sort> sort = List.of(sortAsc("id"));

        QueryResult first = executor.execute(ORDER_META, req(List.of("id"), List.of(), sort, page(0, 2)));
        assertThat(ids(first)).containsExactly(1L, 2L);
        assertThat(first.page()).isEqualTo(new PageInfo(0, 2, 5, 3));

        QueryResult second = executor.execute(ORDER_META, req(List.of("id"), List.of(), sort, page(1, 2)));
        assertThat(ids(second)).containsExactly(3L, 4L);
        assertThat(second.page()).isEqualTo(new PageInfo(1, 2, 5, 3));

        QueryResult third = executor.execute(ORDER_META, req(List.of("id"), List.of(), sort, page(2, 2)));
        assertThat(ids(third)).containsExactly(5L);
        assertThat(third.page()).isEqualTo(new PageInfo(2, 2, 5, 3));
    }

    @Test
    void countReflectsFiltersNotPageSize() {
        List<ConditionNode> filters = List.of(f("status", EQ, "\"PAID\""));

        QueryResult first = executor.execute(ORDER_META, req(List.of("id"), filters, List.of(sortAsc("id")), page(0, 1)));
        assertThat(ids(first)).containsExactly(1L);
        assertThat(first.page()).isEqualTo(new PageInfo(0, 1, 2, 2));

        QueryResult second = executor.execute(ORDER_META, req(List.of("id"), filters, List.of(sortAsc("id")), page(1, 1)));
        assertThat(ids(second)).containsExactly(5L);
        assertThat(second.page()).isEqualTo(new PageInfo(1, 1, 2, 2));
    }

    @Test
    void multipleFiltersAreAndCombined() {
        QueryResult result = executor.execute(ORDER_META, req(
                List.of("id"),
                List.of(f("status", EQ, "\"PAID\""), f("amount", GTE, "200")),
                List.of(sortAsc("id")),
                page(0, 10)));

        assertThat(ids(result)).containsExactly(5L);
    }

    // ================================================================
    // filter tree (AND / OR)
    // ================================================================

    @Test
    void aAndBOrC() {
        // status = PAID  AND  (amount > 200  OR  customer.name ILIKE 'acme corp')
        QueryResult result = executor.execute(ORDER_META, treeReq(
                List.of("id"),
                group(LogicalOperator.AND,
                        f("status", EQ, "\"PAID\""),
                        group(LogicalOperator.OR,
                                f("amount", GT, "200"),
                                f("customer.name", ILIKE, "\"acme corp\""))),
                List.of(sortAsc("id")),
                page(0, 10)));

        assertThat(ids(result)).containsExactly(1L, 5L);
        assertThat(result.page().totalElements()).isEqualTo(2);
    }

    @Test
    void aOrBAndCOrD() {
        // (status = NEW OR status = SHIPPED) AND (amount < 100 OR customer.tier = PLATINUM)
        QueryResult result = executor.execute(ORDER_META, treeReq(
                List.of("id"),
                group(LogicalOperator.AND,
                        group(LogicalOperator.OR,
                                f("status", EQ, "\"NEW\""),
                                f("status", EQ, "\"SHIPPED\"")),
                        group(LogicalOperator.OR,
                                f("amount", LT, "100"),
                                f("customer.tier", EQ, "\"PLATINUM\""))),
                List.of(sortAsc("id")),
                page(0, 10)));

        assertThat(ids(result)).containsExactly(3L);
        assertThat(result.page().totalElements()).isEqualTo(1);
    }

    @Test
    void nestedFieldInBothOrBranchesIsJoinedOnce() {
        RecordingStatementInspector.STATEMENTS.clear();

        QueryResult result = executor.execute(ORDER_META, treeReq(
                List.of("id", "customer.name"),
                group(LogicalOperator.OR,
                        f("customer.name", ILIKE, "\"acme corp\""),
                        f("customer.name", ILIKE, "\"acme_corp\"")),
                List.of(sortAsc("id")),
                page(0, 50)));

        assertThat(ids(result)).containsExactly(1L, 2L, 3L);
        assertThat(result.page().totalElements()).isEqualTo(3);

        List<String> orderStatements = RecordingStatementInspector.STATEMENTS.stream()
                .map(sql -> sql.toLowerCase(java.util.Locale.ROOT))
                .filter(sql -> sql.contains("exec_orders"))
                .toList();
        assertThat(orderStatements).hasSize(2); // the SELECT and the COUNT
        assertThat(orderStatements).allSatisfy(sql ->
                assertThat(countOccurrences(sql, " join ")).isEqualTo(1));
    }

    @Test
    void totalElementsMatchesTheTreeNotThePage() {
        QueryResult result = executor.execute(ORDER_META, treeReq(
                List.of("id"),
                group(LogicalOperator.OR,
                        f("status", EQ, "\"CANCELLED\""),
                        group(LogicalOperator.AND,
                                f("status", EQ, "\"PAID\""),
                                f("amount", GTE, "250"))),
                List.of(sortAsc("id")),
                page(0, 1)));

        // matches: o4 (CANCELLED), o5 (PAID & 250)  -> total 2, but page size 1
        assertThat(ids(result)).containsExactly(4L);
        assertThat(result.page()).isEqualTo(new PageInfo(0, 1, 2, 2));
    }

    @Test
    void alwaysFalsePredicateProducesEmptyResult() {
        ResolvedFilterNode tree = new ResolvedFilterNode.Group(LogicalOperator.OR,
                List.of(new ResolvedFilterNode.AlwaysFalse()));
        QueryResult result = executor.executeResolved(ORDER_META, tree, new Page(0, 10));
        assertThat(result.rows()).isEmpty();
        assertThat(result.page().totalElements()).isZero();
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    // ================================================================
    // helpers
    // ================================================================

    private QueryResult filter(String field, FilterOperator op, String json) {
        return executor.execute(ORDER_META,
                req(List.of("id"), List.of(f(field, op, json)), List.of(sortAsc("id")), page(0, 50)));
    }

    private static List<Long> ids(QueryResult result) {
        return result.rows().stream().map(row -> ((Number) row.get("id")).longValue()).toList();
    }

    private static QueryRequest req(List<String> select, List<ConditionNode> filters, List<Sort> sort, Page page) {
        return new QueryRequest(select, and(filters), sort, page);
    }

    private static QueryRequest treeReq(List<String> select, FilterNode filters, List<Sort> sort, Page page) {
        return new QueryRequest(select, filters, sort, page);
    }

    private static FilterNode and(List<ConditionNode> filters) {
        return filters.isEmpty() ? null : new GroupNode(LogicalOperator.AND, List.<FilterNode>copyOf(filters));
    }

    private static GroupNode group(LogicalOperator logic, FilterNode... children) {
        return new GroupNode(logic, List.of(children));
    }

    private static ConditionNode f(String field, FilterOperator op, String json) {
        JsonNode value = json == null ? null : MAPPER.readTree(json);
        return new ConditionNode(field, op, value);
    }

    private static Sort sortAsc(String field) {
        return new Sort(field, Direction.ASC);
    }

    private static Sort sort(String field, Direction direction) {
        return new Sort(field, direction);
    }

    private static Page page(int number, int size) {
        return new Page(number, size);
    }

    private static FieldMetadata fld(String name, String path, Class<?> type) {
        return new FieldMetadata(name, path, type, true, true, true, DefaultOperators.forType(type), FieldKind.COLUMN);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        FilterValueConverter filterValueConverter() {
            return FilterValueConverter.withDefaultConversionService();
        }

        @Bean
        DynamicQueryExecutor beanQueryExecutor(EntityManager entityManager, FilterValueConverter valueConverter) {
            return new DynamicQueryExecutor(entityManager, valueConverter);
        }
    }
}
