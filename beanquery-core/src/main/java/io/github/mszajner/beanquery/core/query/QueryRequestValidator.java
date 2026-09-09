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

import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.FieldMetadata;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Validates a {@link QueryRequest} against an entity's {@link EntityMetadata},
 * the configured maximum page size and the filter-tree limits.
 *
 * <p>The filter tree is walked recursively; leaves follow the same rules as the
 * old flat filters (field exists, filterable, operator allowed, value shape).
 * Every violation is collected with its path in the tree
 * (e.g. {@code filters.children[1].children[0]}); nothing is fail-fast.
 */
public class QueryRequestValidator {

    static final int DEFAULT_MAX_FILTER_DEPTH = 5;
    static final int DEFAULT_MAX_FILTER_CONDITIONS = 50;

    private final int maxPageSize;
    private final int maxFilterDepth;
    private final int maxFilterConditions;

    public QueryRequestValidator(int maxPageSize) {
        this(maxPageSize, DEFAULT_MAX_FILTER_DEPTH, DEFAULT_MAX_FILTER_CONDITIONS);
    }

    public QueryRequestValidator(int maxPageSize, int maxFilterDepth, int maxFilterConditions) {
        if (maxPageSize < 1) {
            throw new IllegalArgumentException("maxPageSize must be >= 1, was " + maxPageSize);
        }
        if (maxFilterDepth < 1) {
            throw new IllegalArgumentException("maxFilterDepth must be >= 1, was " + maxFilterDepth);
        }
        if (maxFilterConditions < 1) {
            throw new IllegalArgumentException("maxFilterConditions must be >= 1, was " + maxFilterConditions);
        }
        this.maxPageSize = maxPageSize;
        this.maxFilterDepth = maxFilterDepth;
        this.maxFilterConditions = maxFilterConditions;
    }

    public int getMaxFilterDepth() {
        return maxFilterDepth;
    }

    public int getMaxFilterConditions() {
        return maxFilterConditions;
    }

    /**
     * @throws InvalidQueryException if the request breaks any rule
     */
    public void validate(QueryRequest request, EntityMetadata metadata) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(metadata, "metadata");

        List<String> errors = new ArrayList<>();
        validateSelect(request.select(), metadata, errors);
        validateFilters(request.filters(), metadata, errors);
        validateSort(request.sort(), metadata, errors);
        validatePage(request.page(), errors);

        if (!errors.isEmpty()) {
            throw new InvalidQueryException(errors);
        }
    }

    // -- select ----------------------------------------------------------

    private void validateSelect(List<String> select, EntityMetadata metadata, List<String> errors) {
        if (select.isEmpty()) {
            errors.add("select: must not be empty");
            return;
        }
        for (int i = 0; i < select.size(); i++) {
            String field = select.get(i);
            if (isBlank(field)) {
                errors.add("select[" + i + "]: field name must not be blank");
                continue;
            }
            Optional<FieldMetadata> fm = metadata.field(field);
            if (fm.isEmpty()) {
                errors.add("select: unknown field '" + field + "'");
            } else if (!fm.get().selectable()) {
                errors.add("select: field '" + field + "' is not selectable");
            }
        }
    }

    // -- filters (tree) ------------------------------------------------

    private void validateFilters(FilterNode root, EntityMetadata metadata, List<String> errors) {
        if (root == null) {
            return;
        }
        int[] leafCount = {0};
        validateNode(root, "filters", 1, metadata, errors, leafCount);
        if (leafCount[0] > maxFilterConditions) {
            errors.add("filters: the request has " + leafCount[0]
                    + " conditions, which exceeds the maximum of " + maxFilterConditions);
        }
    }

    private void validateNode(FilterNode node, String path, int depth,
            EntityMetadata metadata, List<String> errors, int[] leafCount) {

        if (node == null) {
            errors.add(path + ": must not be null");
            return;
        }
        if (node instanceof GroupNode group) {
            if (depth > maxFilterDepth) {
                errors.add(path + ": filter nesting exceeds the maximum depth of " + maxFilterDepth);
                return;
            }
            if (group.logic() == null) {
                errors.add(path + ".logic: must be AND or OR");
            }
            List<FilterNode> children = group.children();
            if (children.isEmpty()) {
                errors.add(path + ".children: must not be empty");
                return;
            }
            for (int i = 0; i < children.size(); i++) {
                validateNode(children.get(i), path + ".children[" + i + "]", depth + 1, metadata, errors, leafCount);
            }
            return;
        }
        leafCount[0]++;
        validateCondition((ConditionNode) node, path, metadata, errors);
    }

    private void validateCondition(ConditionNode condition, String path,
            EntityMetadata metadata, List<String> errors) {

        String field = condition.field();
        if (isBlank(field)) {
            errors.add(path + ": field name must not be blank");
            return;
        }
        Optional<FieldMetadata> fm = metadata.field(field);
        if (fm.isEmpty()) {
            errors.add(path + ": unknown field '" + field + "'");
            return;
        }
        if (!fm.get().filterable()) {
            errors.add(path + ": field '" + field + "' is not filterable");
            return;
        }
        FilterOperator op = condition.op();
        if (op == null) {
            errors.add(path + ": operator must not be null");
            return;
        }
        if (!fm.get().allows(op)) {
            errors.add(path + ": operator " + op + " is not allowed for field '" + field
                    + "' (allowed: " + fm.get().allowedOperators() + ")");
            return;
        }
        validateValueShape(condition.value(), op, path, errors);
    }

    private void validateValueShape(JsonNode value, FilterOperator op, String path, List<String> errors) {
        boolean present = value != null && !value.isNull();
        switch (op) {
            case IS_NULL, IS_NOT_NULL -> {
                if (present) {
                    errors.add(path + ": operator " + op + " must not carry a value");
                }
            }
            case IN, NOT_IN -> {
                if (!present || !value.isArray() || value.isEmpty()) {
                    errors.add(path + ": operator " + op + " requires a non-empty array value");
                } else if (containsNull(value)) {
                    errors.add(path + ": operator " + op + " array must not contain null");
                }
            }
            case BETWEEN -> {
                if (!present || !value.isArray() || value.size() != 2 || containsNull(value)) {
                    errors.add(path + ": operator BETWEEN requires an array of exactly 2 non-null values");
                }
            }
            default -> {
                if (!present) {
                    errors.add(path + ": operator " + op + " requires a value");
                } else if (!value.isValueNode()) {
                    errors.add(path + ": operator " + op + " requires a single scalar value");
                }
            }
        }
    }

    // -- sort ----------------------------------------------------------

    private void validateSort(List<Sort> sort, EntityMetadata metadata, List<String> errors) {
        for (int i = 0; i < sort.size(); i++) {
            Sort clause = sort.get(i);
            if (clause == null) {
                errors.add("sort[" + i + "]: must not be null");
                continue;
            }
            String field = clause.field();
            if (isBlank(field)) {
                errors.add("sort[" + i + "]: field name must not be blank");
                continue;
            }
            Optional<FieldMetadata> fm = metadata.field(field);
            if (fm.isEmpty()) {
                errors.add("sort: unknown field '" + field + "'");
            } else if (!fm.get().sortable()) {
                errors.add("sort: field '" + field + "' is not sortable");
            }
        }
    }

    // -- page --------------------------------------------------------

    private void validatePage(Page page, List<String> errors) {
        if (page == null) {
            errors.add("page: must be provided");
            return;
        }
        if (page.number() < 0) {
            errors.add("page.number: must be >= 0");
        }
        if (page.size() < 1 || page.size() > maxPageSize) {
            errors.add("page.size: must be between 1 and " + maxPageSize);
        }
    }

    // -- helpers ----------------------------------------------------

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean containsNull(JsonNode array) {
        for (JsonNode element : array) {
            if (element == null || element.isNull()) {
                return true;
            }
        }
        return false;
    }
}
