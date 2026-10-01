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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.FieldKind;
import io.github.mszajner.beanquery.core.metadata.FieldMetadata;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import io.github.mszajner.beanquery.core.metadata.ReferenceMetadata;
import io.github.mszajner.beanquery.core.reference.ReferenceResolver;
import io.github.mszajner.beanquery.core.reference.ReferenceResolvers;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ReferenceFilterTranslatorTest {

    private final AtomicInteger resolveFilterCalls = new AtomicInteger();

    private final FieldMetadata idField = new FieldMetadata(
            "customerId", "customerId", Long.class, true, true, true,
            Set.of(FilterOperator.EQ, FilterOperator.IN, FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL),
            FieldKind.COLUMN);

    private final FieldMetadata refName = new FieldMetadata(
            "customer.name", "", String.class, true, true, false,
            Set.of(FilterOperator.EQ, FilterOperator.ILIKE, FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL),
            FieldKind.REFERENCE);

    private final EntityMetadata meta = new EntityMetadata(
            "order", Object.class, List.of(idField, refName),
            List.of(new ReferenceMetadata("customer", "customerId", List.of("name"))));

    private ReferenceResolvers resolvers(Optional<Set<Object>> filterResult) {
        ReferenceResolver resolver = new ReferenceResolver() {
            public String referenceName() {
                return "customer";
            }

            public Map<Object, Map<String, Object>> resolve(Set<Object> ids, Set<String> fields) {
                return Map.of();
            }

            public Optional<Set<Object>> resolveFilter(String f, FilterOperator o, Object v) {
                resolveFilterCalls.incrementAndGet();
                return filterResult;
            }
        };
        return new ReferenceResolvers(List.of(resolver));
    }

    private ReferenceFilterTranslator translator(Optional<Set<Object>> filterResult) {
        return new ReferenceFilterTranslator(resolvers(filterResult), 1000);
    }

    @Test
    void referenceConditionBecomesIdInList() {
        ResolvedFilterNode out = translator(Optional.of(Set.of(1L, 2L)))
                .translate(new ResolvedFilterNode.Condition(refName, FilterOperator.ILIKE, "acme"), meta);

        ResolvedFilterNode.Condition c = (ResolvedFilterNode.Condition) out;
        assertThat(c.field().name()).isEqualTo("customerId");
        assertThat(c.op()).isEqualTo(FilterOperator.IN);
        @SuppressWarnings("unchecked")
        List<Object> value = (List<Object>) c.value();
        assertThat(value).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void emptyIdSetBecomesAlwaysFalse() {
        ResolvedFilterNode out = translator(Optional.of(Set.of()))
                .translate(new ResolvedFilterNode.Condition(refName, FilterOperator.ILIKE, "zzz"), meta);
        assertThat(out).isInstanceOf(ResolvedFilterNode.AlwaysFalse.class);
    }

    @Test
    void unsupportedOperatorIsA400() {
        assertThatThrownBy(() -> translator(Optional.empty())
                .translate(new ResolvedFilterNode.Condition(refName, FilterOperator.ILIKE, "x"), meta))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining("customer.name");
    }

    @Test
    void tooManyIdsIsA400() {
        Set<Object> big = new HashSet<>();
        for (long i = 0; i < 5; i++) {
            big.add(i);
        }
        ReferenceFilterTranslator small =
                new ReferenceFilterTranslator(resolvers(Optional.of(big)), 3);
        assertThatThrownBy(() -> small.translate(
                new ResolvedFilterNode.Condition(refName, FilterOperator.ILIKE, "x"), meta))
                .isInstanceOf(InvalidQueryException.class)
                .hasMessageContaining("narrow");
    }

    @Test
    void isNullMapsToIdColumnWithoutCallingResolver() {
        ResolvedFilterNode out = translator(Optional.empty())
                .translate(new ResolvedFilterNode.Condition(refName, FilterOperator.IS_NULL, null), meta);
        ResolvedFilterNode.Condition c = (ResolvedFilterNode.Condition) out;
        assertThat(c.field().name()).isEqualTo("customerId");
        assertThat(c.op()).isEqualTo(FilterOperator.IS_NULL);
        assertThat(resolveFilterCalls.get()).isZero();
    }
}
