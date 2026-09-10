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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.github.mszajner.beanquery.core.annotation.Queryable;
import io.github.mszajner.beanquery.core.annotation.QueryableField;
import io.github.mszajner.beanquery.core.annotation.QueryableReference;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryableEntityRegistryReferenceTest {

    private QueryableEntityRegistry registryOf(Class<?>... classes) {
        QueryableEntityRegistry registry =
                new QueryableEntityRegistry(mock(EntityManagerFactory.class), java.util.Set.of("customer"));
        registry.initialize(List.of(classes));
        return registry;
    }

    @Test
    void expandsReferenceIntoSubFieldsAndMetadata() {
        EntityMetadata order = registryOf(OrderModel.class).getRequired("order");

        assertThat(order.fieldsByName()).containsKeys("customerId", "customer.name", "customer.tier");

        FieldMetadata name = order.field("customer.name").orElseThrow();
        assertThat(name.kind()).isEqualTo(FieldKind.REFERENCE);
        assertThat(name.selectable()).isTrue();
        assertThat(name.filterable()).isTrue();
        assertThat(name.sortable()).isFalse();
        assertThat(name.allowedOperators())
                .contains(FilterOperator.ILIKE, FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL);

        assertThat(order.reference("customer")).get()
                .satisfies(ref -> {
                    assertThat(ref.idFieldPath()).isEqualTo("customerId");
                    assertThat(ref.fields()).containsExactly("name", "tier");
                });

        assertThat(order.field("customerId").orElseThrow().kind()).isEqualTo(FieldKind.COLUMN);
    }

    @Test
    void rejectsReferenceWithoutQueryableField() {
        assertThatThrownBy(() -> registryOf(RefWithoutQueryableField.class))
                .isInstanceOf(QueryableMetadataException.class)
                .hasMessageContaining("@QueryableField");
    }

    @Test
    void rejectsReferenceNameCollidingWithAField() {
        assertThatThrownBy(() -> registryOf(RefNameCollision.class))
                .isInstanceOf(QueryableMetadataException.class)
                .hasMessageContaining("customer");
    }

    @Queryable(name = "order")
    static class OrderModel {
        @QueryableField Long id;
        @QueryableField
        @QueryableReference(name = "customer", fields = {"name", "tier"})
        Long customerId;
    }

    @Queryable(name = "x")
    static class RefWithoutQueryableField {
        @QueryableField Long id;
        @QueryableReference(name = "customer", fields = {"name"})
        Long customerId;
    }

    @Queryable(name = "x")
    static class RefNameCollision {
        @QueryableField Long id;
        @QueryableField String customer;
        @QueryableField
        @QueryableReference(name = "customer", fields = {"name"})
        Long customerId;
    }
}
