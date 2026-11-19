/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.rocksdb.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * What one listable type keeps in memory per document, see {@link ProjectionIndex}: the properties a page can be
 * sorted by, the field its text search runs on, and the fields its DAO filters on. A field can be both sortable and
 * filterable; it is then kept once. An enum value sorts by its name and a UUID by its unsigned big-endian bytes, as the
 * SQL columns do.
 *
 * @param <T> the domain type
 */
public final class Projection<T> {

    /**
     * {@code names.size()} consecutive values computed from a document by one call.
     */
    private record Group<T>(List<String> names, Function<T, Object[]> values) {
    }

    private final List<Group<T>> groups;
    private final Function<T, String> textSearch;
    private final String[] names;
    private final Map<String, Integer> sortSlots;
    private final Map<String, Integer> filterSlots;
    private final Map<String, Function<T, Comparable<?>>> sortProperties;
    private final Map<String, Function<T, Object>> filterFields;

    private Projection(Builder<T> builder) {
        this.groups = List.copyOf(builder.groups);
        this.textSearch = builder.textSearch;
        List<String> allNames = new ArrayList<>();
        groups.forEach(group -> allNames.addAll(group.names()));
        this.names = allNames.toArray(String[]::new);
        Map<String, Integer> sortSlots = new LinkedHashMap<>();
        Map<String, Integer> filterSlots = new LinkedHashMap<>();
        Map<String, Function<T, Comparable<?>>> sortProperties = new LinkedHashMap<>();
        Map<String, Function<T, Object>> filterFields = new LinkedHashMap<>();
        int slot = 0;
        for (Group<T> group : groups) {
            for (int i = 0; i < group.names().size(); i++, slot++) {
                String name = group.names().get(i);
                int position = i;
                Function<T, Object> value = document -> group.values().apply(document)[position];
                if (builder.sortable.contains(name)) {
                    sortSlots.put(name, slot);
                    sortProperties.put(name, document -> (Comparable<?>) value.apply(document));
                }
                if (builder.filterable.contains(name)) {
                    filterSlots.put(name, slot);
                    filterFields.put(name, value);
                }
            }
        }
        this.sortSlots = Collections.unmodifiableMap(sortSlots);
        this.filterSlots = Collections.unmodifiableMap(filterSlots);
        this.sortProperties = Collections.unmodifiableMap(sortProperties);
        this.filterFields = Collections.unmodifiableMap(filterFields);
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Each property a {@code SortOrder} may name, under that name, and its value.
     */
    public Map<String, Function<T, Comparable<?>>> sortProperties() {
        return sortProperties;
    }

    /**
     * The field the SQL query's {@code LOWER(..) LIKE} runs on; {@code null} when the type's page has no text search.
     */
    public Function<T, String> textSearch() {
        return textSearch;
    }

    /**
     * The fields a DAO filters a page on, by name.
     */
    public Map<String, Function<T, Object>> filterFields() {
        return filterFields;
    }

    /**
     * The number of values {@link #values} returns: one per field, then the text-search value.
     */
    int slotCount() {
        return names.length + 1;
    }

    int textSearchSlot() {
        return names.length;
    }

    /**
     * The values of every field of {@code document} in slot order, then its text-search value; each group of fields
     * is computed once.
     */
    Object[] values(T document) {
        Object[] values = new Object[slotCount()];
        int slot = 0;
        for (Group<T> group : groups) {
            Object[] groupValues = group.values().apply(document);
            System.arraycopy(groupValues, 0, values, slot, group.names().size());
            slot += group.names().size();
        }
        values[slot] = textSearch == null ? null : textSearch.apply(document);
        return values;
    }

    /**
     * The slot of each sort property, in declaration order.
     */
    Map<String, Integer> sortSlots() {
        return sortSlots;
    }

    /**
     * The slot of each filter field, in declaration order.
     */
    Map<String, Integer> filterSlots() {
        return filterSlots;
    }

    public static final class Builder<T> {

        private final List<Group<T>> groups = new ArrayList<>();
        private final Set<String> names = new HashSet<>();
        private final Set<String> sortable = new HashSet<>();
        private final Set<String> filterable = new HashSet<>();
        private Function<T, String> textSearch;

        private Builder() {
        }

        public Builder<T> sort(String name, Function<T, ? extends Comparable<?>> value) {
            return field(name, value, true, false);
        }

        public Builder<T> filter(String name, Function<T, ?> value) {
            return field(name, value, false, true);
        }

        public Builder<T> sortAndFilter(String name, Function<T, ? extends Comparable<?>> value) {
            return field(name, value, true, true);
        }

        /**
         * Filter fields whose values come from one computation, such as one parse of a stored JSON text; {@code values}
         * returns them in the order of {@code names}.
         */
        public Builder<T> filters(List<String> names, Function<T, Object[]> values) {
            names.forEach(this::claim);
            filterable.addAll(names);
            groups.add(new Group<>(List.copyOf(names), values));
            return this;
        }

        public Builder<T> textSearch(Function<T, String> value) {
            this.textSearch = value;
            return this;
        }

        public Projection<T> build() {
            return new Projection<>(this);
        }

        private Builder<T> field(String name, Function<T, ?> value, boolean sort, boolean filter) {
            claim(name);
            if (sort) {
                sortable.add(name);
            }
            if (filter) {
                filterable.add(name);
            }
            groups.add(new Group<>(List.of(name), document -> new Object[]{value.apply(document)}));
            return this;
        }

        private void claim(String name) {
            if (!names.add(name)) {
                throw new IllegalArgumentException("The projection declares the field " + name + " twice");
            }
        }

    }

}
