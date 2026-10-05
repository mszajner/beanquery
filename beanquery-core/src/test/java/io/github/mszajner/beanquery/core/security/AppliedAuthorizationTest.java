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
package io.github.mszajner.beanquery.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.FieldKind;
import io.github.mszajner.beanquery.core.metadata.FieldMetadata;
import io.github.mszajner.beanquery.core.metadata.ReferenceMetadata;

class AppliedAuthorizationTest {

    private static FieldMetadata field(String name, Class<?> type) {
        return new FieldMetadata(name, name, type, true, true, true, null, FieldKind.COLUMN);
    }

    private static EntityMetadata order() {
        return new EntityMetadata(
                "order", Object.class,
                List.of(field("id", Long.class), field("customerId", Long.class)),
                List.of(
                        new ReferenceMetadata("customer", "customerId", List.of("name", "email")),
                        new ReferenceMetadata("shipper", "shipperId", List.of("code"))));
    }

    @Test
    void hidingASubFieldRemovesItButKeepsTheReference() {
        AppliedAuthorization auth = new AppliedAuthorization(List.of(), Set.of("customer.email"));

        EntityMetadata visible = auth.visibleMetadata(order());

        ReferenceMetadata customer = visible.reference("customer").orElseThrow();
        assertThat(customer.fields()).containsExactly("name");
        assertThat(customer.name()).isEqualTo("customer");
        assertThat(customer.idFieldPath()).isEqualTo("customerId");
    }

    @Test
    void aReferenceWithEverySubFieldHiddenIsDropped() {
        AppliedAuthorization auth = new AppliedAuthorization(
                List.of(), Set.of("shipper.code", "unrelated"));

        EntityMetadata visible = auth.visibleMetadata(order());

        assertThat(visible.reference("shipper")).isEmpty();
        assertThat(visible.reference("customer")).isPresent();
    }

    @Test
    void hidingAReferencesFkIdColumnDropsTheWholeReferenceAndItsSubFields() {
        FieldMetadata subName = new FieldMetadata("customer.name", "", String.class,
                true, true, false, null, FieldKind.REFERENCE);
        FieldMetadata subEmail = new FieldMetadata("customer.email", "", String.class,
                true, true, false, null, FieldKind.REFERENCE);
        EntityMetadata meta = new EntityMetadata(
                "order", Object.class,
                List.of(field("id", Long.class), field("customerId", Long.class), subName, subEmail),
                List.of(new ReferenceMetadata("customer", "customerId", List.of("name", "email"))));

        EntityMetadata visible = new AppliedAuthorization(List.of(), Set.of("customerId")).visibleMetadata(meta);

        assertThat(visible.reference("customer")).isEmpty();
        assertThat(visible.field("customer.name")).isEmpty();
        assertThat(visible.field("customer.email")).isEmpty();
        assertThat(visible.field("customerId")).isEmpty();
        assertThat(visible.field("id")).isPresent();
    }

    @Test
    void emptyHiddenFieldsPassesMetadataThroughUntouched() {
        EntityMetadata meta = order();
        AppliedAuthorization auth = new AppliedAuthorization(List.of(), Set.of());

        assertThat(auth.visibleMetadata(meta)).isSameAs(meta);
    }
}
