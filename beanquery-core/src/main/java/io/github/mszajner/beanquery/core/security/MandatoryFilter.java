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
package io.github.mszajner.beanquery.core.security;

import io.github.mszajner.beanquery.core.metadata.FilterOperator;

/**
 * A predicate a {@link QueryAuthorizer} forces onto every request for an entity
 * (e.g. {@code tenantId EQ "t1"}).
 *
 * @param field a field that MUST be registered in the entity's metadata; the
 *              operator MUST be in its {@code allowedOperators} - otherwise it is
 *              a host configuration error (HTTP 500, logged), never silently dropped
 * @param op    the operator
 * @param value the value, <strong>already converted</strong> to the field's Java
 *              type (the host knows the types); the library does not parse it
 */
public record MandatoryFilter(String field, FilterOperator op, Object value) {
}
