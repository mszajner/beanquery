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
package io.github.mszajner.beanquery.starter.refit;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import io.github.mszajner.beanquery.core.security.MandatoryFilter;
import io.github.mszajner.beanquery.core.security.QueryAuthorization;
import io.github.mszajner.beanquery.core.security.QueryAuthorizationContext;
import io.github.mszajner.beanquery.core.security.QueryAuthorizer;
import jakarta.persistence.EntityManager;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end proof of the reference-resolver feature: a {@code RefOrder} entity that
 * carries only {@code customerId} (no JPA association) is enriched and filtered against
 * a {@code customers} table read by a spy
 * {@link io.github.mszajner.beanquery.core.reference.ReferenceResolver} through a plain
 * {@link JdbcTemplate}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(ReferenceResolverIT.Config.class)
class ReferenceResolverIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CountingCustomerResolver resolver;

    @BeforeEach
    void seed() {
        // Not a JPA entity - Hibernate's create-drop does not manage it. H2 DDL auto-commits,
        // so the table survives the per-test @Transactional rollback; its rows are re-seeded here.
        jdbc.execute("CREATE TABLE IF NOT EXISTS customers "
                + "(id BIGINT PRIMARY KEY, name VARCHAR(64), tier VARCHAR(16))");
        jdbc.update("DELETE FROM customers");
        jdbc.update("INSERT INTO customers VALUES (10,'Acme','gold'),(11,'Beta','silver'),(12,'Ceres','gold')");

        em.persist(new RefOrder(1L, "NEW", 10L));
        em.persist(new RefOrder(2L, "NEW", 11L));
        em.persist(new RefOrder(3L, "PAID", 10L));
        em.persist(new RefOrder(4L, "NEW", null));
        em.persist(new RefOrder(5L, "PAID", 99L)); // customer row does not exist
        em.flush();
        em.clear();

        resolver.resolveCalls.set(0);
        resolver.resolveFilterCalls.set(0);
        MandatoryCustomerContext.clear();
    }

    @AfterEach
    void clearContext() {
        MandatoryCustomerContext.clear();
    }

    @Test
    void selectEnrichesFromTheCustomersTableWithOneResolveCall() throws Exception {
        mvc.perform(post("/api/bq/refOrder/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id","customer.name"],"sort":[{"field":"id","direction":"ASC"}],
                         "page":{"number":0,"size":50}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[?(@.id == 1)].['customer.name']", contains("Acme")))
                .andExpect(jsonPath("$.rows[?(@.id == 2)].['customer.name']", contains("Beta")))
                .andExpect(jsonPath("$.rows[?(@.id == 4)].['customer.name']", contains((Object) null)))
                .andExpect(jsonPath("$.rows[?(@.id == 5)].['customer.name']", contains((Object) null)));

        Assertions.assertThat(resolver.resolveCalls.get()).isEqualTo(1);
    }

    @Test
    void filterOnReferenceFieldReturnsOnlyMatchingOrdersWithCorrectTotal() throws Exception {
        mvc.perform(post("/api/bq/refOrder/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id"],"filters":{"field":"customer.name","op":"ILIKE","value":"acme"},
                         "sort":[{"field":"id","direction":"ASC"}],"page":{"number":0,"size":50}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.rows[*].id", equalTo(List.of(1, 3))));
    }

    @Test
    void filterResolvingToNoIdsYieldsZeroRowsAndZeroTotal() throws Exception {
        mvc.perform(post("/api/bq/refOrder/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id"],"filters":{"field":"customer.name","op":"ILIKE","value":"zzz"},
                         "page":{"number":0,"size":50}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.rows.length()").value(0));
    }

    @Test
    void sortOnReferenceFieldIs400() throws Exception {
        mvc.perform(post("/api/bq/refOrder/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id"],"sort":[{"field":"customer.name","direction":"ASC"}],
                         "page":{"number":0,"size":10}}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void referenceFilterInsideOrOnlyConstrainsItsBranch() throws Exception {
        // (customer.name ILIKE "acme" -> orders 1,3) OR (status EQ "NEW" -> orders 1,2,4) = 1,2,3,4
        // order 5 (status PAID, customerId 99 -> no customer row) must NOT appear: the IN list
        // from the reference branch does not leak into the status branch.
        mvc.perform(post("/api/bq/refOrder/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id"],"filters":{"logic":"or","children":[
                           {"field":"customer.name","op":"ILIKE","value":"acme"},
                           {"field":"status","op":"EQ","value":"NEW"}]},
                         "sort":[{"field":"id","direction":"ASC"}],"page":{"number":0,"size":50}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.rows[*].id", equalTo(List.of(1, 2, 3, 4))));
    }

    @Test
    void mandatoryReferenceFilterResolvingToEmptyDeniesRatherThanWidens() throws Exception {
        MandatoryCustomerContext.enable(); // authorizer now forces customer.name ILIKE "nobody"

        mvc.perform(post("/api/bq/refOrder/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id","status"],"sort":[{"field":"id","direction":"ASC"}],
                         "page":{"number":0,"size":50}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.rows.length()").value(0));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        CountingCustomerResolver customerResolver(JdbcTemplate jdbc) {
            return new CountingCustomerResolver(jdbc);
        }

        @Bean
        MandatoryCustomerAuthorizer mandatoryCustomerAuthorizer() {
            return new MandatoryCustomerAuthorizer();
        }
    }

    /** Stand-in for however a host would decide to force a reference predicate onto a call. */
    static final class MandatoryCustomerContext {

        private static final ThreadLocal<Boolean> ENABLED = ThreadLocal.withInitial(() -> Boolean.FALSE);

        private MandatoryCustomerContext() {
        }

        static void enable() {
            ENABLED.set(Boolean.TRUE);
        }

        static boolean enabled() {
            return ENABLED.get();
        }

        static void clear() {
            ENABLED.remove();
        }
    }

    /**
     * When enabled for the current thread, forces {@code customer.name ILIKE "nobody"} - a
     * reference predicate that resolves to no ids - onto every {@code refOrder} call.
     */
    static final class MandatoryCustomerAuthorizer implements QueryAuthorizer {

        @Override
        public boolean supports(EntityMetadata meta) {
            return "refOrder".equals(meta.name());
        }

        @Override
        public QueryAuthorization authorize(QueryAuthorizationContext ctx) {
            if (!MandatoryCustomerContext.enabled()) {
                return QueryAuthorization.allow();
            }
            return QueryAuthorization.allowWith(
                    new MandatoryFilter("customer.name", FilterOperator.ILIKE, "nobody"));
        }
    }
}
