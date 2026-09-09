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

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Smoke test: the demo app boots, seeds 55 rows and serves the beanquery API. */
@SpringBootTest
@AutoConfigureMockMvc
class DemoApplicationIT {

    @Autowired
    private MockMvc mvc;

    @Test
    void productMetadataIsExposed() throws Exception {
        mvc.perform(get("/api/bq/product/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity").value("product"))
                .andExpect(jsonPath("$.fields[*].name", hasItem("category.name")))
                .andExpect(jsonPath("$.fields[?(@.name == 'price')].type", hasItem("number")))
                .andExpect(jsonPath("$.fields[?(@.name == 'releasedOn')].type", hasItem("date")))
                .andExpect(jsonPath("$.fields[?(@.name == 'status')].values", hasItem(hasItem("ACTIVE"))));
    }

    @Test
    void dataSqlSeededAllProducts() throws Exception {
        mvc.perform(post("/api/bq/product/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"select\":[\"id\"],\"page\":{\"number\":0,\"size\":1}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(55));
    }

    @Test
    void likeAndBetweenWithSortOnNestedCategory() throws Exception {
        mvc.perform(post("/api/bq/product/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "select": ["id", "name", "price", "releasedOn", "category.name"],
                                  "filters": [
                                    { "field": "name", "op": "ILIKE", "value": "pro" },
                                    { "field": "releasedOn", "op": "BETWEEN", "value": ["2024-01-01", "2024-12-31"] }
                                  ],
                                  "sort": [{ "field": "category.name", "direction": "ASC" }],
                                  "page": { "number": 0, "size": 10 }
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()", greaterThan(0)))
                .andExpect(jsonPath("$.rows[*].releasedOn", everyItem(greaterThan("2023-12-31"))))
                .andExpect(jsonPath("$.rows[*].releasedOn", everyItem(lessThanOrEqualTo("2024-12-31"))))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(10));
    }

    @Test
    void paginationExposesTotals() throws Exception {
        mvc.perform(post("/api/bq/product/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"select":["id"],"sort":[{"field":"id","direction":"ASC"}],"page":{"number":2,"size":20}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(15))
                .andExpect(jsonPath("$.page.number").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(55))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    void unknownFieldYields400() throws Exception {
        mvc.perform(post("/api/bq/product/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"select\":[\"nope\"],\"page\":{\"number\":0,\"size\":10}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }
}
