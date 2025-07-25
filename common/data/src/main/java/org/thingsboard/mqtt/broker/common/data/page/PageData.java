/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.common.data.page;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class PageData<T> {

    private final List<T> data;
    private final int totalPages;
    private final long totalElements;
    private final boolean hasNext;

    public PageData() {
        this(Collections.emptyList(), 0, 0, false);
    }

    @JsonCreator
    public PageData(@JsonProperty("data") List<T> data,
                    @JsonProperty("totalPages") int totalPages,
                    @JsonProperty("totalElements") long totalElements,
                    @JsonProperty("hasNext") boolean hasNext) {
        this.data = data;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
        this.hasNext = hasNext;
    }

    public static <T> PageData<T> emptyPageData() {
        return new PageData<>();
    }

    public static <T> PageData<T> of(List<T> data, int size, PageLink pageLink) {
        int totalPages = (int) Math.ceil((double) size / pageLink.getPageSize());
        return new PageData<>(data,
                totalPages,
                size,
                pageLink.getPage() < totalPages - 1);
    }

    public PageData<T> copyWithNewData(List<T> updatedData) {
        return new PageData<>(updatedData, getTotalPages(), getTotalElements(), hasNext());
    }

    @JsonProperty("hasNext")
    public boolean hasNext() {
        return hasNext;
    }

}
