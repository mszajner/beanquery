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
package io.github.mszajner.beanquery.core.testentities;

import io.github.mszajner.beanquery.core.annotation.Queryable;
import io.github.mszajner.beanquery.core.annotation.QueryableField;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code @Queryable} with no explicit name - registered under the derived
 * {@code "purchaseOrder"}.
 */
@Entity
@Queryable
public class PurchaseOrder {

    public enum Status {
        NEW, PAID, SHIPPED
    }

    @Id
    @QueryableField
    private Long id;

    @QueryableField(operators = {FilterOperator.EQ, FilterOperator.IN})
    private String reference;

    @QueryableField
    private BigDecimal total;

    @QueryableField
    private LocalDate placedOn;

    @QueryableField
    @Enumerated(EnumType.STRING)
    private Status status;

    @QueryableField
    private boolean paid;

    @ManyToOne
    @QueryableField(nested = {"id", "name"})
    private Customer customer;

    @OneToOne
    @QueryableField(nested = {"code"})
    private ShippingAddress shippingAddress;

    /** Not annotated: must stay invisible to the API. */
    private String notes;

    protected PurchaseOrder() {
    }
}
