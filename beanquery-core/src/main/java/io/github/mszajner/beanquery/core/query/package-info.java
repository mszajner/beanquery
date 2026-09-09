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
 * The query engine: translates a validated query request (select / filters /
 * sort / page) into a JPA {@code CriteriaQuery<Tuple>} with multiselect, depth-1
 * LEFT JOINs, and returns rows as ordered {@code Map<String, Object>}.
 */
package io.github.mszajner.beanquery.core.query;
