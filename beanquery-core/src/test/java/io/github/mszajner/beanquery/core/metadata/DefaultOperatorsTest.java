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

import static io.github.mszajner.beanquery.core.metadata.FilterOperator.BETWEEN;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.EQ;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.GT;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.GTE;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.ILIKE;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.IN;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.IS_NOT_NULL;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.IS_NULL;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.LIKE;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.LT;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.LTE;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.NE;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.NOT_IN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DefaultOperatorsTest {

    @Test
    void stringGetsTextOperators() {
        assertThat(DefaultOperators.forType(String.class))
                .containsExactlyInAnyOrder(EQ, NE, LIKE, ILIKE, IN, IS_NULL, IS_NOT_NULL);
    }

    @ParameterizedTest
    @ValueSource(classes = {
            int.class, long.class, double.class, float.class, short.class, byte.class,
            Integer.class, Long.class, Double.class, Float.class, Short.class, Byte.class,
            BigDecimal.class, BigInteger.class,
            LocalDate.class, LocalDateTime.class, Instant.class
    })
    void numbersAndTemporalsGetComparableOperators(Class<?> type) {
        assertThat(DefaultOperators.forType(type))
                .containsExactlyInAnyOrder(EQ, NE, GT, GTE, LT, LTE, BETWEEN, IN, IS_NULL, IS_NOT_NULL);
    }

    @ParameterizedTest
    @ValueSource(classes = {boolean.class, Boolean.class})
    void booleanGetsEqualityOperators(Class<?> type) {
        assertThat(DefaultOperators.forType(type))
                .containsExactlyInAnyOrder(EQ, NE, IS_NULL, IS_NOT_NULL);
    }

    @Test
    void enumGetsMembershipOperators() {
        assertThat(DefaultOperators.forType(Season.class))
                .containsExactlyInAnyOrder(EQ, NE, IN, NOT_IN, IS_NULL, IS_NOT_NULL);
    }

    @Test
    void enumWithConstantBodyIsStillTreatedAsEnum() {
        assertThat(DefaultOperators.forType(WithBody.A.getClass()))
                .containsExactlyInAnyOrder(EQ, NE, IN, NOT_IN, IS_NULL, IS_NOT_NULL);
    }

    @ParameterizedTest
    @ValueSource(classes = {Object.class, UUID.class, LocalTime.class, int[].class})
    void unknownTypesGetNoOperators(Class<?> type) {
        assertThat(DefaultOperators.forType(type)).isEmpty();
    }

    @Test
    void nullTypeIsRejected() {
        assertThatNullPointerException().isThrownBy(() -> DefaultOperators.forType(null));
    }

    @Test
    void resultIteratesInFilterOperatorDeclarationOrder() {
        assertThat(DefaultOperators.forType(BigDecimal.class))
                .containsExactly(EQ, NE, GT, GTE, LT, LTE, IN, BETWEEN, IS_NULL, IS_NOT_NULL);
    }

    @Test
    void resultIsUnmodifiable() {
        assertThatThrownBy(() -> DefaultOperators.forType(String.class).add(BETWEEN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    enum Season {
        SPRING, SUMMER, AUTUMN, WINTER
    }

    enum WithBody {
        A {
            @Override
            String label() {
                return "a";
            }
        };

        abstract String label();
    }
}
