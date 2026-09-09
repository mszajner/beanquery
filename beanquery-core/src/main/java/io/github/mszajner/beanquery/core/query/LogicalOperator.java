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
package io.github.mszajner.beanquery.core.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

/** How a {@link GroupNode}'s children are combined. */
public enum LogicalOperator {
    AND,
    OR;

    /**
     * Case-insensitive parse ({@code "and"}, {@code "AND"}, …). An unrecognised
     * value yields {@code null} so the validator can report it.
     */
    @JsonCreator
    public static LogicalOperator fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
