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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
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
 * Pins the AND/OR filter-tree examples from {@code demo.http} to the row counts
 * they produce against {@code data.sql}, so the two stay in sync.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DemoOrFilterTest {

    @Autowired
    private MockMvc mvc;

    private void expectTotal(String body, int expectedTotal) throws Exception {
        mvc.perform(post("/api/bq/product/query").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(expectedTotal));
    }

    @Test
    void a_legacyFlatArrayStillWorks_elektronikaAndPro() throws Exception {
        // same conditions as (e) but the old flat array -> AND
        expectTotal("""
                {
                  "select": ["id"],
                  "filters": [
                    { "field": "category.name", "op": "EQ", "value": "Elektronika" },
                    { "field": "name", "op": "ILIKE", "value": "pro" }
                  ],
                  "page": { "number": 0, "size": 100 }
                }""", 4);
    }

    @Test
    void d_orsJoinedByAnd_categoriesAndPriceOrPro() throws Exception {
        expectTotal("""
                {
                  "select": ["id"],
                  "filters": {
                    "logic": "and",
                    "children": [
                      { "logic": "or", "children": [
                        { "field": "category.name", "op": "EQ", "value": "Elektronika" },
                        { "field": "category.name", "op": "EQ", "value": "Audio" }
                      ] },
                      { "logic": "or", "children": [
                        { "field": "price", "op": "LT", "value": 100 },
                        { "field": "name", "op": "ILIKE", "value": "pro" }
                      ] }
                    ]
                  },
                  "page": { "number": 0, "size": 100 }
                }""", 9);
    }

    @Test
    void b_simpleOr_priceLt50OrPriceGt500() throws Exception {
        expectTotal("""
                {
                  "select": ["id"],
                  "filters": {
                    "logic": "or",
                    "children": [
                      { "field": "price", "op": "LT", "value": 50 },
                      { "field": "price", "op": "GT", "value": 500 }
                    ]
                  },
                  "page": { "number": 0, "size": 100 }
                }""", 29);
    }

    @Test
    void c_andWithNestedOr_elektronikaAndProOrExpensive() throws Exception {
        expectTotal("""
                {
                  "select": ["id"],
                  "filters": {
                    "logic": "and",
                    "children": [
                      { "field": "category.name", "op": "EQ", "value": "Elektronika" },
                      {
                        "logic": "or",
                        "children": [
                          { "field": "name", "op": "ILIKE", "value": "pro" },
                          { "field": "price", "op": "GT", "value": 500 }
                        ]
                      }
                    ]
                  },
                  "page": { "number": 0, "size": 100 }
                }""", 5);
    }

    @Test
    void e_orOnNestedField_elektronikaOrPro() throws Exception {
        expectTotal("""
                {
                  "select": ["id", "name", "category.name"],
                  "filters": {
                    "logic": "or",
                    "children": [
                      { "field": "category.name", "op": "EQ", "value": "Elektronika" },
                      { "field": "name", "op": "ILIKE", "value": "pro" }
                    ]
                  },
                  "page": { "number": 0, "size": 100 }
                }""", 22);
    }

    @Test
    void e_orIncludesNoCategoryRowsViaTheSecondBranch() throws Exception {
        // "Pro Drone X" (51) and "Pocket Pro Mic" (52) have no category, yet match name ILIKE "pro"
        mvc.perform(post("/api/bq/product/query").contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "select": ["id", "name", "category.name"],
                  "filters": {
                    "logic": "or",
                    "children": [
                      { "field": "category.name", "op": "EQ", "value": "Elektronika" },
                      { "field": "name", "op": "ILIKE", "value": "pro" }
                    ]
                  },
                  "sort": [{ "field": "id", "direction": "ASC" }],
                  "page": { "number": 0, "size": 100 }
                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[*].id", hasItem(51)))
                .andExpect(jsonPath("$.rows[*].id", hasItem(52)))
                .andExpect(jsonPath("$.rows[?(@.id == 51)].name", hasItem("Pro Drone X")));
    }

    @Test
    void f_requestBeyondMaxFilterDepthIs400() throws Exception {
        mvc.perform(post("/api/bq/product/query").contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "select": ["id"],
                  "filters": {
                    "logic": "and", "children": [{
                      "logic": "and", "children": [{
                        "logic": "and", "children": [{
                          "logic": "and", "children": [{
                            "logic": "and", "children": [{
                              "logic": "and", "children": [
                                { "field": "price", "op": "GT", "value": 0 }
                              ]
                            }]
                          }]
                        }]
                      }]
                    }]
                  },
                  "page": { "number": 0, "size": 10 }
                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors", hasItem(containsString("depth"))));
    }
}
