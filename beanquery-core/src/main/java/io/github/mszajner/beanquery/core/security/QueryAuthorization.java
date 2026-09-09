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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A {@link QueryAuthorizer}'s verdict for one call.
 *
 * @param decision        {@link Decision#ALLOW} or {@link Decision#DENY}
 * @param mandatoryFilters predicates {@code AND}-ed onto the request (never {@code null};
 *                         normalised to an empty list)
 * @param hiddenFields     field names invisible for this call - treated as if they did
 *                         not exist (never {@code null}; normalised to an empty set)
 */
public record QueryAuthorization(
        Decision decision,
        List<MandatoryFilter> mandatoryFilters,
        Set<String> hiddenFields) {

    public enum Decision {
        ALLOW,
        DENY
    }

    public QueryAuthorization {
        mandatoryFilters = (mandatoryFilters == null) ? List.of() : List.copyOf(mandatoryFilters);
        hiddenFields = (hiddenFields == null) ? Set.of() : Set.copyOf(hiddenFields);
    }

    public boolean isDenied() {
        return decision == Decision.DENY;
    }

    // -- factories -------------------------------------------------------

    /** Allow with no restrictions. */
    public static QueryAuthorization allow() {
        return new QueryAuthorization(Decision.ALLOW, List.of(), Set.of());
    }

    /** Deny access entirely (HTTP 403). */
    public static QueryAuthorization deny() {
        return new QueryAuthorization(Decision.DENY, List.of(), Set.of());
    }

    /** Allow, forcing these predicates onto every request. */
    public static QueryAuthorization allowWith(List<MandatoryFilter> mandatoryFilters) {
        return new QueryAuthorization(Decision.ALLOW, mandatoryFilters, Set.of());
    }

    /** Allow, forcing a single predicate onto every request. */
    public static QueryAuthorization allowWith(MandatoryFilter mandatoryFilter) {
        return allowWith(List.of(mandatoryFilter));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builds an {@link QueryAuthorization} combining mandatory filters and hidden fields. */
    public static final class Builder {

        private final List<MandatoryFilter> mandatoryFilters = new ArrayList<>();
        private final Set<String> hiddenFields = new LinkedHashSet<>();

        private Builder() {
        }

        public Builder mandatoryFilter(MandatoryFilter filter) {
            this.mandatoryFilters.add(filter);
            return this;
        }

        public Builder mandatoryFilter(String field, io.github.mszajner.beanquery.core.metadata.FilterOperator op, Object value) {
            return mandatoryFilter(new MandatoryFilter(field, op, value));
        }

        public Builder mandatoryFilters(Collection<MandatoryFilter> filters) {
            this.mandatoryFilters.addAll(filters);
            return this;
        }

        public Builder hideField(String name) {
            this.hiddenFields.add(name);
            return this;
        }

        public Builder hideFields(Collection<String> names) {
            this.hiddenFields.addAll(names);
            return this;
        }

        public QueryAuthorization allow() {
            return new QueryAuthorization(Decision.ALLOW, mandatoryFilters, hiddenFields);
        }

        public QueryAuthorization deny() {
            return new QueryAuthorization(Decision.DENY, mandatoryFilters, hiddenFields);
        }
    }
}
