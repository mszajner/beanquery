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
package io.github.mszajner.beanquery.core.executor;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity(name = "ExecutorOrder")
@Table(name = "exec_orders")
public class Order {

    public enum Status {
        NEW, PAID, SHIPPED, CANCELLED
    }

    @Id
    private Long id;

    private String reference;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private Status status;

    /** Nullable - exercises IS_NULL / IS_NOT_NULL. */
    private LocalDate placedOn;

    /** Nullable - exercises LEFT JOIN semantics. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;

    protected Order() {
    }

    public Order(Long id, String reference, BigDecimal amount, Status status, LocalDate placedOn, Customer customer) {
        this.id = id;
        this.reference = reference;
        this.amount = amount;
        this.status = status;
        this.placedOn = placedOn;
        this.customer = customer;
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDate getPlacedOn() {
        return placedOn;
    }

    public Customer getCustomer() {
        return customer;
    }
}
