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
package org.thingsboard.mqtt.broker.common.data.util;

import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;

import java.util.Comparator;
import java.util.function.Function;

public class ComparableUtil {

    public static <T, R extends Comparable<R>> Comparator<T> getComparatorBy(SortOrder sortOrder, Function<T, R> func) {
        Comparator<T> comparator = Comparator.comparing(func, Comparator.nullsLast(Comparator.naturalOrder()));
        return sortOrder.getDirection() == SortOrder.Direction.DESC ? comparator.reversed() : comparator;
    }

    public static <T> Comparator<T> sorted(PageLink pageLink, Function<SortOrder, Comparator<T>> comparatorSupplier) {
        return pageLink.getSortOrder() == null ? (o1, o2) -> 0 :
                Comparator.nullsLast(comparatorSupplier.apply(pageLink.getSortOrder()));
    }

}
