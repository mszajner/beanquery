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

import static io.github.mszajner.beanquery.core.metadata.FilterOperator.EQ;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.IN;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.IS_NOT_NULL;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.IS_NULL;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.NE;
import static io.github.mszajner.beanquery.core.metadata.FilterOperator.NOT_IN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mszajner.beanquery.core.testentities.Customer;
import io.github.mszajner.beanquery.core.testentities.PurchaseOrder;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Integration test: a real Hibernate/H2 {@link EntityManagerFactory} feeds the
 * registry, exercising the {@code SmartInitializingSingleton} startup path.
 */
@DataJpaTest
@Import(QueryableEntityRegistryIT.RegistryTestConfig.class)
class QueryableEntityRegistryIT {

    @Autowired
    private QueryableEntityRegistry registry;

    @Test
    void registersOnlyQueryableEntities() {
        assertThat(registry.getAll())
                .extracting(EntityMetadata::name)
                .containsExactlyInAnyOrder("customer", "purchaseOrder");
    }

    @Test
    void derivesDefaultEntityNameInLowerCamelCase() {
        assertThat(registry.getRequired("purchaseOrder").entityClass()).isEqualTo(PurchaseOrder.class);
    }

    @Test
    void getRequiredRejectsUnknownAndNonQueryableEntities() {
        assertThatThrownBy(() -> registry.getRequired("shippingAddress"))
                .isInstanceOf(UnknownEntityException.class);
        assertThatThrownBy(() -> registry.getRequired("nope"))
                .isInstanceOf(UnknownEntityException.class);
    }

    @Test
    void mapsScalarFieldsWithFlags() {
        EntityMetadata customer = registry.getRequired("customer");

        assertThat(customer.entityClass()).isEqualTo(Customer.class);
        assertThat(customer.fieldsByName()).containsOnlyKeys("id", "name", "email");

        FieldMetadata email = customer.field("email").orElseThrow();
        assertThat(email.selectable()).isTrue();
        assertThat(email.filterable()).isFalse();
        assertThat(email.sortable()).isFalse();
    }

    @Test
    void excludesFieldsWithoutQueryableField() {
        assertThat(registry.getRequired("purchaseOrder").field("notes")).isEmpty();
    }

    @Test
    void expandsManyToOneNestedIntoDepth1DottedPaths() {
        EntityMetadata order = registry.getRequired("purchaseOrder");

        FieldMetadata customerId = order.field("customer.id").orElseThrow();
        assertThat(customerId.path()).isEqualTo("customer.id");
        assertThat(customerId.javaType()).isEqualTo(Long.class);

        FieldMetadata customerName = order.field("customer.name").orElseThrow();
        assertThat(customerName.path()).isEqualTo("customer.name");
        assertThat(customerName.javaType()).isEqualTo(String.class);
    }

    @Test
    void expandsOneToOneNested() {
        FieldMetadata code = registry.getRequired("purchaseOrder").field("shippingAddress.code").orElseThrow();
        assertThat(code.path()).isEqualTo("shippingAddress.code");
        assertThat(code.javaType()).isEqualTo(String.class);
    }

    @Test
    void appliesExplicitOperators() {
        assertThat(registry.getRequired("purchaseOrder").field("reference").orElseThrow().allowedOperators())
                .containsExactlyInAnyOrder(EQ, IN);
    }

    @Test
    void appliesDefaultOperatorsForEnumType() {
        assertThat(registry.getRequired("purchaseOrder").field("status").orElseThrow().allowedOperators())
                .containsExactlyInAnyOrder(EQ, NE, IN, NOT_IN, IS_NULL, IS_NOT_NULL);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RegistryTestConfig {

        @Bean
        QueryableEntityRegistry queryableEntityRegistry(EntityManagerFactory entityManagerFactory) {
            return new QueryableEntityRegistry(entityManagerFactory);
        }
    }
}
