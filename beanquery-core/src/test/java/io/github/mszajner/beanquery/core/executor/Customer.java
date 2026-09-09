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
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Association target for {@link Order} in the executor integration tests. */
@Entity(name = "ExecutorCustomer")
@Table(name = "exec_customers")
public class Customer {

    public enum Tier {
        STANDARD, GOLD, PLATINUM
    }

    @Id
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private Tier tier;

    protected Customer() {
    }

    public Customer(Long id, String name, Tier tier) {
        this.id = id;
        this.name = name;
        this.tier = tier;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Tier getTier() {
        return tier;
    }
}
