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
package io.github.mszajner.beanquery.starter.it;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import io.github.mszajner.beanquery.core.annotation.Queryable;
import io.github.mszajner.beanquery.core.annotation.QueryableField;

@Entity(name = "SecuredOrder")
@Table(name = "secured_order")
@Queryable(name = "securedOrder")
public class SecuredOrder {

    @Id
    @QueryableField
    private Long id;

    @QueryableField
    private String status;

    @QueryableField
    private BigDecimal totalAmount;

    /** Discriminator the host filters by; users usually never see it (host hides it). */
    @QueryableField
    private String tenantId;

    @ManyToOne
    @QueryableField(nested = {"id", "name"})
    private SecuredCustomer customer;

    protected SecuredOrder() {
    }

    public SecuredOrder(Long id, String tenantId, String status, BigDecimal totalAmount, SecuredCustomer customer) {
        this.id = id;
        this.tenantId = tenantId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.customer = customer;
    }
}
