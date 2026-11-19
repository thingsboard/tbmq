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

import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;

import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The in-memory projection of one listable type: one {@link Row} per document, keyed by id. A page is filtered, text
 * searched and sorted here; only the ids of its slice leave, so the caller reads just those documents.
 * <p>
 * Rows are immutable and live in a {@link ConcurrentHashMap}: a writer replaces or removes a whole row, so a reader
 * that runs alongside sees each document either before or after a write, never half of it, with no lock. The writers
 * are serialized by the type lock of {@link RocksEntityStore}.
 */
final class ProjectionIndex<T> {

    /**
     * The id of a document and its {@link Projection#values}, kept compactly: a string that only has Latin-1
     * characters is kept as its Latin-1 bytes (24 bytes less than the {@link String} around them), and equal strings of
     * one document, the id included, are one object. {@link #expand} gives the string back.
     */
    record Row(Object id, Object[] values) {
    }

    /**
     * The ids of one page, in page order, and the number of documents that matched.
     */
    record Slice(List<Object> ids, long totalElements) {
    }

    private static final Comparator<Object> VALUE_ORDER = ProjectionIndex::compareValues;

    private final StoredEntityType type;
    private final Projection<T> projection;
    private final Map<Object, Row> rows = new ConcurrentHashMap<>();
    private final String[] sortNames;
    private final int[] sortSlots;
    private final String[] filterNames;
    private final int[] filterSlots;
    private final int createdTimeSlot;

    ProjectionIndex(StoredEntityType type, Projection<T> projection) {
        this.type = type;
        this.projection = projection;
        this.sortNames = projection.sortSlots().keySet().toArray(String[]::new);
        this.sortSlots = projection.sortSlots().values().stream().mapToInt(Integer::intValue).toArray();
        this.filterNames = projection.filterSlots().keySet().toArray(String[]::new);
        this.filterSlots = projection.filterSlots().values().stream().mapToInt(Integer::intValue).toArray();
        this.createdTimeSlot = projection.sortSlots().getOrDefault("createdTime", -1);
    }

    Row project(Object id, T document) {
        Object[] values = projection.values(document);
        Object[] originals = values.clone();
        for (int i = 0; i < values.length; i++) {
            if (originals[i] instanceof String value) {
                values[i] = compact(value, id, originals, values, i);
            }
        }
        return new Row(id, values);
    }

    void put(Row row) {
        rows.put(row.id(), row);
    }

    void remove(Object id) {
        rows.remove(id);
    }

    int size() {
        return rows.size();
    }

    /**
     * Filters by the text search and {@code filter}, sorts by the page link's sort order (by id without one), and
     * slices {@code [page * pageSize, page * pageSize + pageSize)}. Among equal sort keys the order is unspecified.
     *
     * @param filter {@code null} for none
     * @throws InvalidDataAccessApiUsageException for an unknown sort property, as the SQL DAO's paging refuses it
     * @throws IllegalArgumentException            for a page size below one or a negative page, as SQL paging refuses them
     */
    Slice find(PageLink pageLink, Predicate<RocksEntityStore.ProjectedEntity> filter) {
        if (pageLink.getPageSize() < 1) {
            throw new IllegalArgumentException("Page size must not be less than one");
        }
        if (pageLink.getPage() < 0) {
            throw new IllegalArgumentException("Page index must not be less than zero");
        }
        Comparator<Row> order = order(pageLink.getSortOrder());
        String search = pageLink.getTextSearch() == null || pageLink.getTextSearch().isEmpty() || projection.textSearch() == null
                ? null : pageLink.getTextSearch().toLowerCase(Locale.ROOT);
        int textSearchSlot = projection.textSearchSlot();
        List<Row> matched = new ArrayList<>();
        for (Row row : rows.values()) {
            if (search != null && !matches(row.values()[textSearchSlot], search)) {
                continue;
            }
            if (filter != null && !filter.test(projected(row))) {
                continue;
            }
            matched.add(row);
        }
        Row[] sorted = matched.toArray(Row[]::new);
        Arrays.sort(sorted, order);
        long from = (long) pageLink.getPage() * pageLink.getPageSize();
        long to = Math.min(sorted.length, from + pageLink.getPageSize());
        List<Object> ids = new ArrayList<>();
        for (long i = from; i < to; i++) {
            ids.add(sorted[(int) i].id());
        }
        return new Slice(ids, sorted.length);
    }

    private Comparator<Row> order(SortOrder sortOrder) {
        if (sortOrder == null) {
            return Comparator.comparing(Row::id, VALUE_ORDER);
        }
        Integer slot = projection.sortSlots().get(sortOrder.getProperty());
        if (slot == null) {
            throw new InvalidDataAccessApiUsageException("Unknown sort property '" + sortOrder.getProperty() + "' of " + type);
        }
        Comparator<Row> ascending = Comparator.comparing(row -> row.values()[slot], VALUE_ORDER);
        return sortOrder.getDirection() == SortOrder.Direction.DESC ? ascending.reversed() : ascending;
    }

    private RocksEntityStore.ProjectedEntity projected(Row row) {
        Object[] values = row.values();
        long createdTime = createdTimeSlot >= 0 && values[createdTimeSlot] instanceof Long time ? time : 0L;
        return new RocksEntityStore.ProjectedEntity(row.id(), createdTime, new SlotMap<>(sortNames, sortSlots, values),
                (String) expand(values[projection.textSearchSlot()]), new SlotMap<>(filterNames, filterSlots, values));
    }

    private static boolean matches(Object value, String lowerCaseSearch) {
        return value != null && ((String) expand(value)).toLowerCase(Locale.ROOT).contains(lowerCaseSearch);
    }

    /**
     * {@code value}, the {@code i}-th of {@code originals}, as it is kept: the kept form of an equal earlier value, the
     * id when it equals the id, its Latin-1 bytes when it has only Latin-1 characters, or itself.
     */
    private static Object compact(String value, Object id, Object[] originals, Object[] kept, int i) {
        for (int j = 0; j < i; j++) {
            if (value.equals(originals[j])) {
                return kept[j];
            }
        }
        if (value.equals(id)) {
            return id;
        }
        for (int c = 0; c < value.length(); c++) {
            if (value.charAt(c) > 0xFF) {
                return value;
            }
        }
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * A kept value as it was projected: the string of Latin-1 bytes, any other value as it is.
     */
    private static Object expand(Object kept) {
        return kept instanceof byte[] latin1 ? new String(latin1, StandardCharsets.ISO_8859_1) : kept;
    }

    /**
     * Ascending order of kept values with {@code null} last, as PostgreSQL sorts by default; reversed, {@code null}
     * comes first, as there. Strings compare as {@link #compareText} does, an enum by its name (the SQL column holds the
     * name) and a UUID by its unsigned big-endian bytes (PostgreSQL's {@code uuid} order).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareValues(Object a, Object b) {
        if (a == null || b == null) {
            return a == null ? (b == null ? 0 : 1) : -1;
        }
        if (a instanceof byte[] x && b instanceof byte[] y) {
            return compareText(x, y);
        }
        if (a instanceof byte[] || b instanceof byte[] || a instanceof String) {
            return compareText((String) expand(a), (String) expand(b));
        }
        if (a instanceof UUID x && b instanceof UUID y) {
            int high = Long.compareUnsigned(x.getMostSignificantBits(), y.getMostSignificantBits());
            return high != 0 ? high : Long.compareUnsigned(x.getLeastSignificantBits(), y.getLeastSignificantBits());
        }
        if (a instanceof Enum<?> x && b instanceof Enum<?> y) {
            return x.name().compareTo(y.name());
        }
        return ((Comparable) a).compareTo(b);
    }

    /**
     * The text order, close to the {@code en_US.utf8} collation that PostgreSQL gets by default in the images TBMQ
     * ships with: characters compare ignoring case, and of two texts that differ only in case, the one with the
     * lower-case character at the first such difference comes first ({@code ab, aB, Ab, AB, apple, Apple}). Accents
     * and punctuation still compare by their code, where that collation weighs them less: {@code f} sorts before
     * {@code éclair}, and {@code a-c} before {@code abc}.
     */
    private static int compareText(String x, String y) {
        int common = Math.min(x.length(), y.length());
        int caseOrder = 0;
        for (int i = 0; i < common; i++) {
            char a = x.charAt(i);
            char b = y.charAt(i);
            if (a != b) {
                int folded = Integer.compare(fold(a), fold(b));
                if (folded != 0) {
                    return folded;
                }
                if (caseOrder == 0) {
                    caseOrder = caseOrder(a, b);
                }
            }
        }
        return x.length() != y.length() ? Integer.compare(x.length(), y.length()) : caseOrder;
    }

    /**
     * {@link #compareText(String, String)} of two kept Latin-1 strings, whose bytes are their characters.
     */
    private static int compareText(byte[] x, byte[] y) {
        int common = Math.min(x.length, y.length);
        int caseOrder = 0;
        for (int i = 0; i < common; i++) {
            if (x[i] != y[i]) {
                char a = (char) (x[i] & 0xFF);
                char b = (char) (y[i] & 0xFF);
                int folded = Integer.compare(fold(a), fold(b));
                if (folded != 0) {
                    return folded;
                }
                if (caseOrder == 0) {
                    caseOrder = caseOrder(a, b);
                }
            }
        }
        return x.length != y.length ? Integer.compare(x.length, y.length) : caseOrder;
    }

    // the case folding of String.CASE_INSENSITIVE_ORDER
    private static int fold(char c) {
        return Character.toLowerCase(Character.toUpperCase(c));
    }

    // two different characters that fold alike: the lower-case one first, else by code
    private static int caseOrder(char a, char b) {
        boolean lowerA = Character.isLowerCase(a);
        return lowerA != Character.isLowerCase(b) ? (lowerA ? -1 : 1) : Character.compare(a, b);
    }

    /**
     * A read-only map of some of a row's values under their names; the names and slots are shared by every row.
     */
    private static final class SlotMap<V> extends AbstractMap<String, V> {

        private final String[] names;
        private final int[] slots;
        private final Object[] values;

        SlotMap(String[] names, int[] slots, Object[] values) {
            this.names = names;
            this.slots = slots;
            this.values = values;
        }

        @Override
        public int size() {
            return names.length;
        }

        @Override
        public boolean containsKey(Object key) {
            return position(key) >= 0;
        }

        @Override
        @SuppressWarnings("unchecked")
        public V get(Object key) {
            int position = position(key);
            return position < 0 ? null : (V) expand(values[slots[position]]);
        }

        @Override
        public Set<Entry<String, V>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public int size() {
                    return names.length;
                }

                @Override
                public Iterator<Entry<String, V>> iterator() {
                    return new Iterator<>() {
                        private int next;

                        @Override
                        public boolean hasNext() {
                            return next < names.length;
                        }

                        @Override
                        @SuppressWarnings("unchecked")
                        public Entry<String, V> next() {
                            if (next >= names.length) {
                                throw new NoSuchElementException();
                            }
                            int position = next++;
                            return new SimpleImmutableEntry<>(names[position], (V) expand(values[slots[position]]));
                        }
                    };
                }
            };
        }

        private int position(Object key) {
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(key)) {
                    return i;
                }
            }
            return -1;
        }

    }

}
