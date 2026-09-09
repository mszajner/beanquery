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
package io.github.mszajner.beanquery.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a JPA entity as exposed through the beanquery REST API.
 *
 * <p>The {@link #name()} is the identifier used in the URL
 * ({@code /api/bq/{name}/metadata}, {@code /api/bq/{name}/query}). When left
 * blank it defaults to the entity's simple class name in {@code lowerCamelCase}
 * (e.g. {@code Customer} &rarr; {@code customer}, {@code OrderLine} &rarr;
 * {@code orderLine}).
 *
 * <p>Only fields additionally annotated with {@link QueryableField} are
 * registered; everything else stays invisible to the API (whitelist-first).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Queryable {

    /**
     * URL identifier for this entity. Blank means "derive from the class name".
     */
    String name() default "";
}
