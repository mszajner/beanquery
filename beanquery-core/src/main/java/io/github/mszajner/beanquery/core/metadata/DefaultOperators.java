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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Maps a field's Java type to the default set of {@link FilterOperator}s, used
 * when {@code @QueryableField(operators = {})} is left empty.
 *
 * <ul>
 *   <li>{@code String} &rarr; EQ, NE, LIKE, ILIKE, IN, IS_NULL, IS_NOT_NULL</li>
 *   <li>{@code Number} (incl. primitives), {@code LocalDate},
 *       {@code LocalDateTime}, {@code Instant} &rarr;
 *       EQ, NE, GT, GTE, LT, LTE, BETWEEN, IN, IS_NULL, IS_NOT_NULL</li>
 *   <li>{@code Boolean} &rarr; EQ, NE, IS_NULL, IS_NOT_NULL</li>
 *   <li>{@code Enum} &rarr; EQ, NE, IN, NOT_IN, IS_NULL, IS_NOT_NULL</li>
 * </ul>
 *
 * <p>Any other type yields an empty set - such a field cannot be filtered until
 * operators are declared explicitly on the annotation.
 */
public final class DefaultOperators {

    private static final Set<FilterOperator> STRING_OPERATORS =
            immutable(EQ, NE, LIKE, ILIKE, IN, IS_NULL, IS_NOT_NULL);

    private static final Set<FilterOperator> COMPARABLE_OPERATORS =
            immutable(EQ, NE, GT, GTE, LT, LTE, BETWEEN, IN, IS_NULL, IS_NOT_NULL);

    private static final Set<FilterOperator> BOOLEAN_OPERATORS =
            immutable(EQ, NE, IS_NULL, IS_NOT_NULL);

    private static final Set<FilterOperator> ENUM_OPERATORS =
            immutable(EQ, NE, IN, NOT_IN, IS_NULL, IS_NOT_NULL);

    private static final Set<FilterOperator> NO_OPERATORS =
            Collections.unmodifiableSet(EnumSet.noneOf(FilterOperator.class));

    private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPERS = Map.of(
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            short.class, Short.class,
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class,
            char.class, Character.class);

    private DefaultOperators() {
    }

    /**
     * Returns the default operator set for {@code type}. Primitive types are
     * treated as their wrapper. Never {@code null}; the returned set is
     * unmodifiable and iterates in {@link FilterOperator} declaration order.
     *
     * @throws NullPointerException if {@code type} is {@code null}
     */
    public static Set<FilterOperator> forType(Class<?> type) {
        Objects.requireNonNull(type, "type");
        Class<?> t = PRIMITIVE_WRAPPERS.getOrDefault(type, type);

        if (t == String.class) {
            return STRING_OPERATORS;
        }
        if (t == Boolean.class) {
            return BOOLEAN_OPERATORS;
        }
        if (Number.class.isAssignableFrom(t)
                || t == LocalDate.class
                || t == LocalDateTime.class
                || t == Instant.class) {
            return COMPARABLE_OPERATORS;
        }
        if (Enum.class.isAssignableFrom(t)) {
            return ENUM_OPERATORS;
        }
        return NO_OPERATORS;
    }

    private static Set<FilterOperator> immutable(FilterOperator... operators) {
        EnumSet<FilterOperator> set = EnumSet.noneOf(FilterOperator.class);
        Collections.addAll(set, operators);
        return Collections.unmodifiableSet(set);
    }
}
