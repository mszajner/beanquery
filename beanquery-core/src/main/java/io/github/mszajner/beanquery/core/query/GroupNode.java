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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An internal node of the filter tree: children combined with {@link #logic()}.
 *
 * @param logic    {@code AND} or {@code OR}
 * @param children child nodes; must be non-empty (enforced by the validator).
 *                 A {@code null} element is kept so the validator can report it.
 */
public record GroupNode(LogicalOperator logic, List<FilterNode> children) implements FilterNode {

    public GroupNode {
        children = (children == null)
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(children));
    }
}
