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
package io.github.mszajner.beanquery.core.metadata;

import java.util.List;
import java.util.Objects;

/**
 * Links a {@code @QueryableReference} to the local foreign-key column and the
 * sub-fields a {@code ReferenceResolver} serves.
 *
 * @param name        the reference name / field-name prefix ({@code "customer"})
 * @param idFieldPath the registered {@link FieldMetadata#path()} of the FK id column
 *                    ({@code "customerId"})
 * @param fields      the exposed sub-field short names ({@code ["name", "email"]})
 */
public record ReferenceMetadata(String name, String idFieldPath, List<String> fields) {

    public ReferenceMetadata {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(idFieldPath, "idFieldPath");
        fields = List.copyOf(fields);
    }
}
