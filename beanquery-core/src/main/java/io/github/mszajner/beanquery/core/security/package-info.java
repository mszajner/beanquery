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
/**
 * The authorization hook.
 *
 * <p>The library knows nothing about Spring Security or the host's permission
 * model - it only offers an extension point. A host registers one or more
 * {@link io.github.mszajner.beanquery.core.security.QueryAuthorizer} beans; for every
 * {@code /metadata} and {@code /query} call they can deny access, append
 * mandatory predicates ({@code AND}-ed over the whole request) and hide fields.
 *
 * <p>With no {@code QueryAuthorizer} bean present, every call is allowed - the
 * behaviour is exactly as before this package existed.
 */
package io.github.mszajner.beanquery.core.security;
