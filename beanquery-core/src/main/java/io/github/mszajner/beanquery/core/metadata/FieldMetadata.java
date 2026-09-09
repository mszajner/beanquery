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
package io.github.mszajner.beanquery.core.metadata;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Describes one whitelisted field of a queryable entity.
 *
 * @param name             logical name exposed to clients and used in requests
 *                         ({@code "name"}, {@code "customer.name"})
 * @param path             property path relative to the entity root, used to
 *                         build the Criteria expression ({@code "name"},
 *                         {@code "customer.name"}); depth is at most 1
 * @param javaType         the field's Java type
 * @param selectable       may appear in {@code select}
 * @param filterable       may appear in {@code filters}
 * @param sortable         may appear in {@code sort}
 * @param allowedOperators operators permitted in a filter on this field;
 *                         never {@code null}, iteration follows
 *                         {@link FilterOperator} declaration order
 */
public record FieldMetadata(
        String name,
        String path,
        Class<?> javaType,
        boolean selectable,
        boolean filterable,
        boolean sortable,
        Set<FilterOperator> allowedOperators) {

    public FieldMetadata {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(javaType, "javaType");
        allowedOperators = (allowedOperators == null || allowedOperators.isEmpty())
                ? Collections.unmodifiableSet(EnumSet.noneOf(FilterOperator.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(allowedOperators));
    }

    /** Whether {@code operator} is permitted in a filter on this field. */
    public boolean allows(FilterOperator operator) {
        return allowedOperators.contains(operator);
    }
}
