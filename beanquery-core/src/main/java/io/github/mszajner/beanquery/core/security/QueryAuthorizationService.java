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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.FieldKind;
import io.github.mszajner.beanquery.core.metadata.FieldMetadata;
import io.github.mszajner.beanquery.core.query.QueryRequest;
import io.github.mszajner.beanquery.core.query.ResolvedFilterNode;

/**
 * Runs the registered {@link QueryAuthorizer}s (in order) and aggregates their
 * verdicts for one {@code /metadata} or {@code /query} call.
 *
 * <ul>
 *   <li>any {@code DENY} (or a {@code null} verdict) &rarr; {@link QueryAccessDeniedException}</li>
 *   <li>mandatory filters from all supporting authorizers are validated against the
 *       entity's metadata and resolved; a bad one &rarr;
 *       {@link QueryAuthorizerConfigurationException} (logged at {@code ERROR})</li>
 *   <li>hidden field names that do not exist are logged at {@code WARN} and dropped</li>
 * </ul>
 *
 * With no authorizer registered, {@link #authorize} returns {@link AppliedAuthorization#ALLOW_ALL}.
 */
public class QueryAuthorizationService {

    private static final Log log = LogFactory.getLog(QueryAuthorizationService.class);

    private final List<QueryAuthorizer> authorizers;

    public QueryAuthorizationService(List<QueryAuthorizer> orderedAuthorizers) {
        this.authorizers = List.copyOf(orderedAuthorizers);
    }

    public boolean hasAuthorizers() {
        return !authorizers.isEmpty();
    }

    /**
     * @param request the incoming request, or {@code null} for a {@code GET /metadata} call
     * @throws QueryAccessDeniedException            if any authorizer denies
     * @throws QueryAuthorizerConfigurationException if a mandatory filter references an
     *                                               unknown field or a disallowed operator
     */
    public AppliedAuthorization authorize(EntityMetadata meta, QueryRequest request) {
        Objects.requireNonNull(meta, "meta");
        if (authorizers.isEmpty()) {
            return AppliedAuthorization.ALLOW_ALL;
        }

        QueryAuthorizationContext ctx = new QueryAuthorizationContext(meta, request);
        List<ResolvedFilterNode> mandatory = new ArrayList<>();
        Set<String> hidden = new LinkedHashSet<>();

        for (QueryAuthorizer authorizer : authorizers) {
            if (!authorizer.supports(meta)) {
                continue;
            }
            QueryAuthorization verdict = authorizer.authorize(ctx);
            if (verdict == null || verdict.isDenied()) {
                throw new QueryAccessDeniedException(meta.name());
            }
            for (MandatoryFilter filter : verdict.mandatoryFilters()) {
                mandatory.add(resolve(filter, meta, authorizer));
            }
            for (String field : verdict.hiddenFields()) {
                if (meta.field(field).isPresent()) {
                    hidden.add(field);
                } else {
                    log.warn("QueryAuthorizer " + authorizer.getClass().getName() + " hides field '" + field
                            + "' which is not registered for entity '" + meta.name() + "' - ignored");
                }
            }
        }
        return new AppliedAuthorization(mandatory, hidden);
    }

    private ResolvedFilterNode resolve(MandatoryFilter filter, EntityMetadata meta, QueryAuthorizer authorizer) {
        FieldMetadata field = meta.field(filter.field()).orElse(null);
        if (field == null) {
            throw configurationError(authorizer, meta, "unknown field '" + filter.field() + "'");
        }
        if (field.kind() == FieldKind.REFERENCE) {
            // the executor's ReferenceFilterTranslator validates the operator and resolves the value
            return new ResolvedFilterNode.Condition(field, filter.op(), filter.value());
        }
        if (filter.op() == null || !field.allows(filter.op())) {
            throw configurationError(authorizer, meta, "operator " + filter.op() + " is not allowed for field '"
                    + filter.field() + "' (allowed: " + field.allowedOperators() + ")");
        }
        return new ResolvedFilterNode.Condition(field, filter.op(), filter.value());
    }

    private QueryAuthorizerConfigurationException configurationError(
            QueryAuthorizer authorizer, EntityMetadata meta, String detail) {

        String message = "QueryAuthorizer " + authorizer.getClass().getName()
                + " produced an invalid mandatory filter for entity '" + meta.name() + "': " + detail;
        log.error(message);
        return new QueryAuthorizerConfigurationException(message);
    }
}
