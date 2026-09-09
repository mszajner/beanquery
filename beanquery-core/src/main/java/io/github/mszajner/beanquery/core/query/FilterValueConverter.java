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
import io.github.mszajner.beanquery.core.metadata.FieldMetadata;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import tools.jackson.databind.JsonNode;

/**
 * Converts the raw {@link JsonNode} carried by a {@link Filter} into the target
 * field's Java type ({@link FieldMetadata#javaType()}).
 *
 * <p>Handled explicitly (ISO-8601 / by-name): {@link LocalDate}, {@link LocalDateTime},
 * {@link Instant}, {@link BigDecimal}, {@link BigInteger}, {@link UUID} and enums
 * (matched by name, case-insensitive). Everything else is delegated to the Spring
 * {@link ConversionService}.
 *
 * <p>Shape follows the operator:
 * <ul>
 *   <li>{@code IN} / {@code NOT_IN} &rarr; {@code List<Object>}</li>
 *   <li>{@code BETWEEN} &rarr; {@link Range}</li>
 *   <li>{@code IS_NULL} / {@code IS_NOT_NULL} &rarr; {@code null}</li>
 *   <li>everything else &rarr; a single {@code Object}</li>
 * </ul>
 *
 * <p>A value that cannot be converted raises {@link FilterValueConversionException}
 * whose message names the field, the expected type and the received value.
 */
public class FilterValueConverter {

    private static final Map<Class<?>, Class<?>> WRAPPERS = Map.of(
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            short.class, Short.class,
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class,
            char.class, Character.class);

    private final ConversionService conversionService;

    public FilterValueConverter(ConversionService conversionService) {
        this.conversionService = Objects.requireNonNull(conversionService, "conversionService");
    }

    /** A converter backed by Spring's shared {@link DefaultConversionService}. */
    public static FilterValueConverter withDefaultConversionService() {
        return new FilterValueConverter(DefaultConversionService.getSharedInstance());
    }

    /**
     * Convert {@code value} according to {@code operator}.
     *
     * @return {@code null} for {@code IS_NULL}/{@code IS_NOT_NULL}, a
     *         {@code List<Object>} for {@code IN}/{@code NOT_IN}, a {@link Range}
     *         for {@code BETWEEN}, otherwise the single converted value
     * @throws FilterValueConversionException if any element cannot be converted
     */
    /**
     * Walk a {@link FilterNode} tree, resolving each leaf's field against
     * {@code entity} and converting its value. Conversion happens only in leaves;
     * groups are copied structurally.
     *
     * @return the resolved tree, or {@code null} if {@code node} is {@code null}
     * @throws FilterValueConversionException carrying every leaf that failed
     */
    public ResolvedFilterNode resolve(FilterNode node, EntityMetadata entity) {
        Objects.requireNonNull(entity, "entity");
        if (node == null) {
            return null;
        }
        List<String> errors = new ArrayList<>();
        ResolvedFilterNode resolved = resolveNode(node, entity, errors);
        if (!errors.isEmpty()) {
            throw new FilterValueConversionException(errors);
        }
        return resolved;
    }

    private ResolvedFilterNode resolveNode(FilterNode node, EntityMetadata entity, List<String> errors) {
        if (node instanceof GroupNode group) {
            List<ResolvedFilterNode> children = new ArrayList<>(group.children().size());
            for (FilterNode child : group.children()) {
                if (child != null) {
                    ResolvedFilterNode resolvedChild = resolveNode(child, entity, errors);
                    if (resolvedChild != null) {
                        children.add(resolvedChild);
                    }
                }
            }
            return new ResolvedFilterNode.Group(group.logic(), children);
        }
        ConditionNode condition = (ConditionNode) node;
        FieldMetadata field = entity.field(condition.field()).orElse(null);
        if (field == null) {
            errors.add("filter references unknown field '" + condition.field() + "'");
            return null;
        }
        if (condition.op() == null) {
            errors.add("filter on '" + condition.field() + "': operator must not be null");
            return null;
        }
        try {
            Object value = convert(field, condition.op(), condition.value());
            return new ResolvedFilterNode.Condition(field, condition.op(), value);
        } catch (FilterValueConversionException e) {
            errors.addAll(e.getErrors());
            return null;
        }
    }

    /**
     * Convert {@code value} according to {@code operator}.
     *
     * @return {@code null} for {@code IS_NULL}/{@code IS_NOT_NULL}, a
     *         {@code List<Object>} for {@code IN}/{@code NOT_IN}, a {@link Range}
     *         for {@code BETWEEN}, otherwise the single converted value
     * @throws FilterValueConversionException if any element cannot be converted
     */
    public Object convert(FieldMetadata field, FilterOperator operator, JsonNode value) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(operator, "operator");
        return switch (operator) {
            case IS_NULL, IS_NOT_NULL -> null;
            case IN, NOT_IN -> convertList(field, value);
            case BETWEEN -> convertRange(field, value);
            default -> convertSingle(field, value);
        };
    }

    /** Convert a single scalar value to {@code field.javaType()}. */
    public Object convertSingle(FieldMetadata field, JsonNode value) {
        return convertElement(field, value);
    }

    /** Convert every element of a JSON array to {@code field.javaType()}. */
    public List<Object> convertList(FieldMetadata field, JsonNode value) {
        if (value == null || !value.isArray()) {
            throw new FilterValueConversionException(message(field, value) + " (expected a JSON array)");
        }
        List<Object> converted = new ArrayList<>(value.size());
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < value.size(); i++) {
            try {
                converted.add(convertElement(field, value.get(i)));
            } catch (FilterValueConversionException e) {
                errors.addAll(e.getErrors());
            }
        }
        if (!errors.isEmpty()) {
            throw new FilterValueConversionException(errors);
        }
        return converted;
    }

    /** Convert the two elements of a {@code [lo, hi]} JSON array. */
    public Range convertRange(FieldMetadata field, JsonNode value) {
        if (value == null || !value.isArray() || value.size() != 2) {
            throw new FilterValueConversionException(
                    message(field, value) + " (expected a 2-element JSON array)");
        }
        List<String> errors = new ArrayList<>();
        Object lower = tryConvert(field, value.get(0), errors);
        Object upper = tryConvert(field, value.get(1), errors);
        if (!errors.isEmpty()) {
            throw new FilterValueConversionException(errors);
        }
        return new Range(lower, upper);
    }

    // -- internals ---------------------------------------------------------

    private Object tryConvert(FieldMetadata field, JsonNode node, List<String> errors) {
        try {
            return convertElement(field, node);
        } catch (FilterValueConversionException e) {
            errors.addAll(e.getErrors());
            return null;
        }
    }

    private Object convertElement(FieldMetadata field, JsonNode node) {
        if (node == null || node.isNull()) {
            throw new FilterValueConversionException(message(field, node));
        }
        if (node.isArray() || node.isObject()) {
            throw new FilterValueConversionException(message(field, node));
        }

        Class<?> target = field.javaType();
        Class<?> boxed = WRAPPERS.getOrDefault(target, target);
        String raw = node.asString();
        try {
            return coerce(boxed, target, raw);
        } catch (FilterValueConversionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FilterValueConversionException(message(field, node, e));
        }
    }

    private Object coerce(Class<?> boxed, Class<?> target, String raw) {
        String trimmed = raw.trim();
        if (boxed == String.class) {
            return raw;
        }
        if (boxed == UUID.class) {
            return UUID.fromString(trimmed);
        }
        if (boxed == LocalDate.class) {
            return LocalDate.parse(trimmed);
        }
        if (boxed == LocalDateTime.class) {
            return LocalDateTime.parse(trimmed);
        }
        if (boxed == Instant.class) {
            return Instant.parse(trimmed);
        }
        if (boxed == BigDecimal.class) {
            return new BigDecimal(trimmed);
        }
        if (boxed == BigInteger.class) {
            return new BigInteger(trimmed);
        }
        if (boxed.isEnum()) {
            return toEnum(boxed, trimmed);
        }
        if (conversionService.canConvert(String.class, target)) {
            return conversionService.convert(raw, target);
        }
        throw new IllegalStateException("no converter available for target type " + target.getName());
    }

    private static Object toEnum(Class<?> enumType, String raw) {
        for (Object constant : enumType.getEnumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(raw)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("expected one of " + enumNames(enumType) + " (case-insensitive)");
    }

    private static String enumNames(Class<?> enumType) {
        return Arrays.stream(enumType.getEnumConstants())
                .map(c -> ((Enum<?>) c).name())
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String message(FieldMetadata field, JsonNode node) {
        return "filter on '" + field.name() + "': expected " + field.javaType().getSimpleName()
                + " but got " + render(node);
    }

    private static String message(FieldMetadata field, JsonNode node, RuntimeException cause) {
        String reason = cause.getMessage();
        return message(field, node) + (reason == null || reason.isBlank() ? "" : " (" + reason + ")");
    }

    private static String render(JsonNode node) {
        return node == null ? "null" : node.toString();
    }
}
