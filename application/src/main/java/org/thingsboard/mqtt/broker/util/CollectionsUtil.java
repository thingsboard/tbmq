/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.util;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BinaryOperator;

public class CollectionsUtil {

    public static <T> Set<T> getAddedValues(Collection<T> newValues, Collection<T> oldValues, Comparator<? super T> comparator) {
        return getValues(newValues, oldValues, comparator, (newValuesTreeSet, oldValuesTreeSet) -> {
            newValuesTreeSet.removeAll(oldValuesTreeSet);
            return newValuesTreeSet;
        });
    }

    public static <T> Set<T> getRemovedValues(Collection<T> newValues, Collection<T> oldValues, Comparator<? super T> comparator) {
        return getValues(newValues, oldValues, comparator, (newValuesTreeSet, oldValuesTreeSet) -> {
            oldValuesTreeSet.removeAll(newValuesTreeSet);
            return oldValuesTreeSet;
        });
    }

    private static <T> Set<T> getValues(Collection<T> newValues, Collection<T> oldValues, Comparator<? super T> comparator,
                                        BinaryOperator<TreeSet<T>> binaryOperator) {
        TreeSet<T> newValuesTreeSet = new TreeSet<>(comparator);
        newValuesTreeSet.addAll(newValues);
        TreeSet<T> oldValuesTreeSet = new TreeSet<>(comparator);
        oldValuesTreeSet.addAll(oldValues);

        return binaryOperator.apply(newValuesTreeSet, oldValuesTreeSet);
    }
}
