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

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The starter, unmodified, exposes the full REST API against a real JPA/H2
 * context purely via {@code META-INF/spring/*.AutoConfiguration.imports}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BeanQueryAutoConfigurationIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EntityManager em;

    @BeforeEach
    void seed() {
        Supplier globex = new Supplier(1L, "Globex");
        Supplier initech = new Supplier(2L, "Initech");
        em.persist(globex);
        em.persist(initech);
        em.persist(new Widget(1L, "Gizmo", 10, globex));
        em.persist(new Widget(2L, "Gadget", 20, globex));
        em.persist(new Widget(3L, "Sprocket", 5, initech));
        em.flush();
    }

    @Test
    void metadataEndpointIsServed() throws Exception {
        mvc.perform(get("/api/bq/widget/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity").value("widget"))
                .andExpect(jsonPath("$.fields[*].name", hasItem("quantity")))
                .andExpect(jsonPath("$.fields[*].name", hasItem("supplier.name")))
                .andExpect(jsonPath("$.fields[?(@.name == 'quantity')].type", hasItem("number")))
                .andExpect(jsonPath("$.capabilities.filterLogic", hasItem("OR")))
                .andExpect(jsonPath("$.capabilities.maxFilterDepth").value(5))
                .andExpect(jsonPath("$.capabilities.maxFilterConditions").value(50));
    }

    @Test
    void filterTreeWithAndOrIsExecuted() throws Exception {
        // quantity < 8  OR  (supplier.name = 'Globex' AND quantity >= 15)
        mvc.perform(post("/api/bq/widget/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "select": ["id", "name"],
                                  "filters": {
                                    "logic": "or",
                                    "children": [
                                      { "field": "quantity", "op": "LT", "value": 8 },
                                      {
                                        "logic": "and",
                                        "children": [
                                          { "field": "supplier.name", "op": "EQ", "value": "Globex" },
                                          { "field": "quantity", "op": "GTE", "value": 15 }
                                        ]
                                      }
                                    ]
                                  },
                                  "sort": [{ "field": "id", "direction": "ASC" }],
                                  "page": { "number": 0, "size": 10 }
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].name").value("Gadget"))
                .andExpect(jsonPath("$.rows[1].name").value("Sprocket"))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void queryEndpointRunsThroughValidatorConverterAndExecutor() throws Exception {
        mvc.perform(post("/api/bq/widget/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "select": ["id", "name", "supplier.name"],
                                  "filters": [{"field": "quantity", "op": "GTE", "value": 15}],
                                  "sort": [{"field": "name", "direction": "ASC"}],
                                  "page": {"number": 0, "size": 10}
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].name").value("Gadget"))
                .andExpect(jsonPath("$.rows[0]['supplier.name']").value("Globex"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void invalidQueryReturns400WithErrorList() throws Exception {
        mvc.perform(post("/api/bq/widget/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"select": ["ghost"], "page": {"number": 0, "size": 10}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors", hasItem(org.hamcrest.Matchers.containsString("ghost"))));
    }

    @Test
    void unknownEntityReturns404() throws Exception {
        mvc.perform(get("/api/bq/nope/metadata")).andExpect(status().isNotFound());
    }

    @Test
    void missingPageUsesConfiguredDefaultPageSize() throws Exception {
        mvc.perform(post("/api/bq/widget/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"select\": [\"id\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(0));
    }
}
