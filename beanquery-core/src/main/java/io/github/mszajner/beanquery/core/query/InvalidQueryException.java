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
 * Thrown by {@link QueryRequestValidator} when a request violates one or more
 * whitelist / shape rules. Carries every error found, not just the first; the
 * web layer renders it as a 400 response.
 */
public class InvalidQueryException extends RuntimeException {

    private final List<String> errors;

    public InvalidQueryException(List<String> errors) {
        super("Invalid query: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    /** All validation errors, in the order they were detected. */
    public List<String> getErrors() {
        return errors;
    }
}
