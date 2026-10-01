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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;

import io.github.mszajner.beanquery.core.reference.ReferenceResolvers;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

class DynamicQueryExecutorTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final FilterValueConverter valueConverter = FilterValueConverter.withDefaultConversionService();

    @Test
    void rejectsMaxReferenceFilterIdsBelowOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DynamicQueryExecutor(entityManager, valueConverter, ReferenceResolvers.EMPTY, 0))
                .withMessageContaining("maxReferenceFilterIds must be >= 1");
    }

    @Test
    void acceptsMaxReferenceFilterIdsOfOne() {
        assertThatCode(() -> new DynamicQueryExecutor(entityManager, valueConverter, ReferenceResolvers.EMPTY, 1))
                .doesNotThrowAnyException();
    }
}
