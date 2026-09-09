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
package io.github.mszajner.beanquery.starter.it;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import io.github.mszajner.beanquery.core.security.MandatoryFilter;
import io.github.mszajner.beanquery.core.security.QueryAuthorization;
import io.github.mszajner.beanquery.core.security.QueryAuthorizationContext;
import io.github.mszajner.beanquery.core.security.QueryAuthorizer;

/**
 * End-to-end multi-tenancy: a host {@link QueryAuthorizer} appends
 * {@code tenantId EQ <current>} to every {@code securedOrder} call.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(QueryAuthorizerIT.Config.class)
@TestPropertySource(properties =
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "io.github.mszajner.beanquery.starter.it.RecordingStatementInspector")
class QueryAuthorizerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EntityManager em;

    @BeforeEach
    void seed() {
        SecuredCustomer acme = new SecuredCustomer(1L, "Acme");
        SecuredCustomer beta = new SecuredCustomer(2L, "Beta");
        em.persist(acme);
        em.persist(beta);
        em.persist(new SecuredOrder(1L, "t1", "NEW", new BigDecimal("100.00"), acme));
        em.persist(new SecuredOrder(2L, "t1", "PAID", new BigDecimal("200.00"), acme));
        em.persist(new SecuredOrder(3L, "t1", "SHIPPED", new BigDecimal("50.00"), beta));
        em.persist(new SecuredOrder(4L, "t2", "NEW", new BigDecimal("999.00"), acme));
        em.persist(new SecuredOrder(5L, "t2", "PAID", new BigDecimal("300.00"), beta));

        Supplier globex = new Supplier(1L, "Globex");
        em.persist(globex);
        em.persist(new Widget(1L, "Gizmo", 10, globex));
        em.persist(new Widget(2L, "Gadget", 20, globex));

        em.flush();
        em.clear();
        RecordingStatementInspector.STATEMENTS.clear();
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void rowsAndTotalsAreScopedToTheCurrentTenant() throws Exception {
        TenantContext.set("t1");

        mvc.perform(post("/api/bq/securedOrder/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id","tenantId","status"],"sort":[{"field":"id","direction":"ASC"}],
                         "page":{"number":0,"size":1}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.rows.length()").value(1));

        mvc.perform(post("/api/bq/securedOrder/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id","tenantId"],"page":{"number":0,"size":50}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(3))
                .andExpect(jsonPath("$.rows[*].tenantId", everyItem(equalTo("t1"))));
    }

    @Test
    void topLevelOrCannotBypassTheMandatoryPredicate() throws Exception {
        TenantContext.set("t1");

        // globally NEW or PAID = orders 1,2,4,5 ; tenant t1 keeps only 1,2
        mvc.perform(post("/api/bq/securedOrder/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "select": ["id", "tenantId", "status"],
                          "filters": { "logic": "or", "children": [
                            { "field": "status", "op": "EQ", "value": "NEW" },
                            { "field": "status", "op": "EQ", "value": "PAID" }
                          ] },
                          "sort": [{ "field": "id", "direction": "ASC" }],
                          "page": { "number": 0, "size": 50 }
                        }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.rows[*].id", equalTo(List.of(1, 2))))
                .andExpect(jsonPath("$.rows[*].tenantId", everyItem(equalTo("t1"))));
    }

    @Test
    void missingTenantContextDeniesQueryAndMetadata() throws Exception {
        mvc.perform(post("/api/bq/securedOrder/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id"],"page":{"number":0,"size":10}}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("access_denied"));

        mvc.perform(get("/api/bq/securedOrder/metadata"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mandatoryPredicateIsInBothTheRowQueryAndTheCountQuery() throws Exception {
        TenantContext.set("t1");
        RecordingStatementInspector.STATEMENTS.clear();

        mvc.perform(post("/api/bq/securedOrder/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id","customer.name"],"page":{"number":0,"size":50}}"""))
                .andExpect(status().isOk());

        List<String> orderStatements = RecordingStatementInspector.STATEMENTS.stream()
                .map(sql -> sql.toLowerCase(Locale.ROOT))
                .filter(sql -> sql.contains("secured_order"))
                .toList();

        Assertions.assertThat(orderStatements).hasSize(2); // the SELECT and the COUNT
        Assertions.assertThat(orderStatements).allSatisfy(sql ->
                Assertions.assertThat(sql).contains("tenant_id"));
    }

    @Test
    void entitiesWithoutTheTenantColumnAreUnaffected() throws Exception {
        TenantContext.set("t1"); // set, but the authorizer does not support "widget"

        mvc.perform(post("/api/bq/widget/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id","name"],"page":{"number":0,"size":50}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        TenantAuthorizer tenantAuthorizer() {
            return new TenantAuthorizer();
        }
    }

    /** A host authorizer forcing {@code tenantId EQ <current tenant>} on every supported entity. */
    static class TenantAuthorizer implements QueryAuthorizer {

        @Override
        public boolean supports(EntityMetadata meta) {
            return meta.field("tenantId").isPresent();
        }

        @Override
        public QueryAuthorization authorize(QueryAuthorizationContext ctx) {
            String tenant = TenantContext.get();
            if (tenant == null) {
                return QueryAuthorization.deny();
            }
            return QueryAuthorization.allowWith(new MandatoryFilter("tenantId", FilterOperator.EQ, tenant));
        }
    }
}
