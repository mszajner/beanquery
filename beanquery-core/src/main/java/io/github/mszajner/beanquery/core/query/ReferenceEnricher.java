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

import io.github.mszajner.beanquery.core.metadata.ReferenceMetadata;
import io.github.mszajner.beanquery.core.reference.ReferenceResolver;
import io.github.mszajner.beanquery.core.reference.ReferenceResolvers;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fills {@code REFERENCE} row keys via a single batched {@link ReferenceResolver#resolve}
 * call per reference per page.
 */
final class ReferenceEnricher {

    /**
     * @param reference the reference being enriched
     * @param subFields the sub-field short names requested in {@code select}
     * @param idPerRow  the local id value for each row, index-aligned with the row list
     *                  ({@code null} where the row's FK column is null)
     */
    record Selection(ReferenceMetadata reference, List<String> subFields, List<Object> idPerRow) {
    }

    private final ReferenceResolvers resolvers;

    ReferenceEnricher(ReferenceResolvers resolvers) {
        this.resolvers = resolvers;
    }

    void enrich(List<Map<String, Object>> rows, List<Selection> selections) {
        for (Selection selection : selections) {
            enrichOne(rows, selection);
        }
    }

    private void enrichOne(List<Map<String, Object>> rows, Selection selection) {
        String prefix = selection.reference().name() + ".";
        Set<Object> ids = new LinkedHashSet<>();
        for (Object id : selection.idPerRow()) {
            if (id != null) {
                ids.add(id);
            }
        }

        Map<Object, Map<String, Object>> resolved = ids.isEmpty()
                ? Map.of()
                : resolvers.require(selection.reference().name())
                        .resolve(ids, Set.copyOf(selection.subFields()));

        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            Object id = selection.idPerRow().get(i);
            Map<String, Object> values = id == null ? null : resolved.get(id);
            for (String sub : selection.subFields()) {
                row.put(prefix + sub, values == null ? null : values.get(sub));
            }
        }
    }
}
