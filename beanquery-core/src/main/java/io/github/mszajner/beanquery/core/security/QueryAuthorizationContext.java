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
import io.github.mszajner.beanquery.core.query.QueryRequest;

/**
 * What a {@link QueryAuthorizer} is handed for a single call.
 *
 * <p>The library passes <em>nothing</em> about the current user - the host pulls
 * that from wherever it keeps it ({@code SecurityContextHolder}, a {@code ThreadLocal},
 * a request-scoped bean, …).
 *
 * @param meta    the entity being queried (public metadata, before field hiding)
 * @param request the incoming request; {@code null} for {@code GET /metadata} calls
 */
public record QueryAuthorizationContext(EntityMetadata meta, QueryRequest request) {
}
