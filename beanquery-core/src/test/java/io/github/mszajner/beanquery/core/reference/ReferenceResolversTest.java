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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import io.github.mszajner.beanquery.core.metadata.QueryableMetadataException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReferenceResolversTest {

    static class Fake implements ReferenceResolver {
        private final String name;
        Fake(String name) { this.name = name; }
        public String referenceName() { return name; }
        public Map<Object, Map<String, Object>> resolve(Set<Object> ids, Set<String> fields) { return Map.of(); }
        public Optional<Set<Object>> resolveFilter(String f, FilterOperator o, Object v) { return Optional.empty(); }
    }

    @Test
    void indexesByReferenceName() {
        ReferenceResolvers resolvers = new ReferenceResolvers(List.of(new Fake("customer")));
        assertThat(resolvers.names()).containsExactly("customer");
        assertThat(resolvers.find("customer")).isPresent();
        assertThat(resolvers.find("nope")).isEmpty();
    }

    @Test
    void rejectsDuplicateReferenceNames() {
        assertThatThrownBy(() -> new ReferenceResolvers(List.of(new Fake("customer"), new Fake("customer"))))
                .isInstanceOf(QueryableMetadataException.class)
                .hasMessageContaining("customer");
    }

    @Test
    void requireThrowsForUnknownName() {
        assertThatThrownBy(() -> ReferenceResolvers.EMPTY.require("customer"))
                .isInstanceOf(IllegalStateException.class);
    }
}
