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
package io.github.mszajner.beanquery.starter.refit;

import io.github.mszajner.beanquery.core.annotation.Queryable;
import io.github.mszajner.beanquery.core.annotation.QueryableField;
import io.github.mszajner.beanquery.core.annotation.QueryableReference;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Order entity owned by one module that references a {@code customers} row owned by
 * another. There is deliberately no JPA association - only the scalar {@code customerId}.
 */
@Entity(name = "RefOrder")
@Table(name = "ref_order")
@Queryable(name = "refOrder")
public class RefOrder {

    @Id
    @QueryableField
    private Long id;

    @QueryableField
    private String status;

    @QueryableField
    @QueryableReference(name = "customer", fields = {"name", "tier"},
            operators = {FilterOperator.EQ, FilterOperator.ILIKE})
    private Long customerId;

    protected RefOrder() {
    }

    public RefOrder(Long id, String status, Long customerId) {
        this.id = id;
        this.status = status;
        this.customerId = customerId;
    }
}
