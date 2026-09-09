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
package io.github.mszajner.beanquery.core.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The body of {@code POST /api/bq/{entity}/query}.
 *
 * <p>{@code null} collections are normalised to empty ones. {@code filters}
 * accepts either the legacy flat array (read as {@code AND}) or a filter tree
 * (see {@link FiltersDeserializer}); {@code null} means "no filter".
 *
 * @param select  field names to return, in output order
 * @param filters filter tree, or {@code null}
 * @param sort    sort clauses, applied in order
 * @param page    paging request
 */
public record QueryRequest(
        List<String> select,
        FilterNode filters,
        List<Sort> sort,
        Page page) {

    public QueryRequest {
        select = nullSafeCopy(select);
        sort = nullSafeCopy(sort);
    }

    private static <T> List<T> nullSafeCopy(List<T> source) {
        return source == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(source));
    }
}
