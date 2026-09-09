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
package io.github.mszajner.beanquery.demo;

import io.github.mszajner.beanquery.core.annotation.Queryable;
import io.github.mszajner.beanquery.core.annotation.QueryableField;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Queryable(name = "product")
public class Product {

    public enum Status {
        DRAFT, ACTIVE, DISCONTINUED
    }

    @Id
    @QueryableField
    private Long id;

    @QueryableField
    private String name;

    @QueryableField
    private String sku;

    @QueryableField
    private BigDecimal price;

    @QueryableField
    private int stock;

    @QueryableField
    private LocalDate releasedOn;

    @QueryableField
    @Enumerated(EnumType.STRING)
    private Status status;

    @ManyToOne
    @QueryableField(nested = {"id", "name"})
    private Category category;

    protected Product() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSku() {
        return sku;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public LocalDate getReleasedOn() {
        return releasedOn;
    }

    public Status getStatus() {
        return status;
    }

    public Category getCategory() {
        return category;
    }
}
