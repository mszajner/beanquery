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
package io.github.mszajner.beanquery.core.web;

import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.FieldMetadata;
import io.github.mszajner.beanquery.core.web.MetadataResponse.Capabilities;
import io.github.mszajner.beanquery.core.web.MetadataResponse.FieldDescriptor;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Builds a {@link MetadataResponse} from an {@link EntityMetadata}. */
class MetadataMapper {

    private static final Map<Class<?>, Class<?>> WRAPPERS = Map.of(
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            short.class, Short.class,
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class,
            char.class, Character.class);

    MetadataResponse toResponse(EntityMetadata meta, int maxFilterDepth, int maxFilterConditions) {
        List<FieldDescriptor> fields = meta.fields().stream().map(MetadataMapper::describe).toList();
        Capabilities capabilities = new Capabilities(List.of("AND", "OR"), maxFilterDepth, maxFilterConditions);
        return new MetadataResponse(meta.name(), fields, capabilities);
    }

    private static FieldDescriptor describe(FieldMetadata field) {
        String type = simpleType(field.javaType());
        List<String> values = "enum".equals(type) ? enumValues(field.javaType()) : null;
        List<String> operators = field.allowedOperators().stream().map(Enum::name).toList();
        return new FieldDescriptor(field.name(), type, values,
                field.selectable(), field.filterable(), field.sortable(), operators, field.kind().name());
    }

    static String simpleType(Class<?> raw) {
        Class<?> type = WRAPPERS.getOrDefault(raw, raw);
        if (type.isEnum()) {
            return "enum";
        }
        if (Number.class.isAssignableFrom(type)) {
            return "number";
        }
        if (type == Boolean.class) {
            return "boolean";
        }
        if (type == LocalDate.class) {
            return "date";
        }
        if (type == LocalDateTime.class || type == Instant.class
                || type == OffsetDateTime.class || type == ZonedDateTime.class) {
            return "datetime";
        }
        return "string";
    }

    private static List<String> enumValues(Class<?> enumType) {
        return Arrays.stream(enumType.getEnumConstants()).map(c -> ((Enum<?>) c).name()).toList();
    }
}
