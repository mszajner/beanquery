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
package io.github.mszajner.beanquery.demo.order;

import io.github.mszajner.beanquery.core.annotation.Queryable;
import io.github.mszajner.beanquery.core.annotation.QueryableField;
import io.github.mszajner.beanquery.core.annotation.QueryableReference;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Owned by the {@code order} module. Holds only {@code customerId} — the customer's
 * name/tier come from the {@code customer} module's {@code ReferenceResolver}.
 */
@Entity
@Table(name = "customer_order")
@Queryable(name = "order")
public class CustomerOrder {

    @Id
    @QueryableField
    private Long id;

    @QueryableField
    private String status;

    @QueryableField
    private BigDecimal total;

    @QueryableField
    @QueryableReference(name = "customer", fields = {"name", "tier"},
            operators = {FilterOperator.EQ, FilterOperator.ILIKE})
    private Long customerId;

    protected CustomerOrder() {
    }

    public Long getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Long getCustomerId() {
        return customerId;
    }
}
