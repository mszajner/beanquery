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
package io.github.mszajner.beanquery.core.reference;

import io.github.mszajner.beanquery.core.metadata.QueryableMetadataException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Internal registry of {@link ReferenceResolver} beans, keyed by {@code referenceName()}. */
public final class ReferenceResolvers {

    /** No resolvers registered. */
    public static final ReferenceResolvers EMPTY = new ReferenceResolvers(List.of());

    private final Map<String, ReferenceResolver> byName = new LinkedHashMap<>();

    public ReferenceResolvers(List<ReferenceResolver> resolvers) {
        for (ReferenceResolver resolver : resolvers) {
            String name = resolver.referenceName();
            if (byName.putIfAbsent(name, resolver) != null) {
                throw new QueryableMetadataException(
                        "Duplicate ReferenceResolver for reference name '" + name + "': "
                                + byName.get(name).getClass().getName() + " and "
                                + resolver.getClass().getName());
            }
        }
    }

    public Optional<ReferenceResolver> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public ReferenceResolver require(String name) {
        return find(name).orElseThrow(() ->
                new IllegalStateException("No ReferenceResolver registered for reference name '" + name + "'"));
    }

    public Set<String> names() {
        return Set.copyOf(byName.keySet());
    }
}
