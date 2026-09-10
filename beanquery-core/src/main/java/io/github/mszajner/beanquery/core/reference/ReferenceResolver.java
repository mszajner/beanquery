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
package io.github.mszajner.beanquery.core.reference;

import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Host-implemented lookup for a {@code @QueryableReference}. Register one Spring
 * bean per reference name.
 */
public interface ReferenceResolver {

    /** Must equal the {@code name} of the {@code @QueryableReference} this serves. */
    String referenceName();

    /**
     * Batch lookup: {@code id -> (subField -> value)}. A missing id key means "no
     * such referenced row" and every sub-field renders as {@code null}. Never
     * called with an empty {@code ids} set.
     */
    Map<Object, Map<String, Object>> resolve(Set<Object> ids, Set<String> fields);

    /**
     * Translate one filter on a reference sub-field to the set of local id values
     * that match. {@code field} is the sub-field short name ({@code "name"}, not
     * {@code "customer.name"}); {@code value} is the JSON-converted value
     * ({@code String}, or {@code List<Object>} of strings for {@code IN}/{@code NOT_IN}).
     *
     * @return {@code Optional.empty()} — this field/operator pair is unsupported (HTTP 400);
     *         an empty set — supported, nothing matches (always-false predicate);
     *         a non-empty set — the matching local id values
     */
    Optional<Set<Object>> resolveFilter(String field, FilterOperator op, Object value);
}
