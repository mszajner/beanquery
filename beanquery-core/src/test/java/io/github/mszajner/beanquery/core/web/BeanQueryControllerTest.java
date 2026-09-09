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
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.mszajner.beanquery.core.metadata.DefaultOperators;
import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.FieldMetadata;
import io.github.mszajner.beanquery.core.metadata.QueryableEntityRegistry;
import io.github.mszajner.beanquery.core.metadata.UnknownEntityException;
import io.github.mszajner.beanquery.core.query.DynamicQueryExecutor;
import io.github.mszajner.beanquery.core.query.Page;
import io.github.mszajner.beanquery.core.query.PageInfo;
import io.github.mszajner.beanquery.core.query.QueryRequest;
import io.github.mszajner.beanquery.core.query.QueryRequestValidator;
import io.github.mszajner.beanquery.core.query.QueryResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import({BeanQueryExceptionHandler.class, BeanQueryControllerTest.ControllerConfig.class})
class BeanQueryControllerTest {

    enum Status {
        NEW, PAID
    }

    private static final EntityMetadata ORDER_META = new EntityMetadata("order", Object.class, List.of(
            field("id", Long.class, true, true, true),
            field("reference", String.class, true, true, true),
            field("status", Status.class, true, true, true),
            field("createdAt", java.time.Instant.class, true, true, true),
            field("secret", String.class, false, false, false),
            new FieldMetadata("customer.name", "customer.name", String.class, true, false, true,
                    DefaultOperators.forType(String.class))));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private QueryableEntityRegistry registry;

    @MockitoBean
    private DynamicQueryExecutor executor;

    @BeforeEach
    void stubRegistry() {
        when(registry.getRequired("order")).thenReturn(ORDER_META);
        when(registry.getRequired("nope")).thenThrow(new UnknownEntityException("nope"));
    }

    @Test
    void metadataDescribesFields() throws Exception {
        mvc.perform(get("/api/bq/order/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity").value("order"))
                .andExpect(jsonPath("$.fields[0].name").value("id"))
                .andExpect(jsonPath("$.fields[0].type").value("number"))
                .andExpect(jsonPath("$.fields[0].selectable").value(true))
                .andExpect(jsonPath("$.fields[0].operators").isArray())
                .andExpect(jsonPath("$.fields[0].values").doesNotExist())
                .andExpect(jsonPath("$.fields[1].type").value("string"))
                .andExpect(jsonPath("$.fields[2].type").value("enum"))
                .andExpect(jsonPath("$.fields[2].values").value(hasItems("NEW", "PAID")))
                .andExpect(jsonPath("$.fields[3].type").value("datetime"))
                .andExpect(jsonPath("$.fields[4].name").value("secret"))
                .andExpect(jsonPath("$.fields[4].selectable").value(false))
                .andExpect(jsonPath("$.fields[4].filterable").value(false))
                .andExpect(jsonPath("$.fields[5].name").value("customer.name"))
                .andExpect(jsonPath("$.fields[5].filterable").value(false))
                .andExpect(jsonPath("$.capabilities.filterLogic").value(hasItems("AND", "OR")))
                .andExpect(jsonPath("$.capabilities.maxFilterDepth").value(5))
                .andExpect(jsonPath("$.capabilities.maxFilterConditions").value(50));
    }

    @Test
    void validQueryReturnsRowsAndPage() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("reference", "REF-001");
        when(executor.execute(any(), any(), any()))
                .thenReturn(new QueryResult(List.of(row), new PageInfo(0, 20, 1, 1)));

        mvc.perform(post("/api/bq/order/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"select":["id","reference"],"page":{"number":0,"size":20}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].id").value(1))
                .andExpect(jsonPath("$.rows[0].reference").value("REF-001"))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1));
    }

    @Test
    void invalidQueryReturns400WithErrorList() throws Exception {
        mvc.perform(post("/api/bq/order/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "select": ["ghost"],
                                  "filters": [{"field": "secret", "op": "EQ", "value": "x"}],
                                  "sort": [{"field": "customer.name", "direction": "ASC"}],
                                  "page": {"number": 0, "size": 999}
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors", hasItem(containsString("unknown field 'ghost'"))))
                .andExpect(jsonPath("$.errors", hasItem(containsString("'secret' is not filterable"))))
                .andExpect(jsonPath("$.errors", hasItem(containsString("page.size"))));

        verifyNoInteractions(executor);
    }

    @Test
    void unknownEntityReturns404OnMetadata() throws Exception {
        mvc.perform(get("/api/bq/nope/metadata"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("nope")));
    }

    @Test
    void missingPageFallsBackToDefaultPageSize() throws Exception {
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        when(executor.execute(any(), captor.capture(), any()))
                .thenReturn(new QueryResult(List.of(), new PageInfo(0, 20, 0, 0)));

        mvc.perform(post("/api/bq/order/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"select\":[\"id\"]}"))
                .andExpect(status().isOk());

        assertThat(captor.getValue().page()).isEqualTo(new Page(0, 20));
    }

    @Test
    void unknownEntityReturns404OnQuery() throws Exception {
        mvc.perform(post("/api/bq/nope/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"select\":[\"id\"],\"page\":{\"number\":0,\"size\":10}}"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(executor);
    }

    private static FieldMetadata field(String name, Class<?> type,
            boolean selectable, boolean filterable, boolean sortable) {
        Set<io.github.mszajner.beanquery.core.metadata.FilterOperator> ops = DefaultOperators.forType(type);
        return new FieldMetadata(name, name, type, selectable, filterable, sortable,
                ops.isEmpty() ? Set.of() : ops);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ControllerConfig {

        @Bean
        BeanQueryController beanQueryController(QueryableEntityRegistry registry, DynamicQueryExecutor executor) {
            return new BeanQueryController(registry, new QueryRequestValidator(200), executor,
                    new io.github.mszajner.beanquery.core.security.QueryAuthorizationService(java.util.List.of()), 20);
        }
    }
}
