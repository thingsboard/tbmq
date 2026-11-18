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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ObjLongConsumer;

/**
 * How {@link RocksEntityStore} keeps one domain type: its stored type, its id, its index values, the types removed
 * with it and, for a paged type, its projection. The instances are the constants of {@link EntityDescriptors}.
 *
 * @param <T>  the domain type, stored as its JSON
 * @param <ID> {@link UUID} for a {@link StoredEntityType.IdKind#UUID} type, {@link String} for a
 *             {@link StoredEntityType.IdKind#STRING} one
 */
public final class EntityDescriptor<T, ID> {

    /**
     * Removing a document of the owning type removes every document of {@code child} whose {@code childIndex} value
     * is the removed id as text, in the same batch.
     */
    public record Cascade(EntityIndex childIndex, EntityDescriptor<?, ?> child) {
    }

    private final StoredEntityType type;
    private final Class<T> domainClass;
    private final Function<T, ID> id;
    private final BiConsumer<T, UUID> idAssigner;
    private final ObjLongConsumer<T> createdTimeSetter;
    private final Map<EntityIndex, Function<T, String>> indexes;
    private final List<Cascade> cascades;
    private final Projection<T> projection;

    private EntityDescriptor(Builder<T, ID> builder) {
        this.type = builder.type;
        this.domainClass = builder.domainClass;
        this.id = builder.id;
        this.idAssigner = builder.idAssigner;
        this.createdTimeSetter = builder.createdTimeSetter;
        this.indexes = Collections.unmodifiableMap(new EnumMap<>(builder.indexes));
        this.cascades = List.copyOf(builder.cascades);
        this.projection = builder.projection;
    }

    public static <T, ID> Builder<T, ID> builder(StoredEntityType type, Class<T> domainClass, Function<T, ID> id) {
        return new Builder<>(type, domainClass, id);
    }

    public StoredEntityType type() {
        return type;
    }

    public Class<T> domainClass() {
        return domainClass;
    }

    public Function<T, ID> id() {
        return id;
    }

    /**
     * Sets a new id on a document saved without one; {@code null} for a type whose id the caller always gives.
     */
    public BiConsumer<T, UUID> idAssigner() {
        return idAssigner;
    }

    /**
     * Sets the created time of a document saved without an id; {@code null} when {@link #idAssigner()} is.
     */
    public ObjLongConsumer<T> createdTimeSetter() {
        return createdTimeSetter;
    }

    /**
     * The value of each index of the type; a function that returns {@code null} leaves the document out of that
     * index.
     */
    public Map<EntityIndex, Function<T, String>> indexes() {
        return indexes;
    }

    public List<Cascade> cascades() {
        return cascades;
    }

    /**
     * What the type keeps in memory for paging; {@code null} for a type that is never paged.
     */
    public Projection<T> projection() {
        return projection;
    }

    /**
     * The id as it is written in the keys, see {@link EntityKeys}.
     */
    byte[] idBytes(ID entityId) {
        return switch (type.idKind()) {
            case UUID -> EntityKeys.uuidBytes((UUID) entityId);
            case STRING -> EntityKeys.stringBytes((String) entityId);
        };
    }

    public static final class Builder<T, ID> {

        private final StoredEntityType type;
        private final Class<T> domainClass;
        private final Function<T, ID> id;
        private BiConsumer<T, UUID> idAssigner;
        private ObjLongConsumer<T> createdTimeSetter;
        private final Map<EntityIndex, Function<T, String>> indexes = new EnumMap<>(EntityIndex.class);
        private final List<Cascade> cascades = new ArrayList<>();
        private Projection<T> projection;

        private Builder(StoredEntityType type, Class<T> domainClass, Function<T, ID> id) {
            this.type = type;
            this.domainClass = domainClass;
            this.id = id;
        }

        /**
         * A document saved without an id gets a random UUID and the current time as its created time, as the SQL
         * DAOs do.
         */
        public Builder<T, ID> assignedIds(BiConsumer<T, UUID> idAssigner, ObjLongConsumer<T> createdTimeSetter) {
            this.idAssigner = idAssigner;
            this.createdTimeSetter = createdTimeSetter;
            return this;
        }

        public Builder<T, ID> index(EntityIndex index, Function<T, String> value) {
            if (indexes.put(index, value) != null) {
                throw new IllegalArgumentException(type + " declares the index " + index + " twice");
            }
            return this;
        }

        public Builder<T, ID> cascade(EntityIndex childIndex, EntityDescriptor<?, ?> child) {
            if (!child.indexes().containsKey(childIndex)) {
                throw new IllegalArgumentException(type + " cascades through " + childIndex + ", which " + child.type() + " does not have");
            }
            cascades.add(new Cascade(childIndex, child));
            return this;
        }

        public Builder<T, ID> projection(Projection<T> projection) {
            this.projection = projection;
            return this;
        }

        public EntityDescriptor<T, ID> build() {
            boolean uuidIds = type.idKind() == StoredEntityType.IdKind.UUID;
            if (uuidIds != (idAssigner != null && createdTimeSetter != null)) {
                throw new IllegalArgumentException(type + ": a UUID type assigns ids and created times, a string type does not");
            }
            return new EntityDescriptor<>(this);
        }

    }

}
