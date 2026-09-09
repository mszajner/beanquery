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
package io.github.mszajner.beanquery.core.web;

import io.github.mszajner.beanquery.core.query.PageInfo;
import io.github.mszajner.beanquery.core.query.QueryResult;
import java.util.List;
import java.util.Map;

/**
 * Response body of {@code POST /{entity}/query}.
 *
 * @param rows one ordered map per row; keys are the request's {@code select} names
 * @param page paging metadata ({@code number}, {@code size}, {@code totalElements}, {@code totalPages})
 */
public record QueryResponse(List<Map<String, Object>> rows, PageInfo page) {

    public static QueryResponse from(QueryResult result) {
        return new QueryResponse(result.rows(), result.page());
    }
}
