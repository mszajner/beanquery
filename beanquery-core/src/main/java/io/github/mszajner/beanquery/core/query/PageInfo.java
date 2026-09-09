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

/**
 * Paging metadata returned alongside a page of rows.
 *
 * @param number        zero-based index of the returned page
 * @param size          requested page size
 * @param totalElements total number of rows matching the filters
 * @param totalPages     total number of pages at this size ({@code 0} when empty)
 */
public record PageInfo(int number, int size, long totalElements, int totalPages) {
}
