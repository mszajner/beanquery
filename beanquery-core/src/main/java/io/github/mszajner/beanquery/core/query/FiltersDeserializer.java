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

import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * Deserializer for {@link FilterNode}, accepting both shapes:
 *
 * <ul>
 *   <li>the legacy flat array {@code [ {condition}, {condition} ]} &rarr;
 *       {@code GroupNode(AND, [...])} (an empty array &rarr; {@code null})</li>
 *   <li>a single tree node - a condition object ({@code "field"}) or a group
 *       object ({@code "logic"} / {@code "children"}), recursively</li>
 * </ul>
 *
 * Anything else fails deserialization with a readable message. Nodes are built
 * directly from the JSON tree so that the deserializer does not recurse into
 * itself via {@link FilterNode}'s subtypes.
 */
public class FiltersDeserializer extends ValueDeserializer<FilterNode> {

    @Override
    public FilterNode getNullValue(DeserializationContext ctxt) {
        return null;
    }

    @Override
    public FilterNode deserialize(JsonParser parser, DeserializationContext ctxt) {
        return toFilterNode(ctxt.readTree(parser), ctxt);
    }

    private FilterNode toFilterNode(JsonNode node, DeserializationContext ctxt) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isArray()) {
            if (node.isEmpty()) {
                return null;
            }
            List<FilterNode> children = new ArrayList<>(node.size());
            for (JsonNode child : node) {
                children.add(toFilterNode(child, ctxt));
            }
            return new GroupNode(LogicalOperator.AND, children);
        }
        if (node.isObject()) {
            if (node.has("logic") || node.has("children")) {
                return toGroup(node, ctxt);
            }
            if (node.has("field") || node.has("op")) {
                return toCondition(node, ctxt);
            }
            return ctxt.reportInputMismatch(FilterNode.class,
                    "filters: a filter object must be a condition (with \"field\") "
                            + "or a group (with \"logic\" and \"children\"); got %s",
                    node.toString());
        }
        return ctxt.reportInputMismatch(FilterNode.class,
                "filters: expected a JSON array or a filter object, got a %s", node.getNodeType());
    }

    private FilterNode toGroup(JsonNode node, DeserializationContext ctxt) {
        LogicalOperator logic = readValue(node.get("logic"), LogicalOperator.class, ctxt);
        JsonNode childrenNode = node.get("children");
        List<FilterNode> children = new ArrayList<>();
        if (childrenNode != null && childrenNode.isArray()) {
            for (JsonNode child : childrenNode) {
                children.add(toFilterNode(child, ctxt));
            }
        } else if (childrenNode != null && !childrenNode.isNull()) {
            return ctxt.reportInputMismatch(FilterNode.class,
                    "filters: a group's \"children\" must be a JSON array");
        }
        return new GroupNode(logic, children);
    }

    private FilterNode toCondition(JsonNode node, DeserializationContext ctxt) {
        JsonNode fieldNode = node.get("field");
        String field = (fieldNode == null || fieldNode.isNull()) ? null : fieldNode.asString();
        FilterOperator op = readValue(node.get("op"), FilterOperator.class, ctxt);
        JsonNode value = node.get("value");
        if (value != null && value.isNull()) {
            value = null;
        }
        return new ConditionNode(field, op, value);
    }

    private static <T> T readValue(JsonNode node, Class<T> type, DeserializationContext ctxt) {
        if (node == null || node.isNull()) {
            return null;
        }
        return ctxt.readTreeAsValue(node, type);
    }
}
