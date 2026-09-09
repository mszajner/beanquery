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

import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * A node in the filter tree: either a {@link ConditionNode} leaf or a
 * {@link GroupNode} combining children with {@code AND} / {@code OR}.
 *
 * <p>{@link FiltersDeserializer} deduces the shape from the JSON (no {@code "type"}
 * discriminator): an object with {@code "field"} is a condition, one with
 * {@code "logic"} is a group. The legacy flat array {@code [ {...}, {...} ]} is
 * accepted too and read as {@code GroupNode(AND, [...])}.
 */
@JsonDeserialize(using = FiltersDeserializer.class)
public sealed interface FilterNode permits ConditionNode, GroupNode {
}
