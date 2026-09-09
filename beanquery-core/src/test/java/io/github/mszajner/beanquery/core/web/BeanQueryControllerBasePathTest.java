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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.QueryableEntityRegistry;
import io.github.mszajner.beanquery.core.query.DynamicQueryExecutor;
import io.github.mszajner.beanquery.core.query.QueryRequestValidator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The REST base path is driven by {@code beanquery.base-path}. */
@WebMvcTest
@Import({BeanQueryExceptionHandler.class, BeanQueryControllerBasePathTest.Config.class})
@TestPropertySource(properties = "beanquery.base-path=/data/query")
class BeanQueryControllerBasePathTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private QueryableEntityRegistry registry;

    @MockitoBean
    private DynamicQueryExecutor executor;

    @Test
    void servesUnderTheConfiguredBasePath() throws Exception {
        when(registry.getRequired("order")).thenReturn(new EntityMetadata("order", Object.class, List.of()));

        mvc.perform(get("/data/query/order/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity").value("order"));

        mvc.perform(get("/api/bq/order/metadata"))
                .andExpect(status().isNotFound());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        BeanQueryController beanQueryController(QueryableEntityRegistry registry, DynamicQueryExecutor executor) {
            return new BeanQueryController(registry, new QueryRequestValidator(200), executor,
                    new io.github.mszajner.beanquery.core.security.QueryAuthorizationService(java.util.List.of()), 20);
        }
    }
}
