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
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure-reflection unit tests for the registry's build and validation logic.
 * The models here are deliberately <em>not</em> JPA entities.
 */
class QueryableEntityRegistryTest {

    private QueryableEntityRegistry registryOf(Class<?>... classes) {
        QueryableEntityRegistry registry = new QueryableEntityRegistry(mock(EntityManagerFactory.class));
        registry.initialize(List.of(classes));
        return registry;
    }

    @Test
    void ignoresClassesWithoutQueryable() {
        assertThat(registryOf(PlainModel.class).getAll()).isEmpty();
    }

    @Test
    void usesExplicitNameAndDerivesLowerCamelCaseOtherwise() {
        QueryableEntityRegistry registry = registryOf(CustomerModel.class, PurchaseOrderModel.class);

        assertThat(registry.getAll()).extracting(EntityMetadata::name)
                .containsExactlyInAnyOrder("customer", "purchaseOrderModel");
    }

    @Test
    void getRequiredThrowsUnknownEntityException() {
        QueryableEntityRegistry registry = registryOf(CustomerModel.class);

        assertThatThrownBy(() -> registry.getRequired("missing"))
                .isInstanceOf(UnknownEntityException.class)
                .satisfies(ex -> assertThat(((UnknownEntityException) ex).getEntityName()).isEqualTo("missing"));
    }

    @Test
    void mapsScalarFields() {
        EntityMetadata customer = registryOf(CustomerModel.class).getRequired("customer");

        assertThat(customer.entityClass()).isEqualTo(CustomerModel.class);
        assertThat(customer.fieldsByName()).containsOnlyKeys("id", "name", "email");

        FieldMetadata name = customer.field("name").orElseThrow();
        assertThat(name.path()).isEqualTo("name");
        assertThat(name.javaType()).isEqualTo(String.class);
        assertThat(name.selectable()).isTrue();
        assertThat(name.filterable()).isTrue();
        assertThat(name.sortable()).isTrue();

        FieldMetadata email = customer.field("email").orElseThrow();
        assertThat(email.filterable()).isFalse();
        assertThat(email.sortable()).isFalse();
    }

    @Test
    void honoursExplicitOperatorsOtherwiseDefaultsByType() {
        EntityMetadata order = registryOf(PurchaseOrderModel.class).getRequired("purchaseOrderModel");

        assertThat(order.field("reference").orElseThrow().allowedOperators())
                .containsExactly(FilterOperator.EQ);
        assertThat(order.field("amount").orElseThrow().allowedOperators())
                .containsExactlyInAnyOrder(FilterOperator.EQ, FilterOperator.NE, FilterOperator.GT,
                        FilterOperator.GTE, FilterOperator.LT, FilterOperator.LTE, FilterOperator.BETWEEN,
                        FilterOperator.IN, FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL);
    }

    @Test
    void expandsAssociationNestedIntoDepth1DottedFields() {
        EntityMetadata order = registryOf(PurchaseOrderModel.class).getRequired("purchaseOrderModel");

        assertThat(order.fieldsByName()).containsKeys("customer.id", "customer.name");

        FieldMetadata customerName = order.field("customer.name").orElseThrow();
        assertThat(customerName.name()).isEqualTo("customer.name");
        assertThat(customerName.path()).isEqualTo("customer.name");
        assertThat(customerName.javaType()).isEqualTo(String.class);
        // flags are inherited from the association's annotation
        assertThat(customerName.selectable()).isTrue();
        assertThat(customerName.filterable()).isFalse();
    }

    @Test
    void scalarFieldsAreKindColumn() {
        EntityMetadata customer = registryOf(CustomerModel.class).getRequired("customer");
        assertThat(customer.field("name").orElseThrow().kind()).isEqualTo(FieldKind.COLUMN);
    }

    @Test
    void nestedAssociationFieldsAreKindJoined() {
        EntityMetadata order = registryOf(PurchaseOrderModel.class).getRequired("purchaseOrderModel");
        assertThat(order.field("customer.name").orElseThrow().kind()).isEqualTo(FieldKind.JOINED);
    }

    @Test
    void rejectsDuplicateEntityNames() {
        assertThatThrownBy(() -> registryOf(DuplicateA.class, DuplicateB.class))
                .isInstanceOf(QueryableMetadataException.class)
                .hasMessageContaining("Duplicate queryable entity name 'dup'");
    }

    @Test
    void rejectsNestedOnScalarField() {
        assertThatThrownBy(() -> registryOf(NestedOnScalar.class))
                .isInstanceOf(QueryableMetadataException.class)
                .hasMessageContaining("is not a @ManyToOne/@OneToOne association");
    }

    @Test
    void rejectsAssociationWithoutNested() {
        assertThatThrownBy(() -> registryOf(AssociationWithoutNested.class))
                .isInstanceOf(QueryableMetadataException.class)
                .hasMessageContaining("must declare nested sub-fields");
    }

    @Test
    void rejectsUnknownNestedSubProperty() {
        assertThatThrownBy(() -> registryOf(UnknownNested.class))
                .isInstanceOf(QueryableMetadataException.class)
                .hasMessageContaining("Nested field 'ghost' does not exist");
    }

    @Test
    void rejectsNestedDeeperThanOneLevel() {
        assertThatThrownBy(() -> registryOf(DeepNested.class))
                .isInstanceOf(QueryableMetadataException.class)
                .hasMessageContaining("exceeds the maximum nesting depth of 1");
    }

    // -- models -----------------------------------------------------------

    static class PlainModel {
        @QueryableField
        Long id;
    }

    @Queryable(name = "customer")
    static class CustomerModel {
        @QueryableField
        Long id;
        @QueryableField
        String name;
        @QueryableField(filterable = false, sortable = false)
        String email;
        String internalNote;
    }

    @Queryable
    static class PurchaseOrderModel {
        @QueryableField
        Long id;
        @QueryableField(operators = {FilterOperator.EQ})
        String reference;
        @QueryableField
        BigDecimal amount;
        @ManyToOne
        @QueryableField(nested = {"id", "name"}, filterable = false)
        CustomerModel customer;
    }

    @Queryable(name = "dup")
    static class DuplicateA {
        @QueryableField
        Long id;
    }

    @Queryable(name = "dup")
    static class DuplicateB {
        @QueryableField
        Long id;
    }

    @Queryable(name = "x")
    static class NestedOnScalar {
        @QueryableField(nested = {"foo"})
        String name;
    }

    @Queryable(name = "x")
    static class AssociationWithoutNested {
        @OneToOne
        @QueryableField
        CustomerModel customer;
    }

    @Queryable(name = "x")
    static class UnknownNested {
        @ManyToOne
        @QueryableField(nested = {"ghost"})
        CustomerModel customer;
    }

    @Queryable(name = "x")
    static class DeepNested {
        @ManyToOne
        @QueryableField(nested = {"address.city"})
        CustomerModel customer;
    }
}
