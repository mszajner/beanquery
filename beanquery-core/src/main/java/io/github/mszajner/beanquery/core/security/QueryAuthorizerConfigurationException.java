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

/**
 * Thrown when a {@link QueryAuthorizer} returns a {@link MandatoryFilter} whose
 * field is not registered, or whose operator is not allowed for that field. This
 * is a host bug, not a client error: the web layer answers {@code 500} and the
 * detail (entity, field, authorizer class) is logged at {@code ERROR}.
 *
 * <p>A missing mandatory predicate (e.g. {@code tenantId}) is a data leak, so it
 * is never swallowed.
 */
public class QueryAuthorizerConfigurationException extends RuntimeException {

    public QueryAuthorizerConfigurationException(String message) {
        super(message);
    }
}
