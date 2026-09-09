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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.QueryableEntityRegistry;
import io.github.mszajner.beanquery.core.query.DynamicQueryExecutor;
import io.github.mszajner.beanquery.core.query.Page;
import io.github.mszajner.beanquery.core.query.QueryRequest;
import io.github.mszajner.beanquery.core.query.QueryRequestValidator;
import io.github.mszajner.beanquery.core.security.AppliedAuthorization;
import io.github.mszajner.beanquery.core.security.QueryAuthorizationService;

/**
 * The beanquery REST API. Base path is configurable via
 * {@code beanquery.base-path} (default {@code /api/bq}).
 *
 * <p>Every call passes through {@link QueryAuthorizationService} first: a host
 * {@code QueryAuthorizer} may deny (403), append mandatory predicates and hide
 * fields. Hidden fields are stripped from the metadata handed to validation, the
 * engine and the {@code /metadata} response.
 */
@RestController
@RequestMapping("${beanquery.base-path:/api/bq}")
public class BeanQueryController {

    private final QueryableEntityRegistry registry;
    private final QueryRequestValidator validator;
    private final DynamicQueryExecutor executor;
    private final QueryAuthorizationService authorization;
    private final int defaultPageSize;
    private final MetadataMapper metadataMapper = new MetadataMapper();

    public BeanQueryController(QueryableEntityRegistry registry, QueryRequestValidator validator,
            DynamicQueryExecutor executor, QueryAuthorizationService authorization, int defaultPageSize) {
        this.registry = registry;
        this.validator = validator;
        this.executor = executor;
        this.authorization = authorization;
        this.defaultPageSize = defaultPageSize;
    }

    @GetMapping("/{entity}/metadata")
    public MetadataResponse metadata(@PathVariable String entity) {
        EntityMetadata meta = registry.getRequired(entity);
        AppliedAuthorization applied = authorization.authorize(meta, null);
        return metadataMapper.toResponse(applied.visibleMetadata(meta),
                validator.getMaxFilterDepth(), validator.getMaxFilterConditions());
    }

    @PostMapping("/{entity}/query")
    public QueryResponse query(@PathVariable String entity, @RequestBody QueryRequest request) {
        EntityMetadata meta = registry.getRequired(entity);
        QueryRequest effectiveRequest = withDefaultPage(request);

        AppliedAuthorization applied = authorization.authorize(meta, effectiveRequest);
        EntityMetadata visible = applied.visibleMetadata(meta);

        validator.validate(effectiveRequest, visible);
        return QueryResponse.from(executor.execute(visible, effectiveRequest, applied.mandatoryPredicates()));
    }

    private QueryRequest withDefaultPage(QueryRequest request) {
        if (request.page() != null) {
            return request;
        }
        return new QueryRequest(request.select(), request.filters(), request.sort(),
                new Page(0, defaultPageSize));
    }
}
