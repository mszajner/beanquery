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

import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import tools.jackson.databind.JsonNode;

/**
 * A leaf of the filter tree: one field / operator / value clause.
 *
 * <p>{@code value} stays a raw {@link JsonNode} because its shape depends on the
 * operator and its type on the target field:
 * <ul>
 *   <li>{@code BETWEEN} &rarr; a 2-element array {@code [lo, hi]}</li>
 *   <li>{@code IN} / {@code NOT_IN} &rarr; a non-empty array</li>
 *   <li>{@code IS_NULL} / {@code IS_NOT_NULL} &rarr; no value (may be {@code null})</li>
 *   <li>everything else &rarr; a single scalar</li>
 * </ul>
 *
 * @param field logical field name; must be registered and {@code filterable}
 * @param op    the operator; must be in the field's {@code allowedOperators}
 * @param value operator-dependent payload, or {@code null}
 */
public record ConditionNode(String field, FilterOperator op, JsonNode value) implements FilterNode {
}
