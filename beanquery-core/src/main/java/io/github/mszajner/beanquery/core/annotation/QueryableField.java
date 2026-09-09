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
 * Registers a single entity field in the query whitelist.
 *
 * <p>A field must carry this annotation to be usable in {@code select},
 * {@code filters} or {@code sort}; the per-capability flags narrow that further.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QueryableField {

    /** Whether the field may appear in {@code select}. */
    boolean selectable() default true;

    /** Whether the field may appear in {@code filters}. */
    boolean filterable() default true;

    /** Whether the field may appear in {@code sort}. */
    boolean sortable() default true;

    /**
     * Explicit set of allowed filter operators. An empty array (the default)
     * means "use the default set derived from the field's Java type" - see
     * {@link io.github.mszajner.beanquery.core.metadata.DefaultOperators}.
     */
    FilterOperator[] operators() default {};

    /**
     * For a {@code @ManyToOne} / {@code @OneToOne} association field: the target
     * entity's property names to expose. Each becomes its own field with a
     * depth-1 dotted path (e.g. {@code "customer.name"}), inheriting the
     * {@code selectable}/{@code filterable}/{@code sortable} flags declared here.
     *
     * <p>Must be empty on scalar fields; must be non-empty on association fields.
     * Entries may not themselves contain a dot (depth is limited to 1).
     */
    String[] nested() default {};
}
