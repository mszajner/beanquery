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
package io.github.mszajner.beanquery.starter;

import io.github.mszajner.beanquery.core.metadata.QueryableEntityRegistry;
import io.github.mszajner.beanquery.core.query.DynamicQueryExecutor;
import io.github.mszajner.beanquery.core.query.FilterValueConverter;
import io.github.mszajner.beanquery.core.query.QueryRequestValidator;
import io.github.mszajner.beanquery.core.reference.ReferenceResolver;
import io.github.mszajner.beanquery.core.reference.ReferenceResolvers;
import io.github.mszajner.beanquery.core.security.QueryAuthorizationService;
import io.github.mszajner.beanquery.core.security.QueryAuthorizer;
import io.github.mszajner.beanquery.core.web.BeanQueryController;
import io.github.mszajner.beanquery.core.web.BeanQueryExceptionHandler;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures the beanquery whitelist registry, query engine and REST layer.
 *
 * <p>Active when {@link EntityManager} is on the classpath, a JPA
 * {@link EntityManagerFactory} bean exists, and {@code beanquery.enabled} is not
 * {@code false}. Every bean is {@code @ConditionalOnMissingBean}, so any part can
 * be replaced by the application.
 *
 * <p>Any {@code QueryAuthorizer} beans the host declares are picked up (ordered by
 * {@code @Order}); with none, the API is unrestricted.
 */
@AutoConfiguration(afterName = "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration")
@ConditionalOnClass(EntityManager.class)
@ConditionalOnBean(EntityManagerFactory.class)
@ConditionalOnProperty(prefix = "beanquery", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(BeanQueryProperties.class)
public class BeanQueryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ReferenceResolvers beanQueryReferenceResolvers(ObjectProvider<ReferenceResolver> resolvers) {
        return new ReferenceResolvers(resolvers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryableEntityRegistry beanQueryEntityRegistry(EntityManagerFactory entityManagerFactory,
            ReferenceResolvers referenceResolvers) {
        return new QueryableEntityRegistry(entityManagerFactory, referenceResolvers.names());
    }

    @Bean
    @ConditionalOnMissingBean
    public FilterValueConverter beanQueryFilterValueConverter() {
        return FilterValueConverter.withDefaultConversionService();
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryRequestValidator beanQueryRequestValidator(BeanQueryProperties properties) {
        return new QueryRequestValidator(properties.getMaxPageSize(),
                properties.getMaxFilterDepth(), properties.getMaxFilterConditions());
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamicQueryExecutor beanQueryExecutor(EntityManager entityManager, FilterValueConverter valueConverter,
            ReferenceResolvers referenceResolvers, BeanQueryProperties properties) {
        return new DynamicQueryExecutor(entityManager, valueConverter,
                referenceResolvers, properties.getMaxReferenceFilterIds());
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryAuthorizationService queryAuthorizationService(ObjectProvider<QueryAuthorizer> authorizers) {
        return new QueryAuthorizationService(authorizers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public BeanQueryController beanQueryController(QueryableEntityRegistry registry, QueryRequestValidator validator,
            DynamicQueryExecutor executor, QueryAuthorizationService authorization, BeanQueryProperties properties) {
        return new BeanQueryController(registry, validator, executor, authorization, properties.getDefaultPageSize());
    }

    @Bean
    @ConditionalOnMissingBean
    public BeanQueryExceptionHandler beanQueryExceptionHandler() {
        return new BeanQueryExceptionHandler();
    }
}
