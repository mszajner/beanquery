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

import io.github.mszajner.beanquery.core.metadata.FieldMetadata;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import java.util.List;

/**
 * The filter tree after {@link FilterValueConverter} has resolved every leaf's
 * field and converted its value to the target Java type. The executor builds
 * predicates from this and never touches {@code JsonNode}.
 */
public sealed interface ResolvedFilterNode permits ResolvedFilterNode.Condition, ResolvedFilterNode.Group {

    /**
     * A resolved leaf.
     *
     * @param field the resolved field metadata (carries the property path)
     * @param op    the operator
     * @param value converted value: {@code null} for {@code IS_NULL}/{@code IS_NOT_NULL},
     *              {@code List<Object>} for {@code IN}/{@code NOT_IN}, {@link Range} for
     *              {@code BETWEEN}, otherwise a single scalar
     */
    record Condition(FieldMetadata field, FilterOperator op, Object value) implements ResolvedFilterNode {
    }

    /** A resolved group. */
    record Group(LogicalOperator logic, List<ResolvedFilterNode> children) implements ResolvedFilterNode {
    }
}
