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
import jakarta.persistence.Id;

@Entity
@Queryable(name = "category")
public class Category {

    @Id
    @QueryableField
    private Long id;

    @QueryableField
    private String name;

    @QueryableField
    private boolean active;

    protected Category() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
