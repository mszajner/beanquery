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
package io.github.mszajner.beanquery.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for beanquery, bound from the {@code beanquery.*} namespace.
 */
@ConfigurationProperties(prefix = "beanquery")
public class BeanQueryProperties {

    /** Whether the beanquery REST API is auto-configured. */
    private boolean enabled = true;

    /** Base path the REST API is mounted under. */
    private String basePath = "/api/bq";

    /** Largest page size a query request may ask for. */
    private int maxPageSize = 200;

    /** Page size used when a query request omits {@code page}. */
    private int defaultPageSize = 20;

    /** Maximum nesting depth of AND/OR filter groups. */
    private int maxFilterDepth = 5;

    /** Maximum total number of filter leaf conditions in one request. */
    private int maxFilterConditions = 50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    public int getMaxFilterDepth() {
        return maxFilterDepth;
    }

    public void setMaxFilterDepth(int maxFilterDepth) {
        this.maxFilterDepth = maxFilterDepth;
    }

    public int getMaxFilterConditions() {
        return maxFilterConditions;
    }

    public void setMaxFilterConditions(int maxFilterConditions) {
        this.maxFilterConditions = maxFilterConditions;
    }
}
