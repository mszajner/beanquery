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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class FilterNodeDeserializationTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    private QueryRequest read(String json) {
        return mapper.readValue(json, QueryRequest.class);
    }

    @Test
    void legacyFlatArrayBecomesAndGroup() {
        QueryRequest request = read("""
                {
                  "select": ["id"],
                  "filters": [
                    { "field": "status", "op": "EQ", "value": "NEW" },
                    { "field": "total", "op": "GT", "value": 100 }
                  ]
                }""");

        assertThat(request.filters()).isInstanceOf(GroupNode.class);
        GroupNode group = (GroupNode) request.filters();
        assertThat(group.logic()).isEqualTo(LogicalOperator.AND);
        assertThat(group.children()).hasSize(2).allMatch(child -> child instanceof ConditionNode);

        ConditionNode first = (ConditionNode) group.children().get(0);
        assertThat(first.field()).isEqualTo("status");
        assertThat(first.op()).isEqualTo(FilterOperator.EQ);
        assertThat(first.value().asString()).isEqualTo("NEW");
    }

    @Test
    void emptyArrayBecomesNoFilter() {
        assertThat(read("{ \"select\": [\"id\"], \"filters\": [] }").filters()).isNull();
    }

    @Test
    void missingFiltersIsNull() {
        assertThat(read("{ \"select\": [\"id\"] }").filters()).isNull();
    }

    @Test
    void singleConditionObjectIsDeducedAsCondition() {
        QueryRequest request = read("""
                { "select": ["id"], "filters": { "field": "status", "op": "EQ", "value": "NEW" } }""");

        assertThat(request.filters()).isInstanceOf(ConditionNode.class);
        assertThat(((ConditionNode) request.filters()).field()).isEqualTo("status");
    }

    @Test
    void conditionWithoutValueForNullOperator() {
        QueryRequest request = read("""
                { "select": ["id"], "filters": { "field": "status", "op": "IS_NULL" } }""");

        ConditionNode condition = (ConditionNode) request.filters();
        assertThat(condition.op()).isEqualTo(FilterOperator.IS_NULL);
        assertThat(condition.value()).isNull();
    }

    @Test
    void mixedTreeIsDeducedRecursively() {
        QueryRequest request = read("""
                {
                  "select": ["id", "status", "customer.name"],
                  "filters": {
                    "logic": "and",
                    "children": [
                      { "field": "status", "op": "EQ", "value": "NEW" },
                      {
                        "logic": "or",
                        "children": [
                          { "field": "totalAmount", "op": "GT", "value": 1000 },
                          { "field": "customer.name", "op": "ILIKE", "value": "acme" }
                        ]
                      }
                    ]
                  }
                }""");

        GroupNode root = (GroupNode) request.filters();
        assertThat(root.logic()).isEqualTo(LogicalOperator.AND);
        assertThat(root.children()).hasSize(2);
        assertThat(root.children().get(0)).isInstanceOf(ConditionNode.class);

        GroupNode nested = (GroupNode) root.children().get(1);
        assertThat(nested.logic()).isEqualTo(LogicalOperator.OR);
        assertThat(nested.children()).hasSize(2).allMatch(child -> child instanceof ConditionNode);
        assertThat(((ConditionNode) nested.children().get(1)).field()).isEqualTo("customer.name");
    }

    @Test
    void objectWithNeitherFieldNorLogicIsRejectedWithAReadableMessage() {
        assertThatThrownBy(() -> read("""
                { "select": ["id"], "filters": { "foo": 1, "bar": 2 } }"""))
                .isInstanceOf(JacksonException.class)
                .hasMessageContaining("filters: a filter object must be a condition")
                .hasMessageContaining("or a group");
    }

    @Test
    void scalarFiltersValueIsRejected() {
        assertThatThrownBy(() -> read("{ \"select\": [\"id\"], \"filters\": 42 }"))
                .isInstanceOf(JacksonException.class)
                .hasMessageContaining("filters: expected a JSON array or a filter object");
    }
}
