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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** {@code beanquery.*} properties flow end-to-end. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "beanquery.base-path=/bq2",
        "beanquery.default-page-size=1",
        "beanquery.max-page-size=5"
})
class BeanQueryConfigOverrideIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EntityManager em;

    @BeforeEach
    void seed() {
        Supplier globex = new Supplier(1L, "Globex");
        em.persist(globex);
        em.persist(new Widget(1L, "Gizmo", 10, globex));
        em.persist(new Widget(2L, "Gadget", 20, globex));
        em.persist(new Widget(3L, "Sprocket", 30, globex));
        em.flush();
    }

    @Test
    void apiIsServedUnderTheConfiguredBasePath() throws Exception {
        mvc.perform(get("/bq2/widget/metadata")).andExpect(status().isOk());
        mvc.perform(get("/api/bq/widget/metadata")).andExpect(status().isNotFound());
    }

    @Test
    void queryWithoutPageUsesTheConfiguredDefaultPageSize() throws Exception {
        mvc.perform(post("/bq2/widget/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"select\": [\"id\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    void pageSizeAboveTheConfiguredMaximumIsRejected() throws Exception {
        mvc.perform(post("/bq2/widget/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"select\": [\"id\"], \"page\": {\"number\": 0, \"size\": 10}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("page.size")));
    }
}
