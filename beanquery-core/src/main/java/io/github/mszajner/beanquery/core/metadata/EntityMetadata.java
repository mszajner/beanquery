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
package io.github.mszajner.beanquery.core.metadata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The whitelist for a single queryable entity: its URL identifier, the JPA
 * entity class and the registered fields.
 *
 * @param name        URL identifier ({@code /api/bq/{name}/...})
 * @param entityClass the JPA entity class
 * @param fields      registered fields, in registration order
 */
public record EntityMetadata(
        String name,
        Class<?> entityClass,
        List<FieldMetadata> fields) {

    public EntityMetadata {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(entityClass, "entityClass");
        fields = List.copyOf(fields);
    }

    /** Look up a registered field by its logical name. */
    public Optional<FieldMetadata> field(String fieldName) {
        return fields.stream().filter(f -> f.name().equals(fieldName)).findFirst();
    }

    /** Registered fields keyed by logical name, preserving registration order. */
    public Map<String, FieldMetadata> fieldsByName() {
        Map<String, FieldMetadata> map = new LinkedHashMap<>();
        for (FieldMetadata f : fields) {
            map.put(f.name(), f);
        }
        return map;
    }
}
