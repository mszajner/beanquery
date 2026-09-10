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
package io.github.mszajner.beanquery.core.annotation;

import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exposes fields of an entity owned by another module without a JPA association.
 *
 * <p>Placed on a scalar foreign-key id field that also carries {@link QueryableField}.
 * Each entry in {@link #fields()} becomes a read-only, non-sortable field named
 * {@code "<name>.<field>"}; its values are supplied at query time by the
 * {@code ReferenceResolver} bean whose {@code referenceName()} equals {@link #name()}.
 * The id field itself stays a normal filterable/sortable field under its own name.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QueryableReference {

    /** Field-name prefix in the API and the key the resolver is matched on. Must be non-blank. */
    String name();

    /** Sub-field short names to expose ({@code "name"}, {@code "email"}). Non-empty, no dots. */
    String[] fields();

    /**
     * Filter operators advertised for every sub-field. Empty (default) uses
     * {@code EQ, NE, IN, NOT_IN, LIKE, ILIKE}. {@code IS_NULL} / {@code IS_NOT_NULL}
     * are always permitted and always evaluated against the local id column.
     */
    FilterOperator[] operators() default {};
}
