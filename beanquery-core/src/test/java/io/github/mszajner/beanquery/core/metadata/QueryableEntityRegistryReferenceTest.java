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
import jakarta.persistence.ManyToOne;
import java.util.Collection;
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
    void referenceSubFieldsStayNonSortableEvenWhenIdFieldIsSortable() {
        EntityMetadata e = registryOf(SortableIdReference.class).getRequired("x");

        assertThat(e.field("customerId").orElseThrow().sortable()).isTrue();
        assertThat(e.field("customer.name").orElseThrow().sortable()).isFalse();
    }

    @Test
    void declaredOperatorsPathAddsOnlyIsNullAndIsNotNull() {
        EntityMetadata e = registryOf(DeclaredOperatorsReference.class).getRequired("x");

        assertThat(e.field("customer.name").orElseThrow().allowedOperators())
                .containsExactlyInAnyOrder(FilterOperator.EQ, FilterOperator.ILIKE,
                        FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL);
    }

    @Test
    void defaultOperatorsPathIsReferenceDefaultPlusNullChecks() {
        EntityMetadata e = registryOf(OrderModel.class).getRequired("order");

        java.util.EnumSet<FilterOperator> expected =
                java.util.EnumSet.copyOf(DefaultOperators.referenceDefault());
        expected.add(FilterOperator.IS_NULL);
        expected.add(FilterOperator.IS_NOT_NULL);

        assertThat(e.field("customer.name").orElseThrow().allowedOperators())
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void rejectsReferenceWithoutQueryableField() {
        assertThatThrownBy(() -> registryOf(RefWithoutQueryableField.class))
                .isInstanceOf(QueryableMetadataException.class)
                .hasMessageContaining("@QueryableField");
    }

    @Test
    void rejectsReferenceWithNoFields() {
        assertThatThrownBy(() -> registryOf(RefWithNoFields.class))
                .isInstanceOf(QueryableMetadataException.class);
    }

    @Test
    void rejectsReferenceSubFieldContainingDot() {
        assertThatThrownBy(() -> registryOf(RefWithDottedSubField.class))
                .isInstanceOf(QueryableMetadataException.class);
    }

    @Test
    void rejectsReferenceNameCollidingWithAField() {
        assertThatThrownBy(() -> registryOf(RefNameCollision.class))
                .isInstanceOf(QueryableMetadataException.class)
                .hasMessageContaining("customer");
    }

    @Test
    void rejectsReferenceNameCollidingWithAFieldDeclaredAfterTheReference() {
        assertThatThrownBy(() -> registryOf(RefNameCollisionColumnAfter.class))
                .isInstanceOf(QueryableMetadataException.class)
                .hasMessageContaining("customer");
    }

    @Test
    void rejectsTwoReferencesSharingAName() {
        assertThatThrownBy(() -> registryOf(DuplicateReferenceName.class))
                .isInstanceOf(QueryableMetadataException.class)
                .hasMessageContaining("customer");
    }

    @Test
    void rejectsReferenceOnAnAssociationField() {
        assertThatThrownBy(() -> registryOf(RefOnAssociation.class))
                .isInstanceOf(QueryableMetadataException.class)
                .hasMessageContaining("customer")
                .hasMessageContaining("scalar foreign-key id column");
    }

    @Test
    void rejectsReferenceOnACollectionField() {
        assertThatThrownBy(() -> registryOf(RefOnCollection.class))
                .isInstanceOf(QueryableMetadataException.class)
                .hasMessageContaining("scalar foreign-key id column");
    }

    @Test
    void rejectsReferenceNameContainingADot() {
        assertThatThrownBy(() -> registryOf(RefNameWithDot.class))
                .isInstanceOf(QueryableMetadataException.class)
                .hasMessageContaining("a.b")
                .hasMessageContaining("must not contain");
    }

    @Test
    void failsFastWhenNoResolverBeanMatches() {
        QueryableEntityRegistry registry =
                new QueryableEntityRegistry(mock(EntityManagerFactory.class), java.util.Set.of());
        assertThatThrownBy(() -> registry.initialize(List.of(OrderModel.class)))
                .isInstanceOf(QueryableMetadataException.class)
                .hasMessageContaining("order")
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
    static class SortableIdReference {
        @QueryableField Long id;
        @QueryableField(sortable = true)
        @QueryableReference(name = "customer", fields = {"name"})
        Long customerId;
    }

    @Queryable(name = "x")
    static class DeclaredOperatorsReference {
        @QueryableField Long id;
        @QueryableField
        @QueryableReference(name = "customer", fields = {"name"},
                operators = {FilterOperator.EQ, FilterOperator.ILIKE})
        Long customerId;
    }

    @Queryable(name = "x")
    static class RefWithoutQueryableField {
        @QueryableField Long id;
        @QueryableReference(name = "customer", fields = {"name"})
        Long customerId;
    }

    @Queryable(name = "x")
    static class RefWithNoFields {
        @QueryableField Long id;
        @QueryableField
        @QueryableReference(name = "customer", fields = {})
        Long customerId;
    }

    @Queryable(name = "x")
    static class RefWithDottedSubField {
        @QueryableField Long id;
        @QueryableField
        @QueryableReference(name = "customer", fields = {"address.city"})
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

    @Queryable(name = "x")
    static class RefNameCollisionColumnAfter {
        @QueryableField Long id;
        @QueryableField
        @QueryableReference(name = "customer", fields = {"name"})
        Long customerId;
        @QueryableField String customer;
    }

    @Queryable(name = "x")
    static class RefOnAssociation {
        @QueryableField Long id;
        @QueryableField(nested = {"name"})
        @ManyToOne
        @QueryableReference(name = "customer", fields = {"name"})
        Target customer;

        static class Target {
            String name;
        }
    }

    @Queryable(name = "x")
    static class RefOnCollection {
        @QueryableField Long id;
        @QueryableField
        @QueryableReference(name = "customer", fields = {"name"})
        Collection<Long> customerIds;
    }

    @Queryable(name = "x")
    static class RefNameWithDot {
        @QueryableField Long id;
        @QueryableField
        @QueryableReference(name = "a.b", fields = {"name"})
        Long customerId;
    }

    @Queryable(name = "x")
    static class DuplicateReferenceName {
        @QueryableField Long id;
        @QueryableField
        @QueryableReference(name = "customer", fields = {"name"})
        Long customerId;
        @QueryableField
        @QueryableReference(name = "customer", fields = {"tier"})
        Long customerRef;
    }
}
