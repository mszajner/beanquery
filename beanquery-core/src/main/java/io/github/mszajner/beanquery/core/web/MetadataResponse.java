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

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Response body of {@code GET /{entity}/metadata}.
 *
 * @param entity       the entity's query name
 * @param fields       one descriptor per registered field, in registration order
 * @param capabilities what the query endpoint accepts for this deployment
 */
public record MetadataResponse(String entity, List<FieldDescriptor> fields, Capabilities capabilities) {

    /**
     * @param name       logical field name used in requests
     * @param type       simplified type: {@code string}, {@code number}, {@code date},
     *                   {@code datetime}, {@code boolean} or {@code enum}
     * @param values     allowed values when {@code type} is {@code enum}; otherwise {@code null}
     * @param selectable may appear in {@code select}
     * @param filterable may appear in {@code filters}
     * @param sortable   may appear in {@code sort}
     * @param operators  allowed filter operators, as names
     * @param kind       how the field is backed: {@code COLUMN}, {@code JOINED} or
     *                   {@code REFERENCE} (a {@code REFERENCE} field is served by a
     *                   {@code ReferenceResolver} and cannot be sorted)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldDescriptor(
            String name,
            String type,
            List<String> values,
            boolean selectable,
            boolean filterable,
            boolean sortable,
            List<String> operators,
            String kind) {
    }

    /**
     * @param filterLogic         combinators allowed in filter groups
     * @param maxFilterDepth       maximum nesting depth of filter groups
     * @param maxFilterConditions  maximum total number of filter leaf conditions
     */
    public record Capabilities(List<String> filterLogic, int maxFilterDepth, int maxFilterConditions) {
    }
}
