/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
import java.util.HashSet;
import java.util.Set;

public class CollectionsUtil {
    public static <T> Set<T> getAddedValues(Collection<T> newValues, Collection<T> oldValues) {
        Set<T> addedValues = new HashSet<>(newValues);
        addedValues.removeAll(oldValues);
        return addedValues;
    }

    public static <T> Set<T> getRemovedValues(Collection<T> newValues, Collection<T> oldValues) {
        Set<T> removedValues = new HashSet<>(oldValues);
        removedValues.removeAll(newValues);
        return removedValues;
    }
}
