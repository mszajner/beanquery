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
package io.github.mszajner.beanquery.starter.refit;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application for the reference-resolver end-to-end test. Kept in its own package so its
 * {@link RefOrder} entity - which needs a {@code customer} {@code ReferenceResolver} bean
 * to satisfy the starter's fail-fast check - is not entity-scanned by the other starter
 * ITs under {@code ...starter.it}.
 */
@SpringBootApplication
public class RefTestApp {
}
