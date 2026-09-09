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

/**
 * The complete vocabulary of filter operators a query request may use.
 *
 * <p>Which operators are actually allowed for a given field is decided per field
 * ({@link FieldMetadata#allowedOperators()}), either explicitly via
 * {@code @QueryableField(operators = ...)} or derived from the field type by
 * {@link DefaultOperators}.
 */
public enum FilterOperator {

    /** Equal. */
    EQ,
    /** Not equal. */
    NE,
    /** Greater than. */
    GT,
    /** Greater than or equal. */
    GTE,
    /** Less than. */
    LT,
    /** Less than or equal. */
    LTE,
    /** SQL {@code LIKE}, case sensitive. */
    LIKE,
    /** Case-insensitive {@code LIKE}. */
    ILIKE,
    /** Value in a set. */
    IN,
    /** Value not in a set. */
    NOT_IN,
    /** Value within an inclusive lower/upper bound pair. */
    BETWEEN,
    /** Field is {@code null}. */
    IS_NULL,
    /** Field is not {@code null}. */
    IS_NOT_NULL
}
