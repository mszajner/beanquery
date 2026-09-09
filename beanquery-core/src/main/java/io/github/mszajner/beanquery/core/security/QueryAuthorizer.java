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

import io.github.mszajner.beanquery.core.metadata.EntityMetadata;

/**
 * Host-implemented authorization hook. Register one or more as Spring beans;
 * ordering follows {@code @Order} / {@link org.springframework.core.Ordered}.
 *
 * <p>Called for every {@code GET /{entity}/metadata} and {@code POST /{entity}/query},
 * before the request is validated. Any {@link QueryAuthorization.Decision#DENY}
 * results in HTTP 403. Mandatory filters and hidden fields from all supporting
 * authorizers are aggregated.
 *
 * <p>The library passes no user identity - read it from your own security context.
 */
public interface QueryAuthorizer {

    /** Whether this authorizer has an opinion about {@code meta}. */
    boolean supports(EntityMetadata meta);

    /** The verdict for this call. Never returns {@code null}. */
    QueryAuthorization authorize(QueryAuthorizationContext ctx);
}
