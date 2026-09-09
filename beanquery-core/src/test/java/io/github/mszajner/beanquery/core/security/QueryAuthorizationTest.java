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
package io.github.mszajner.beanquery.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import io.github.mszajner.beanquery.core.security.QueryAuthorization.Decision;

class QueryAuthorizationTest {

    @Test
    void allowHasNoRestrictions() {
        QueryAuthorization a = QueryAuthorization.allow();
        assertThat(a.decision()).isEqualTo(Decision.ALLOW);
        assertThat(a.isDenied()).isFalse();
        assertThat(a.mandatoryFilters()).isEmpty();
        assertThat(a.hiddenFields()).isEmpty();
    }

    @Test
    void deny() {
        assertThat(QueryAuthorization.deny().isDenied()).isTrue();
    }

    @Test
    void allowWithMandatoryFilters() {
        MandatoryFilter f = new MandatoryFilter("tenantId", FilterOperator.EQ, "t1");
        assertThat(QueryAuthorization.allowWith(f).mandatoryFilters()).containsExactly(f);
        assertThat(QueryAuthorization.allowWith(List.of(f)).decision()).isEqualTo(Decision.ALLOW);
    }

    @Test
    void nullCollectionsAreNormalised() {
        QueryAuthorization a = new QueryAuthorization(Decision.ALLOW, null, null);
        assertThat(a.mandatoryFilters()).isEmpty();
        assertThat(a.hiddenFields()).isEmpty();
    }

    @Test
    void builderCombinesMandatoryFiltersAndHiddenFields() {
        QueryAuthorization a = QueryAuthorization.builder()
                .mandatoryFilter("tenantId", FilterOperator.EQ, "t1")
                .mandatoryFilter(new MandatoryFilter("region", FilterOperator.IN, List.of("EU", "US")))
                .hideField("purchasePrice")
                .hideFields(List.of("margin", "supplierCost"))
                .allow();

        assertThat(a.decision()).isEqualTo(Decision.ALLOW);
        assertThat(a.mandatoryFilters()).extracting(MandatoryFilter::field).containsExactly("tenantId", "region");
        assertThat(a.hiddenFields()).containsExactlyInAnyOrder("purchasePrice", "margin", "supplierCost");
    }

    @Test
    void builderCanDeny() {
        assertThat(QueryAuthorization.builder().hideField("x").deny().isDenied()).isTrue();
    }
}
