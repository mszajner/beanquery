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
package io.github.mszajner.beanquery.core.security;

import java.util.List;
import java.util.Set;
import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.FieldMetadata;
import io.github.mszajner.beanquery.core.metadata.ReferenceMetadata;
import io.github.mszajner.beanquery.core.query.ResolvedFilterNode;

/**
 * The aggregate of every {@link QueryAuthorizer}'s verdict for one call, produced
 * by {@link QueryAuthorizationService}.
 *
 * @param mandatoryPredicates already-resolved predicates to {@code AND} over the request
 * @param hiddenFields         field names to treat as non-existent for this call
 */
public record AppliedAuthorization(List<ResolvedFilterNode> mandatoryPredicates, Set<String> hiddenFields) {

    /** No mandatory predicates, nothing hidden - the result when no authorizer is registered. */
    public static final AppliedAuthorization ALLOW_ALL = new AppliedAuthorization(List.of(), Set.of());

    public AppliedAuthorization {
        mandatoryPredicates = List.copyOf(mandatoryPredicates);
        hiddenFields = Set.copyOf(hiddenFields);
    }

    /** {@code meta} minus the hidden fields (or {@code meta} itself when nothing is hidden). */
    public EntityMetadata visibleMetadata(EntityMetadata meta) {
        if (hiddenFields.isEmpty()) {
            return meta;
        }
        List<FieldMetadata> visible = meta.fields().stream()
                .filter(field -> !hiddenFields.contains(field.name()))
                .toList();
        List<ReferenceMetadata> references = meta.references().stream()
                .map(ref -> new ReferenceMetadata(ref.name(), ref.idFieldPath(),
                        ref.fields().stream()
                                .filter(sub -> !hiddenFields.contains(ref.name() + "." + sub))
                                .toList()))
                .filter(ref -> !ref.fields().isEmpty())
                .toList();
        return new EntityMetadata(meta.name(), meta.entityClass(), visible, references);
    }
}
