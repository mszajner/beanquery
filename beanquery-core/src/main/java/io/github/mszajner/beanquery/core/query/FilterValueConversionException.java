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

import java.util.List;

/**
 * Thrown by {@link FilterValueConverter} when a filter value cannot be coerced
 * to the target field type. Each message names the field, the expected type and
 * the received value. Callers add {@link #getErrors()} to the request's
 * validation error list - a bad value is a 400, never a 500.
 */
public class FilterValueConversionException extends RuntimeException {

    private final List<String> errors;

    public FilterValueConversionException(List<String> errors) {
        super("Filter value conversion failed: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public FilterValueConversionException(String error) {
        this(List.of(error));
    }

    /** One entry per value that failed to convert. */
    public List<String> getErrors() {
        return errors;
    }
}
