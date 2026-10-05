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

import static io.github.mszajner.beanquery.core.metadata.FilterOperator.BETWEEN;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.EQ;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.IN;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.IS_NOT_NULL;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.IS_NULL;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.NOT_IN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.github.mszajner.beanquery.core.metadata.FieldKind;
import io.github.mszajner.beanquery.core.metadata.FieldMetadata;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class FilterValueConverterTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final FilterValueConverter converter = FilterValueConverter.withDefaultConversionService();

    enum Color {
        RED, GREEN, BLUE
    }

    // ================================================================
    // happy paths, per type
    // ================================================================

    @Nested
    class HappyPath {

        @Test
        void string() {
            assertThat(converter.convert(field("name", String.class), EQ, node("\"acme\"")))
                    .isEqualTo("acme");
        }

        @Test
        void integerViaConversionService() {
            assertThat(converter.convert(field("qty", Integer.class), EQ, node("42")))
                    .isEqualTo(42);
        }

        @Test
        void primitiveLong() {
            assertThat(converter.convert(field("qty", long.class), EQ, node("42")))
                    .isEqualTo(42L);
        }

        @Test
        void bigDecimalFromJsonString() {
            assertThat(converter.convert(field("total", BigDecimal.class), EQ, node("\"12.50\"")))
                    .isEqualTo(new BigDecimal("12.50"));
        }

        @Test
        void bigDecimalFromJsonNumber() {
            assertThat(converter.convert(field("total", BigDecimal.class), EQ, node("12.5")))
                    .isEqualTo(new BigDecimal("12.5"));
        }

        @Test
        void bigInteger() {
            assertThat(converter.convert(field("huge", BigInteger.class), EQ, node("\"100000000000000000000\"")))
                    .isEqualTo(new BigInteger("100000000000000000000"));
        }

        @Test
        void booleanViaConversionService() {
            assertThat(converter.convert(field("paid", Boolean.class), EQ, node("true")))
                    .isEqualTo(true);
            assertThat(converter.convert(field("paid", boolean.class), EQ, node("\"false\"")))
                    .isEqualTo(false);
        }

        @Test
        void uuid() {
            UUID id = UUID.randomUUID();
            assertThat(converter.convert(field("ref", UUID.class), EQ, node("\"" + id + "\"")))
                    .isEqualTo(id);
        }

        @Test
        void localDate() {
            assertThat(converter.convert(field("placedOn", LocalDate.class), EQ, node("\"2026-09-03\"")))
                    .isEqualTo(LocalDate.of(2026, 9, 3));
        }

        @Test
        void localDateTime() {
            assertThat(converter.convert(field("at", LocalDateTime.class), EQ, node("\"2026-09-03T10:15:30\"")))
                    .isEqualTo(LocalDateTime.of(2026, 9, 3, 10, 15, 30));
        }

        @Test
        void instant() {
            assertThat(converter.convert(field("at", Instant.class), EQ, node("\"2026-09-03T10:15:30Z\"")))
                    .isEqualTo(Instant.parse("2026-09-03T10:15:30Z"));
        }

        @Test
        void enumByNameCaseInsensitive() {
            assertThat(converter.convert(field("color", Color.class), EQ, node("\"red\""))).isEqualTo(Color.RED);
            assertThat(converter.convert(field("color", Color.class), EQ, node("\"GREEN\""))).isEqualTo(Color.GREEN);
            assertThat(converter.convert(field("color", Color.class), EQ, node("\"Blue\""))).isEqualTo(Color.BLUE);
        }
    }

    // ================================================================
    // operator-driven shape
    // ================================================================

    @Nested
    class OperatorShape {

        @Test
        void inReturnsListOfConvertedValues() {
            Object result = converter.convert(field("qty", Integer.class), IN, node("[1,2,3]"));
            assertThat(result).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(Object.class))
                    .containsExactly(1, 2, 3);
        }

        @Test
        void notInReturnsListOfConvertedValues() {
            Object result = converter.convert(field("color", Color.class), NOT_IN, node("[\"red\",\"blue\"]"));
            assertThat(result).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(Object.class))
                    .containsExactly(Color.RED, Color.BLUE);
        }

        @Test
        void betweenReturnsConvertedRange() {
            Object result = converter.convert(field("total", BigDecimal.class), BETWEEN, node("[\"1.00\",\"9.99\"]"));
            assertThat(result).isEqualTo(new Range(new BigDecimal("1.00"), new BigDecimal("9.99")));
        }

        @Test
        void isNullReturnsNothing() {
            assertThat(converter.convert(field("name", String.class), IS_NULL, null)).isNull();
            assertThat(converter.convert(field("name", String.class), IS_NULL, node("\"ignored\""))).isNull();
        }

        @Test
        void isNotNullReturnsNothing() {
            assertThat(converter.convert(field("name", String.class), IS_NOT_NULL, node("null"))).isNull();
        }
    }

    // ================================================================
    // conversion errors, per type
    // ================================================================

    @Nested
    class ConversionErrors {

        @Test
        void integer() {
            assertThat(errorsOf("qty", Integer.class, EQ, node("\"abc\"")))
                    .singleElement().asString()
                    .contains("filter on 'qty'", "expected Integer", "\"abc\"");
        }

        @Test
        void longValue() {
            assertThat(errorsOf("qty", Long.class, EQ, node("\"12x\"")))
                    .singleElement().asString().contains("expected Long", "\"12x\"");
        }

        @Test
        void bigDecimal() {
            assertThat(errorsOf("total", BigDecimal.class, EQ, node("\"not-a-number\"")))
                    .singleElement().asString().contains("expected BigDecimal", "\"not-a-number\"");
        }

        @Test
        void bigInteger() {
            assertThat(errorsOf("huge", BigInteger.class, EQ, node("\"3.14\"")))
                    .singleElement().asString().contains("expected BigInteger", "\"3.14\"");
        }

        @Test
        void booleanValue() {
            assertThat(errorsOf("paid", Boolean.class, EQ, node("\"maybe\"")))
                    .singleElement().asString().contains("expected Boolean", "\"maybe\"");
        }

        @Test
        void uuid() {
            assertThat(errorsOf("ref", UUID.class, EQ, node("\"not-a-uuid\"")))
                    .singleElement().asString().contains("expected UUID", "\"not-a-uuid\"");
        }

        @Test
        void localDate() {
            assertThat(errorsOf("placedOn", LocalDate.class, EQ, node("\"03/09/2026\"")))
                    .singleElement().asString().contains("filter on 'placedOn'", "expected LocalDate", "\"03/09/2026\"");
        }

        @Test
        void localDateTimeMissingTime() {
            assertThat(errorsOf("at", LocalDateTime.class, EQ, node("\"2026-09-03\"")))
                    .singleElement().asString().contains("expected LocalDateTime");
        }

        @Test
        void instantMissingZone() {
            assertThat(errorsOf("at", Instant.class, EQ, node("\"2026-09-03T10:15:30\"")))
                    .singleElement().asString().contains("expected Instant");
        }

        @Test
        void enumUnknownName() {
            assertThat(errorsOf("color", Color.class, EQ, node("\"purple\"")))
                    .singleElement().asString()
                    .contains("expected Color", "\"purple\"", "[RED, GREEN, BLUE]", "case-insensitive");
        }

        @Test
        void nullValueForScalarOperator() {
            assertThat(errorsOf("qty", Integer.class, EQ, null))
                    .singleElement().asString().contains("expected Integer", "but got null");
        }

        @Test
        void objectNodeForScalarOperator() {
            assertThat(errorsOf("qty", Integer.class, EQ, node("{\"a\":1}")))
                    .singleElement().asString().contains("expected Integer");
        }
    }

    // ================================================================
    // multi-value error collection
    // ================================================================

    @Test
    void inCollectsOneErrorPerBadElement() {
        assertThatExceptionOfType(FilterValueConversionException.class)
                .isThrownBy(() -> converter.convert(field("qty", Integer.class), IN, node("[1,\"x\",2,\"y\"]")))
                .satisfies(ex -> assertThat(ex.getErrors()).hasSize(2)
                        .anySatisfy(e -> assertThat(e).contains("\"x\""))
                        .anySatisfy(e -> assertThat(e).contains("\"y\"")));
    }

    @Test
    void betweenCollectsErrorsForBothBounds() {
        assertThatExceptionOfType(FilterValueConversionException.class)
                .isThrownBy(() -> converter.convert(field("placedOn", LocalDate.class), BETWEEN, node("[\"a\",\"b\"]")))
                .satisfies(ex -> assertThat(ex.getErrors()).hasSize(2));
    }

    @Test
    void betweenReportsOnlyTheBadBound() {
        assertThatExceptionOfType(FilterValueConversionException.class)
                .isThrownBy(() -> converter.convert(field("qty", Integer.class), BETWEEN, node("[\"1\",\"oops\"]")))
                .satisfies(ex -> assertThat(ex.getErrors()).singleElement().asString().contains("\"oops\""));
    }

    @Test
    void inRejectsNonArray() {
        assertThatExceptionOfType(FilterValueConversionException.class)
                .isThrownBy(() -> converter.convert(field("qty", Integer.class), IN, node("5")));
    }

    // ================================================================
    // misc
    // ================================================================

    @Test
    void constructorRejectsNullConversionService() {
        assertThatNullPointerException().isThrownBy(() -> new FilterValueConverter(null));
    }

    @Test
    void convertRejectsNullFieldOrOperator() {
        assertThatNullPointerException()
                .isThrownBy(() -> converter.convert(null, EQ, node("1")));
        assertThatNullPointerException()
                .isThrownBy(() -> converter.convert(field("qty", Integer.class), null, node("1")));
    }

    // ================================================================
    // helpers
    // ================================================================

    private java.util.List<String> errorsOf(String name, Class<?> type,
            io.github.mszajner.beanquery.core.metadata.FilterOperator op, JsonNode value) {
        try {
            converter.convert(field(name, type), op, value);
            return java.util.List.of();
        } catch (FilterValueConversionException e) {
            return e.getErrors();
        }
    }

    private static FieldMetadata field(String name, Class<?> type) {
        return new FieldMetadata(name, name, type, true, true, true, Set.of(), FieldKind.COLUMN);
    }

    private static JsonNode node(String raw) {
        return MAPPER.readTree(raw);
    }
}
