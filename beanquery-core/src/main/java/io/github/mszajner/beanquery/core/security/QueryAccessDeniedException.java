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
 * Thrown when a {@link QueryAuthorizer} denies a call. The web layer answers
 * {@code 403} with {@code { "error": "access_denied" }} - no hint about which
 * authorizer or why. Not {@code 404}: the entity exists in the public metadata.
 */
public class QueryAccessDeniedException extends RuntimeException {

    private final String entityName;

    public QueryAccessDeniedException(String entityName) {
        super("Access denied to entity '" + entityName + "'");
        this.entityName = entityName;
    }

    public String getEntityName() {
        return entityName;
    }
}
