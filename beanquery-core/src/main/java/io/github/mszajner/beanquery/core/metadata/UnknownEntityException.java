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

/**
 * Thrown when a request references an entity name that is not registered in the
 * {@link QueryableEntityRegistry}. The web layer translates this into a 400
 * response with a readable message.
 */
public class UnknownEntityException extends RuntimeException {

    private final String entityName;

    public UnknownEntityException(String entityName) {
        super("No queryable entity is registered under name '" + entityName + "'");
        this.entityName = entityName;
    }

    /** The unresolved entity name from the request. */
    public String getEntityName() {
        return entityName;
    }
}
