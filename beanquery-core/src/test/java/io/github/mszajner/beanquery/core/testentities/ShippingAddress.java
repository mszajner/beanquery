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

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * A managed entity that is <em>not</em> {@code @Queryable}: it must be ignored as
 * a top-level entity, yet still be reachable as a nested association target.
 */
@Entity
public class ShippingAddress {

    @Id
    private Long id;

    private String code;

    private String city;

    protected ShippingAddress() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getCity() {
        return city;
    }
}
