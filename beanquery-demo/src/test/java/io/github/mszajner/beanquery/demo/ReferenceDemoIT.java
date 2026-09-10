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
package io.github.mszajner.beanquery.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pins the "Referencje między modułami" examples from {@code demo.http} to the rows
 * they produce against {@code data.sql}, so the two stay in sync. The {@code order}
 * module holds only {@code customer_id}; the name/tier come from the {@code customer}
 * module's {@code ReferenceResolver}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReferenceDemoIT {

    @Autowired
    private MockMvc mvc;

    @Test
    void selectWithCustomerName() throws Exception {
        // order 1 -> customer 1 (Acme Corp); order 8 has a NULL customer_id -> null name
        mvc.perform(post("/api/bq/order/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id","status","customer.name","customer.tier"],
                         "sort":[{"field":"id","direction":"ASC"}],"page":{"number":0,"size":50}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[?(@.id == 1)].['customer.name']").value(
                        org.hamcrest.Matchers.contains("Acme Corp")))
                .andExpect(jsonPath("$.rows[?(@.id == 1)].['customer.tier']").value(
                        org.hamcrest.Matchers.contains("gold")))
                .andExpect(jsonPath("$.rows[?(@.id == 8)].['customer.name']").value(
                        org.hamcrest.Matchers.contains((Object) null)));
    }

    @Test
    void filterOnCustomerNameIlike() throws Exception {
        // "acme" resolves to customer 1 -> orders 1 and 2
        mvc.perform(post("/api/bq/order/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id"],"filters":{"field":"customer.name","op":"ILIKE","value":"acme"},
                         "page":{"number":0,"size":50}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void filterResolvingToNothingIsZero() throws Exception {
        // no customer matches -> 0 rows and totalElements 0 (not "all rows")
        mvc.perform(post("/api/bq/order/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id"],"filters":{"field":"customer.name","op":"ILIKE","value":"nobody"},
                         "page":{"number":0,"size":50}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.rows.length()").value(0));
    }

    @Test
    void referenceFilterInsideOrBranch() throws Exception {
        // customer.name ILIKE "acme" -> orders {1,2}; status EQ NEW -> orders {1,3,6,8}
        // OR union -> {1,2,3,6,8} = 5
        mvc.perform(post("/api/bq/order/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id","status","customer.name"],
                         "filters":{"logic":"or","children":[
                           {"field":"customer.name","op":"ILIKE","value":"acme"},
                           {"field":"status","op":"EQ","value":"NEW"}
                         ]},
                         "sort":[{"field":"id","direction":"ASC"}],"page":{"number":0,"size":50}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.rows[*].id").value(
                        org.hamcrest.Matchers.contains(1, 2, 3, 6, 8)));
    }

    @Test
    void sortOnCustomerNameIsRejected() throws Exception {
        mvc.perform(post("/api/bq/order/query").contentType(MediaType.APPLICATION_JSON).content("""
                        {"select":["id"],"sort":[{"field":"customer.name","direction":"ASC"}],
                         "page":{"number":0,"size":10}}"""))
                .andExpect(status().isBadRequest());
    }
}
