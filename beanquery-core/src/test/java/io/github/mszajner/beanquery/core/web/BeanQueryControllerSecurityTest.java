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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import io.github.mszajner.beanquery.core.metadata.DefaultOperators;
import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.FieldMetadata;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import io.github.mszajner.beanquery.core.metadata.QueryableEntityRegistry;
import io.github.mszajner.beanquery.core.query.DynamicQueryExecutor;
import io.github.mszajner.beanquery.core.query.PageInfo;
import io.github.mszajner.beanquery.core.query.QueryRequestValidator;
import io.github.mszajner.beanquery.core.query.QueryResult;
import io.github.mszajner.beanquery.core.query.ResolvedFilterNode;
import io.github.mszajner.beanquery.core.security.MandatoryFilter;
import io.github.mszajner.beanquery.core.security.QueryAuthorization;
import io.github.mszajner.beanquery.core.security.QueryAuthorizationService;
import io.github.mszajner.beanquery.core.security.QueryAuthorizer;

@WebMvcTest
@Import({BeanQueryExceptionHandler.class, BeanQueryControllerSecurityTest.Config.class})
class BeanQueryControllerSecurityTest {

    private static final EntityMetadata ORDER = new EntityMetadata("order", Object.class, List.of(
            field("id", Long.class),
            field("status", String.class),
            field("totalAmount", java.math.BigDecimal.class),
            field("tenantId", String.class)));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private QueryableEntityRegistry registry;

    @MockitoBean
    private DynamicQueryExecutor executor;

    @MockitoBean
    private QueryAuthorizer authorizer;

    @BeforeEach
    void stubs() {
        when(registry.getRequired("order")).thenReturn(ORDER);
        when(authorizer.supports(any())).thenReturn(true);
        when(executor.execute(any(), any(), any()))
                .thenReturn(new QueryResult(List.of(), new PageInfo(0, 20, 0, 0)));
    }

    private static final String VALID_QUERY = """
            {"select":["id","status"],"page":{"number":0,"size":20}}""";

    @Test
    void denyReturns403OnQuery() throws Exception {
        when(authorizer.authorize(any())).thenReturn(QueryAuthorization.deny());

        mvc.perform(post("/api/bq/order/query").contentType(MediaType.APPLICATION_JSON).content(VALID_QUERY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("access_denied"));

        verifyNoInteractions(executor);
    }

    @Test
    void denyReturns403OnMetadata() throws Exception {
        when(authorizer.authorize(any())).thenReturn(QueryAuthorization.deny());

        mvc.perform(get("/api/bq/order/metadata"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("access_denied"));
    }

    @Test
    void hiddenFieldUsedInSelectFailsAsUnknownField() throws Exception {
        when(authorizer.authorize(any()))
                .thenReturn(QueryAuthorization.builder().hideField("totalAmount").allow());

        mvc.perform(post("/api/bq/order/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id","totalAmount"],"page":{"number":0,"size":20}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("unknown field 'totalAmount'"))));

        verifyNoInteractions(executor);
    }

    @Test
    void hiddenFieldIsStrippedFromMetadata() throws Exception {
        when(authorizer.authorize(any()))
                .thenReturn(QueryAuthorization.builder().hideField("totalAmount").allow());

        mvc.perform(get("/api/bq/order/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[*].name", hasItem("status")))
                .andExpect(jsonPath("$.fields[*].name", not(hasItem("totalAmount"))));
    }

    @Test
    void mandatoryFiltersAreForwardedToTheExecutor() throws Exception {
        when(authorizer.authorize(any())).thenReturn(QueryAuthorization.allowWith(
                new MandatoryFilter("tenantId", FilterOperator.EQ, "t1")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ResolvedFilterNode>> captor = ArgumentCaptor.forClass(List.class);
        when(executor.execute(any(), any(), captor.capture()))
                .thenReturn(new QueryResult(List.of(), new PageInfo(0, 20, 0, 0)));

        mvc.perform(post("/api/bq/order/query").contentType(MediaType.APPLICATION_JSON).content(VALID_QUERY))
                .andExpect(status().isOk());

        List<ResolvedFilterNode> mandatory = captor.getValue();
        assertThat(mandatory).hasSize(1);
        ResolvedFilterNode.Condition condition = (ResolvedFilterNode.Condition) mandatory.get(0);
        assertThat(condition.field().name()).isEqualTo("tenantId");
        assertThat(condition.op()).isEqualTo(FilterOperator.EQ);
        assertThat(condition.value()).isEqualTo("t1");
    }

    @Test
    void misconfiguredMandatoryFilterReturns500() throws Exception {
        when(authorizer.authorize(any())).thenReturn(QueryAuthorization.allowWith(
                new MandatoryFilter("nope", FilterOperator.EQ, "x")));

        mvc.perform(post("/api/bq/order/query").contentType(MediaType.APPLICATION_JSON).content(VALID_QUERY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"));
    }

    private static FieldMetadata field(String name, Class<?> type) {
        return new FieldMetadata(name, name, type, true, true, true, DefaultOperators.forType(type));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        QueryAuthorizationService queryAuthorizationService(QueryAuthorizer authorizer) {
            return new QueryAuthorizationService(List.of(authorizer));
        }

        @Bean
        BeanQueryController beanQueryController(QueryableEntityRegistry registry, DynamicQueryExecutor executor,
                QueryAuthorizationService authorization) {
            return new BeanQueryController(registry, new QueryRequestValidator(200), executor, authorization, 20);
        }
    }
}
