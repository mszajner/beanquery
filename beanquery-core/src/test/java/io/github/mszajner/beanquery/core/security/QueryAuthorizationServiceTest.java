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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import io.github.mszajner.beanquery.core.metadata.DefaultOperators;
import io.github.mszajner.beanquery.core.metadata.EntityMetadata;
import io.github.mszajner.beanquery.core.metadata.FieldKind;
import io.github.mszajner.beanquery.core.metadata.FieldMetadata;
import io.github.mszajner.beanquery.core.metadata.FilterOperator;
import io.github.mszajner.beanquery.core.query.ResolvedFilterNode;

@ExtendWith(OutputCaptureExtension.class)
class QueryAuthorizationServiceTest {

    private static final EntityMetadata ORDER = new EntityMetadata("order", Object.class, List.of(
            field("id", Long.class),
            field("status", String.class),
            field("tenantId", String.class),
            field("region", String.class)));

    private AppliedAuthorization authorize(List<QueryAuthorizer> authorizers) {
        return new QueryAuthorizationService(authorizers).authorize(ORDER, null);
    }

    @Test
    void noAuthorizersMeansAllowAll() {
        QueryAuthorizationService service = new QueryAuthorizationService(List.of());
        assertThat(service.hasAuthorizers()).isFalse();
        assertThat(service.authorize(ORDER, null)).isSameAs(AppliedAuthorization.ALLOW_ALL);
    }

    @Test
    void nonSupportingAuthorizerIsSkipped() {
        AppliedAuthorization applied = authorize(List.of(
                authorizer(false, QueryAuthorization.deny())));
        assertThat(applied.mandatoryPredicates()).isEmpty();
        assertThat(applied.hiddenFields()).isEmpty();
    }

    @Test
    void anyDenyThrows() {
        assertThatExceptionOfType(QueryAccessDeniedException.class).isThrownBy(() ->
                authorize(List.of(
                        authorizer(true, QueryAuthorization.allowWith(
                                new MandatoryFilter("tenantId", FilterOperator.EQ, "t1"))),
                        authorizer(true, QueryAuthorization.deny()))));
    }

    @Test
    void nullVerdictIsTreatedAsDeny() {
        assertThatExceptionOfType(QueryAccessDeniedException.class).isThrownBy(() ->
                authorize(List.of(authorizer(true, null))));
    }

    @Test
    void mandatoryFiltersAndHiddenFieldsAreAggregatedInOrder() {
        AppliedAuthorization applied = authorize(List.of(
                authorizer(true, QueryAuthorization.builder()
                        .mandatoryFilter("tenantId", FilterOperator.EQ, "t1")
                        .hideField("region")
                        .allow()),
                authorizer(true, QueryAuthorization.builder()
                        .mandatoryFilter("region", FilterOperator.EQ, "EU")
                        .hideField("status")
                        .allow())));

        assertThat(applied.mandatoryPredicates()).hasSize(2);
        assertThat(condition(applied, 0).field().name()).isEqualTo("tenantId");
        assertThat(condition(applied, 0).value()).isEqualTo("t1");
        assertThat(condition(applied, 1).field().name()).isEqualTo("region");
        assertThat(applied.hiddenFields()).containsExactlyInAnyOrder("region", "status");
    }

    @Test
    void processingOrderFollowsTheGivenList() {
        QueryAuthorizer a = authorizer(true, QueryAuthorization.allowWith(
                new MandatoryFilter("tenantId", FilterOperator.EQ, "a")));
        QueryAuthorizer b = authorizer(true, QueryAuthorization.allowWith(
                new MandatoryFilter("region", FilterOperator.EQ, "b")));

        assertThat(authorize(List.of(a, b)).mandatoryPredicates())
                .extracting(n -> ((ResolvedFilterNode.Condition) n).field().name())
                .containsExactly("tenantId", "region");
        assertThat(authorize(List.of(b, a)).mandatoryPredicates())
                .extracting(n -> ((ResolvedFilterNode.Condition) n).field().name())
                .containsExactly("region", "tenantId");
    }

    @Test
    void mandatoryFilterOnUnknownFieldIsAConfigurationError(CapturedOutput output) {
        assertThatExceptionOfType(QueryAuthorizerConfigurationException.class)
                .isThrownBy(() -> authorize(List.of(authorizer(true, QueryAuthorization.allowWith(
                        new MandatoryFilter("nope", FilterOperator.EQ, "x"))))))
                .withMessageContaining("order")
                .withMessageContaining("nope")
                .withMessageContaining("QueryAuthorizationServiceTest");
        assertThat(output).contains("unknown field 'nope'");
    }

    @Test
    void mandatoryFilterWithDisallowedOperatorIsAConfigurationError() {
        assertThatExceptionOfType(QueryAuthorizerConfigurationException.class)
                .isThrownBy(() -> authorize(List.of(authorizer(true, QueryAuthorization.allowWith(
                        new MandatoryFilter("tenantId", FilterOperator.GT, "x"))))))
                .withMessageContaining("operator GT is not allowed for field 'tenantId'");
    }

    @Test
    void hiddenFieldNotInMetadataIsWarnedAndDropped(CapturedOutput output) {
        AppliedAuthorization applied = authorize(List.of(
                authorizer(true, QueryAuthorization.builder().hideField("ghost").hideField("status").allow())));

        assertThat(applied.hiddenFields()).containsExactly("status");
        assertThat(output).contains("hides field 'ghost'").contains("not registered");
    }

    // -- helpers -------------------------------------------------------

    private static ResolvedFilterNode.Condition condition(AppliedAuthorization applied, int index) {
        return (ResolvedFilterNode.Condition) applied.mandatoryPredicates().get(index);
    }

    private static QueryAuthorizer authorizer(boolean supports, QueryAuthorization verdict) {
        return new QueryAuthorizer() {
            @Override
            public boolean supports(EntityMetadata meta) {
                return supports;
            }

            @Override
            public QueryAuthorization authorize(QueryAuthorizationContext ctx) {
                return verdict;
            }
        };
    }

    private static FieldMetadata field(String name, Class<?> type) {
        return new FieldMetadata(name, name, type, true, true, true, DefaultOperators.forType(type), FieldKind.COLUMN);
    }
}
