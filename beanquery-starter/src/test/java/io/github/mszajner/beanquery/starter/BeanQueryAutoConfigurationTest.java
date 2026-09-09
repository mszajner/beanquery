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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.mszajner.beanquery.core.metadata.QueryableEntityRegistry;
import io.github.mszajner.beanquery.core.query.DynamicQueryExecutor;
import io.github.mszajner.beanquery.core.query.FilterValueConverter;
import io.github.mszajner.beanquery.core.query.QueryRequestValidator;
import io.github.mszajner.beanquery.core.security.QueryAuthorizationService;
import io.github.mszajner.beanquery.core.web.BeanQueryController;
import io.github.mszajner.beanquery.core.web.BeanQueryExceptionHandler;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Metamodel;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BeanQueryAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BeanQueryAutoConfiguration.class))
            .withBean(EntityManagerFactory.class, BeanQueryAutoConfigurationTest::stubEntityManagerFactory)
            .withBean(EntityManager.class, () -> mock(EntityManager.class));

    @Test
    void registersTheStackByDefault() {
        runner.run(context -> assertThat(context)
                .hasSingleBean(BeanQueryProperties.class)
                .hasSingleBean(QueryableEntityRegistry.class)
                .hasSingleBean(FilterValueConverter.class)
                .hasSingleBean(QueryRequestValidator.class)
                .hasSingleBean(DynamicQueryExecutor.class)
                .hasSingleBean(QueryAuthorizationService.class)
                .hasSingleBean(BeanQueryController.class)
                .hasSingleBean(BeanQueryExceptionHandler.class));
    }

    @Test
    void authorizationServiceHasNoAuthorizersByDefault() {
        runner.run(context ->
                assertThat(context.getBean(QueryAuthorizationService.class).hasAuthorizers()).isFalse());
    }

    @Test
    void appliesDefaultProperties() {
        runner.run(context -> {
            BeanQueryProperties properties = context.getBean(BeanQueryProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getBasePath()).isEqualTo("/api/bq");
            assertThat(properties.getMaxPageSize()).isEqualTo(200);
            assertThat(properties.getDefaultPageSize()).isEqualTo(20);
            assertThat(properties.getMaxFilterDepth()).isEqualTo(5);
            assertThat(properties.getMaxFilterConditions()).isEqualTo(50);
        });
    }

    @Test
    void filterLimitsAreOverridableAndReachTheValidator() {
        runner.withPropertyValues("beanquery.max-filter-depth=2", "beanquery.max-filter-conditions=7").run(context -> {
            BeanQueryProperties properties = context.getBean(BeanQueryProperties.class);
            assertThat(properties.getMaxFilterDepth()).isEqualTo(2);
            assertThat(properties.getMaxFilterConditions()).isEqualTo(7);

            QueryRequestValidator validator = context.getBean(QueryRequestValidator.class);
            assertThat(validator.getMaxFilterDepth()).isEqualTo(2);
            assertThat(validator.getMaxFilterConditions()).isEqualTo(7);
        });
    }

    @Test
    void backsOffEntirelyWhenDisabled() {
        runner.withPropertyValues("beanquery.enabled=false").run(context -> assertThat(context)
                .doesNotHaveBean(BeanQueryAutoConfiguration.class)
                .doesNotHaveBean(BeanQueryController.class)
                .doesNotHaveBean(BeanQueryProperties.class));
    }

    @Test
    void staysEnabledWhenExplicitlyTrue() {
        runner.withPropertyValues("beanquery.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(BeanQueryController.class));
    }

    @Test
    void basePathIsOverridable() {
        runner.withPropertyValues("beanquery.base-path=/custom/query").run(context ->
                assertThat(context.getBean(BeanQueryProperties.class).getBasePath()).isEqualTo("/custom/query"));
    }

    @Test
    void pageSizesAreOverridable() {
        runner.withPropertyValues("beanquery.max-page-size=50", "beanquery.default-page-size=5").run(context -> {
            BeanQueryProperties properties = context.getBean(BeanQueryProperties.class);
            assertThat(properties.getMaxPageSize()).isEqualTo(50);
            assertThat(properties.getDefaultPageSize()).isEqualTo(5);
        });
    }

    @Test
    void backsOffWhenEntityManagerFactoryIsMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(BeanQueryAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(BeanQueryController.class));
    }

    @Test
    void backsOffWhenJpaIsNotOnTheClasspath() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(BeanQueryAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader(EntityManager.class))
                .run(context -> assertThat(context).doesNotHaveBean(BeanQueryController.class));
    }

    @Test
    void userSuppliedBeansWin() {
        QueryRequestValidator custom = new QueryRequestValidator(9);
        runner.withBean("customValidator", QueryRequestValidator.class, () -> custom).run(context -> {
            assertThat(context).hasSingleBean(QueryRequestValidator.class);
            assertThat(context.getBean(QueryRequestValidator.class)).isSameAs(custom);
        });
    }

    private static EntityManagerFactory stubEntityManagerFactory() {
        Metamodel metamodel = mock(Metamodel.class);
        when(metamodel.getEntities()).thenReturn(Set.of());
        EntityManagerFactory entityManagerFactory = mock(EntityManagerFactory.class);
        when(entityManagerFactory.getMetamodel()).thenReturn(metamodel);
        return entityManagerFactory;
    }
}
