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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.FieldMetadata;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class QueryRequestValidatorTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_DEPTH = 3;
    private static final int MAX_CONDITIONS = 5;

    private static final EntityMetadata META = new EntityMetadata("widget", Object.class, List.of(
            field("id", Long.class, true, true, true,
                    EQ, NE, GT, GTE, LT, LTE, BETWEEN, IN, IS_NULL, IS_NOT_NULL),
            field("name", String.class, true, true, true,
                    EQ, NE, LIKE, ILIKE, IN, IS_NULL, IS_NOT_NULL),
            field("price", BigDecimal.class, true, true, true,
                    EQ, NE, GT, GTE, LT, LTE, BETWEEN, IN, IS_NULL, IS_NOT_NULL),
            field("tag", String.class, true, true, true,
                    EQ, NE, IN, NOT_IN, IS_NULL, IS_NOT_NULL),
            field("createdAt", Instant.class, true, true, true,
                    EQ, NE, GT, GTE, LT, LTE, BETWEEN, IN, IS_NULL, IS_NOT_NULL),
            field("secret", String.class, false, false, false),
            field("label", String.class, true, false, true, EQ, NE),
            field("rank", Integer.class, true, true, false, EQ, NE, GT, LT)));

    private final QueryRequestValidator validator =
            new QueryRequestValidator(MAX_PAGE_SIZE, MAX_DEPTH, MAX_CONDITIONS);

    // ================================================================
    // select
    // ================================================================

    @Nested
    class SelectMustBeNonEmpty {

        @Test
        void emptyList() {
            assertThat(errorsOf(request(List.of(), noFilters(), noSort(), page(0, 10))))
                    .contains("select: must not be empty");
        }

        @Test
        void nullListIsNormalisedThenRejected() {
            assertThat(errorsOf(new QueryRequest(null, null, null, page(0, 10))))
                    .contains("select: must not be empty");
        }
    }

    @Nested
    class SelectFieldMustExist {

        @Test
        void unknownField() {
            assertThat(errorsOf(select("ghost"))).contains("select: unknown field 'ghost'");
        }

        @Test
        void unknownFieldAmongKnownOnes() {
            assertThat(errorsOf(select("id", "nope")))
                    .contains("select: unknown field 'nope'")
                    .doesNotContain("select: unknown field 'id'");
        }

        @Test
        void blankFieldName() {
            assertThat(errorsOf(select("  "))).contains("select[0]: field name must not be blank");
        }
    }

    @Nested
    class SelectFieldMustBeSelectable {

        @Test
        void notSelectableAlone() {
            assertThat(errorsOf(select("secret"))).contains("select: field 'secret' is not selectable");
        }

        @Test
        void notSelectableNextToSelectableOne() {
            assertThat(errorsOf(select("id", "secret"))).contains("select: field 'secret' is not selectable");
        }
    }

    // ================================================================
    // filter leaves (rules unchanged, path is now "filters.children[0]")
    // ================================================================

    @Nested
    class FilterFieldMustExist {

        @Test
        void unknownField() {
            assertThat(errorsOf(oneFilter(cond("ghost", EQ, text("x")))))
                    .contains("filters.children[0]: unknown field 'ghost'");
        }

        @Test
        void blankField() {
            assertThat(errorsOf(oneFilter(cond(" ", EQ, text("x")))))
                    .contains("filters.children[0]: field name must not be blank");
        }

        @Test
        void nullChildEntry() {
            List<FilterNode> children = new ArrayList<>();
            children.add(null);
            QueryRequest request = new QueryRequest(List.of("id"),
                    new GroupNode(LogicalOperator.AND, children), noSort(), page(0, 10));
            assertThat(errorsOf(request)).contains("filters.children[0]: must not be null");
        }
    }

    @Nested
    class FilterFieldMustBeFilterable {

        @Test
        void selectableButNotFilterable() {
            assertThat(errorsOf(oneFilter(cond("label", EQ, text("x")))))
                    .contains("filters.children[0]: field 'label' is not filterable");
        }

        @Test
        void fullyHiddenField() {
            assertThat(errorsOf(oneFilter(cond("secret", EQ, text("x")))))
                    .contains("filters.children[0]: field 'secret' is not filterable");
        }
    }

    @Nested
    class FilterOperatorMustBeAllowed {

        @Test
        void likeOnNumericField() {
            assertThat(errorsOf(oneFilter(cond("id", LIKE, text("x")))))
                    .anyMatch(e -> e.contains("operator LIKE is not allowed for field 'id'"));
        }

        @Test
        void greaterThanOnStringField() {
            assertThat(errorsOf(oneFilter(cond("name", GT, text("x")))))
                    .anyMatch(e -> e.contains("operator GT is not allowed for field 'name'"));
        }

        @Test
        void nullOperator() {
            assertThat(errorsOf(oneFilter(cond("name", null, text("x")))))
                    .contains("filters.children[0]: operator must not be null");
        }
    }

    @Nested
    class FilterValueShapeMustMatchOperator {

        @Test
        void betweenNeedsExactlyTwoElements() {
            assertThat(errorsOf(oneFilter(cond("price", BETWEEN, json("[1]")))))
                    .anyMatch(e -> e.contains("operator BETWEEN requires an array of exactly 2 non-null values"));
            assertThat(errorsOf(oneFilter(cond("price", BETWEEN, json("[1,2,3]")))))
                    .anyMatch(e -> e.contains("operator BETWEEN requires an array of exactly 2 non-null values"));
        }

        @Test
        void betweenRejectsNullElement() {
            assertThat(errorsOf(oneFilter(cond("price", BETWEEN, json("[1,null]")))))
                    .anyMatch(e -> e.contains("operator BETWEEN requires an array of exactly 2 non-null values"));
        }

        @Test
        void inNeedsNonEmptyArray() {
            assertThat(errorsOf(oneFilter(cond("name", IN, text("a")))))
                    .anyMatch(e -> e.contains("operator IN requires a non-empty array value"));
            assertThat(errorsOf(oneFilter(cond("name", IN, json("[]")))))
                    .anyMatch(e -> e.contains("operator IN requires a non-empty array value"));
        }

        @Test
        void inRejectsNullElement() {
            assertThat(errorsOf(oneFilter(cond("tag", NOT_IN, json("[\"a\",null]")))))
                    .anyMatch(e -> e.contains("operator NOT_IN array must not contain null"));
        }

        @Test
        void isNullMustNotCarryValue() {
            assertThat(errorsOf(oneFilter(cond("name", IS_NULL, text("x")))))
                    .anyMatch(e -> e.contains("operator IS_NULL must not carry a value"));
        }

        @Test
        void scalarOperatorRequiresValue() {
            assertThat(errorsOf(oneFilter(cond("name", EQ, null))))
                    .anyMatch(e -> e.contains("operator EQ requires a value"));
            assertThat(errorsOf(oneFilter(cond("name", EQ, json("null")))))
                    .anyMatch(e -> e.contains("operator EQ requires a value"));
        }

        @Test
        void scalarOperatorRejectsArrayOrObject() {
            assertThat(errorsOf(oneFilter(cond("name", EQ, json("[1,2]")))))
                    .anyMatch(e -> e.contains("operator EQ requires a single scalar value"));
            assertThat(errorsOf(oneFilter(cond("name", NE, json("{\"a\":1}")))))
                    .anyMatch(e -> e.contains("operator NE requires a single scalar value"));
        }

        @Test
        void wellFormedValuesProduceNoErrors() {
            QueryRequest request = request(List.of("id"), List.of(
                    cond("price", BETWEEN, json("[1,10]")),
                    cond("name", IN, json("[\"a\",\"b\"]")),
                    cond("tag", NOT_IN, json("[\"x\"]")),
                    cond("name", IS_NULL, null),
                    cond("createdAt", IS_NOT_NULL, json("null"))), noSort(), page(0, 10));

            assertThatCode(() -> validator.validate(request, META)).doesNotThrowAnyException();
        }
    }

    // ================================================================
    // filter tree structure
    // ================================================================

    @Nested
    class GroupRules {

        @Test
        void emptyChildrenIsRejected() {
            QueryRequest request = new QueryRequest(List.of("id"),
                    new GroupNode(LogicalOperator.AND, List.of()), noSort(), page(0, 10));
            assertThat(errorsOf(request)).contains("filters.children: must not be empty");
        }

        @Test
        void nullLogicIsRejected() {
            QueryRequest request = new QueryRequest(List.of("id"),
                    new GroupNode(null, List.of(cond("id", EQ, json("1")))), noSort(), page(0, 10));
            assertThat(errorsOf(request)).anyMatch(e -> e.contains("filters.logic: must be AND or OR"));
        }

        @Test
        void nestedGroupErrorCarriesFullPath() {
            QueryRequest request = new QueryRequest(List.of("id"),
                    new GroupNode(LogicalOperator.AND, List.of(
                            cond("id", EQ, json("1")),
                            new GroupNode(LogicalOperator.OR, List.of(
                                    cond("name", EQ, text("ok")),
                                    cond("secret", EQ, text("x")))))),
                    noSort(), page(0, 10));

            assertThat(errorsOf(request))
                    .contains("filters.children[1].children[1]: field 'secret' is not filterable");
        }

        @Test
        void depthWithinLimitIsAccepted() {
            // AND( OR( AND( id EQ 1 ) ) ) -> 3 groups deep, limit is 3
            QueryRequest request = new QueryRequest(List.of("id"),
                    grp(LogicalOperator.AND, grp(LogicalOperator.OR,
                            grp(LogicalOperator.AND, cond("id", EQ, json("1"))))),
                    noSort(), page(0, 10));
            assertThatCode(() -> validator.validate(request, META)).doesNotThrowAnyException();
        }

        @Test
        void depthBeyondLimitIsRejected() {
            // 4 groups deep, limit is 3
            QueryRequest request = new QueryRequest(List.of("id"),
                    grp(LogicalOperator.AND, grp(LogicalOperator.AND, grp(LogicalOperator.AND,
                            grp(LogicalOperator.AND, cond("id", EQ, json("1")))))),
                    noSort(), page(0, 10));
            assertThat(errorsOf(request))
                    .anyMatch(e -> e.contains("filter nesting exceeds the maximum depth of 3"));
        }

        @Test
        void tooManyConditionsIsRejected() {
            List<FilterNode> many = new ArrayList<>();
            for (int i = 0; i < MAX_CONDITIONS + 2; i++) {
                many.add(cond("id", EQ, json("1")));
            }
            QueryRequest request = new QueryRequest(List.of("id"),
                    new GroupNode(LogicalOperator.OR, many), noSort(), page(0, 10));
            assertThat(errorsOf(request))
                    .anyMatch(e -> e.contains("7 conditions, which exceeds the maximum of 5"));
        }

        @Test
        void conditionCountAtLimitIsAccepted() {
            List<FilterNode> exactly = new ArrayList<>();
            for (int i = 0; i < MAX_CONDITIONS; i++) {
                exactly.add(cond("id", EQ, json("1")));
            }
            QueryRequest request = new QueryRequest(List.of("id"),
                    new GroupNode(LogicalOperator.OR, exactly), noSort(), page(0, 10));
            assertThatCode(() -> validator.validate(request, META)).doesNotThrowAnyException();
        }
    }

    // ================================================================
    // sort
    // ================================================================

    @Nested
    class SortFieldMustExist {

        @Test
        void unknownField() {
            assertThat(errorsOf(sort(new Sort("ghost", Direction.ASC)))).contains("sort: unknown field 'ghost'");
        }

        @Test
        void blankField() {
            assertThat(errorsOf(sort(new Sort("", Direction.DESC)))).contains("sort[0]: field name must not be blank");
        }
    }

    @Nested
    class SortFieldMustBeSortable {

        @Test
        void notSortable() {
            assertThat(errorsOf(sort(new Sort("rank", Direction.ASC)))).contains("sort: field 'rank' is not sortable");
        }

        @Test
        void fullyHiddenField() {
            assertThat(errorsOf(sort(new Sort("secret", Direction.DESC))))
                    .contains("sort: field 'secret' is not sortable");
        }
    }

    // ================================================================
    // page
    // ================================================================

    @Nested
    class PageSizeMustBeInRange {

        @Test
        void zeroIsTooSmall() {
            assertThat(errorsOf(request(List.of("id"), noFilters(), noSort(), page(0, 0))))
                    .contains("page.size: must be between 1 and 100");
        }

        @Test
        void aboveMaxIsRejected() {
            assertThat(errorsOf(request(List.of("id"), noFilters(), noSort(), page(0, MAX_PAGE_SIZE + 1))))
                    .contains("page.size: must be between 1 and 100");
        }

        @Test
        void boundariesAreAccepted() {
            assertThatCode(() -> validator.validate(
                    request(List.of("id"), noFilters(), noSort(), page(0, 1)), META)).doesNotThrowAnyException();
            assertThatCode(() -> validator.validate(
                    request(List.of("id"), noFilters(), noSort(), page(0, MAX_PAGE_SIZE)), META))
                    .doesNotThrowAnyException();
        }

        @Test
        void missingPageIsRejected() {
            assertThat(errorsOf(request(List.of("id"), noFilters(), noSort(), null)))
                    .contains("page: must be provided");
        }

        @Test
        void negativePageNumberIsRejected() {
            assertThat(errorsOf(request(List.of("id"), noFilters(), noSort(), page(-1, 10))))
                    .contains("page.number: must be >= 0");
        }
    }

    // ================================================================
    // cross-cutting
    // ================================================================

    @Test
    void validTreeRequestPasses() {
        QueryRequest request = request(
                List.of("id", "name", "price"),
                List.of(cond("name", LIKE, text("%acme%")),
                        cond("price", BETWEEN, json("[10,20]")),
                        cond("id", IS_NOT_NULL, null)),
                List.of(new Sort("price", Direction.DESC), new Sort("id", Direction.ASC)),
                page(2, 50));

        assertThatCode(() -> validator.validate(request, META)).doesNotThrowAnyException();
    }

    @Test
    void noFilterIsValid() {
        assertThatCode(() -> validator.validate(
                new QueryRequest(List.of("id"), null, noSort(), page(0, 10)), META))
                .doesNotThrowAnyException();
    }

    @Test
    void allErrorsAreCollectedNotJustTheFirst() {
        QueryRequest request = request(
                List.of("ghost", "secret"),
                List.of(cond("label", EQ, text("x")), cond("id", LIKE, text("x"))),
                List.of(new Sort("rank", Direction.ASC)),
                page(-1, 9999));

        List<String> errors = errorsOf(request);

        assertThat(errors).hasSizeGreaterThanOrEqualTo(6);
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("unknown field 'ghost'"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("'secret' is not selectable"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("'label' is not filterable"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("operator LIKE is not allowed"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("'rank' is not sortable"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("page.number: must be >= 0"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("page.size: must be between 1 and 100"));
    }

    @Test
    void exceptionMessageJoinsAllErrors() {
        assertThatExceptionOfType(InvalidQueryException.class)
                .isThrownBy(() -> validator.validate(select("ghost", "secret"), META))
                .satisfies(ex -> {
                    assertThat(ex.getErrors()).hasSize(2);
                    assertThat(ex.getMessage())
                            .contains("unknown field 'ghost'")
                            .contains("'secret' is not selectable");
                });
    }

    @Test
    void constructorRejectsNonPositiveLimits() {
        assertThatIllegalArgumentException().isThrownBy(() -> new QueryRequestValidator(0));
        assertThatIllegalArgumentException().isThrownBy(() -> new QueryRequestValidator(10, 0, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> new QueryRequestValidator(10, 5, 0));
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThatNullPointerException().isThrownBy(() -> validator.validate(null, META));
        assertThatNullPointerException().isThrownBy(() -> validator.validate(select("id"), null));
    }

    // ================================================================
    // helpers
    // ================================================================

    private List<String> errorsOf(QueryRequest request) {
        try {
            validator.validate(request, META);
            return List.of();
        } catch (InvalidQueryException e) {
            return e.getErrors();
        }
    }

    private static QueryRequest request(List<String> select, List<ConditionNode> filters, List<Sort> sort, Page page) {
        FilterNode node = filters.isEmpty() ? null : new GroupNode(LogicalOperator.AND, List.<FilterNode>copyOf(filters));
        return new QueryRequest(select, node, sort, page);
    }

    private static QueryRequest select(String... fields) {
        return new QueryRequest(Arrays.asList(fields), null, noSort(), page(0, 10));
    }

    private static QueryRequest oneFilter(ConditionNode condition) {
        return new QueryRequest(List.of("id"),
                new GroupNode(LogicalOperator.AND, List.of(condition)), noSort(), page(0, 10));
    }

    private static QueryRequest sort(Sort sort) {
        return new QueryRequest(List.of("id"), null, List.of(sort), page(0, 10));
    }

    private static ConditionNode cond(String field, FilterOperator op, JsonNode value) {
        return new ConditionNode(field, op, value);
    }

    private static GroupNode grp(LogicalOperator logic, FilterNode... children) {
        return new GroupNode(logic, List.of(children));
    }

    private static List<ConditionNode> noFilters() {
        return List.of();
    }

    private static List<Sort> noSort() {
        return List.of();
    }

    private static Page page(int number, int size) {
        return new Page(number, size);
    }

    private static JsonNode text(String value) {
        return MAPPER.getNodeFactory().stringNode(value);
    }

    private static JsonNode json(String raw) {
        return MAPPER.readTree(raw);
    }

    private static FieldMetadata field(String name, Class<?> type,
            boolean selectable, boolean filterable, boolean sortable, FilterOperator... operators) {
        Set<FilterOperator> ops = operators.length == 0
                ? Set.of()
                : EnumSet.copyOf(Arrays.asList(operators));
        return new FieldMetadata(name, name, type, selectable, filterable, sortable, ops);
    }
}
