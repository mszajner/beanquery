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

import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.FieldKind;
import io.github.mszajner.beanquery.core.metadata.FieldMetadata;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import io.github.mszajner.beanquery.core.metadata.ReferenceMetadata;
import io.github.mszajner.beanquery.core.reference.ReferenceResolver;
import io.github.mszajner.beanquery.core.reference.ReferenceResolvers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Rewrites {@link ResolvedFilterNode.Condition}s on {@link FieldKind#REFERENCE}
 * fields into a local {@code idColumn IN (…)} condition, an {@code IS [NOT] NULL}
 * on the id column, or {@link ResolvedFilterNode.AlwaysFalse}, so the executor
 * only ever sees local columns.
 */
final class ReferenceFilterTranslator {

    private final ReferenceResolvers resolvers;
    private final int maxReferenceFilterIds;

    ReferenceFilterTranslator(ReferenceResolvers resolvers, int maxReferenceFilterIds) {
        this.resolvers = resolvers;
        this.maxReferenceFilterIds = maxReferenceFilterIds;
    }

    ResolvedFilterNode translate(ResolvedFilterNode tree, EntityMetadata meta) {
        if (tree == null) {
            return null;
        }
        List<String> errors = new ArrayList<>();
        ResolvedFilterNode out = walk(tree, meta, errors);
        if (!errors.isEmpty()) {
            throw new InvalidQueryException(errors);
        }
        return out;
    }

    private ResolvedFilterNode walk(ResolvedFilterNode node, EntityMetadata meta, List<String> errors) {
        if (node instanceof ResolvedFilterNode.Group group) {
            List<ResolvedFilterNode> children = new ArrayList<>(group.children().size());
            for (ResolvedFilterNode child : group.children()) {
                children.add(walk(child, meta, errors));
            }
            return new ResolvedFilterNode.Group(group.logic(), children);
        }
        if (node instanceof ResolvedFilterNode.AlwaysFalse) {
            return node;
        }
        ResolvedFilterNode.Condition condition = (ResolvedFilterNode.Condition) node;
        if (condition.field().kind() != FieldKind.REFERENCE) {
            return condition;
        }
        return translateCondition(condition, meta, errors);
    }

    private ResolvedFilterNode translateCondition(
            ResolvedFilterNode.Condition condition, EntityMetadata meta, List<String> errors) {

        String fieldName = condition.field().name();
        ReferenceMetadata ref = meta.referenceForField(fieldName).orElseThrow(() ->
                new IllegalStateException("no ReferenceMetadata for reference field '" + fieldName + "'"));
        FieldMetadata idField = meta.field(ref.idFieldPath()).orElseThrow(() ->
                new IllegalStateException("reference id field '" + ref.idFieldPath() + "' is not registered"));

        FilterOperator op = condition.op();
        if (op == FilterOperator.IS_NULL || op == FilterOperator.IS_NOT_NULL) {
            return new ResolvedFilterNode.Condition(idField, op, null);
        }

        String shortName = fieldName.substring(ref.name().length() + 1);
        ReferenceResolver resolver = resolvers.require(ref.name());
        Optional<Set<Object>> ids = resolver.resolveFilter(shortName, op, condition.value());
        if (ids.isEmpty()) {
            errors.add("operator " + op + " is not supported for field '" + fieldName + "'");
            return new ResolvedFilterNode.AlwaysFalse();
        }
        Set<Object> matched = ids.get();
        if (matched.isEmpty()) {
            return new ResolvedFilterNode.AlwaysFalse();
        }
        if (matched.size() > maxReferenceFilterIds) {
            errors.add("filter on '" + fieldName + "' matches too many references (> "
                    + maxReferenceFilterIds + "); narrow it");
            return new ResolvedFilterNode.AlwaysFalse();
        }
        return new ResolvedFilterNode.Condition(idField, FilterOperator.IN, List.copyOf(matched));
    }
}
