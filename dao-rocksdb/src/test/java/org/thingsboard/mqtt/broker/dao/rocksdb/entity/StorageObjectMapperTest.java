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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class StorageObjectMapperTest {

    private static final String GOLDEN_DIRECTORY = "/storage-golden";
    private static final ObjectMapper TREES = new ObjectMapper();

    static Stream<org.junit.jupiter.params.provider.Arguments> everyStoredDomainType() {
        return StorageFixtures.everyStoredDomainType().entrySet().stream()
                .map(entry -> arguments(entry.getKey(), entry.getValue()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyStoredDomainType")
    void everyFieldRoundTrips(String name, Object filled) {
        Object back = StorageObjectMapper.read(StorageObjectMapper.write(filled), filled.getClass());

        assertThat(back).usingRecursiveComparison().isEqualTo(filled);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyStoredDomainType")
    void theFixturesFillEveryDeclaredField(String name, Object filled) throws IllegalAccessException {
        assertFilled(filled, name, new HashSet<>());
    }

    @Test
    void everyEntityTypeHasADescriptorAndAFixture() {
        assertThat(EntityDescriptors.ALL).extracting(EntityDescriptor::type).containsExactly(StoredEntityType.values());
        Set<Class<?>> fixtureClasses = new HashSet<>();
        StorageFixtures.everyStoredDomainType().values().forEach(filled -> fixtureClasses.add(filled.getClass()));
        for (EntityDescriptor<?, ?> d : EntityDescriptors.ALL) {
            assertThat(fixtureClasses).as("a fixture of %s", d.type()).contains(d.domainClass());
        }
    }

    @Test
    void theFixturesHoldEverySubtypeOfTheirPolymorphicTypes() throws IllegalAccessException {
        Set<Class<?>> reached = new HashSet<>();
        for (Map.Entry<String, Object> fixture : StorageFixtures.everyStoredDomainType().entrySet()) {
            assertFilled(fixture.getValue(), fixture.getKey(), reached);
        }
        Set<Class<?>> declared = new HashSet<>();
        for (Class<?> type : reached) {
            for (Class<?> supertype : supertypes(type)) {
                JsonSubTypes subTypes = supertype.getAnnotation(JsonSubTypes.class);
                if (subTypes != null) {
                    for (JsonSubTypes.Type subType : subTypes.value()) {
                        declared.add(subType.value());
                    }
                }
            }
        }

        assertThat(declared).isNotEmpty();
        assertThat(reached).as("classes the fixtures hold").containsAll(declared);
    }

    @Test
    void anUnknownPropertyIsIgnored() {
        byte[] stored = withFormatByte("{\"email\":\"a@b\",\"addedLater\":1,\"alsoAddedLater\":{\"x\":[1,2]}}");

        assertThat(StorageObjectMapper.read(stored, User.class).getEmail()).isEqualTo("a@b");
    }

    @Test
    void aMissingPropertyTakesItsDefault() {
        User user = StorageObjectMapper.read(withFormatByte("{\"email\":\"a@b\"}"), User.class);

        assertThat(user.getFirstName()).isNull();
        assertThat(user.getCreatedTime()).isZero();
    }

    @Test
    void anUnreadableValueNamesTheType() {
        assertThatThrownBy(() -> StorageObjectMapper.read(new byte[]{1, '{', 'x'}, User.class))
                .isInstanceOf(StoreException.class).hasMessageContaining("User");
    }

    @Test
    void anUnknownFormatByteNamesTheTypeAndTheByte() {
        assertThatThrownBy(() -> StorageObjectMapper.read(new byte[]{2, '{', '}'}, User.class))
                .isInstanceOf(StoreException.class).hasMessageContaining("User").hasMessageContaining("2");
    }

    @Test
    void aNullValueNamesTheType() {
        assertThatThrownBy(() -> StorageObjectMapper.read(null, User.class))
                .isInstanceOf(StoreException.class).hasMessageContaining("User");
    }

    @Test
    void aWriteOnlyAndAReadOnlyPropertyAreStoredAndReadBack() throws Exception {
        AccessAnnotated original = new AccessAnnotated();
        original.setWriteOnly("secret");
        original.setReadOnly("derived");

        AccessAnnotated back = StorageObjectMapper.read(StorageObjectMapper.write(original), AccessAnnotated.class);

        assertThat(back.getWriteOnly()).isEqualTo("secret");
        assertThat(back.getReadOnly()).isEqualTo("derived");
        // the REST mapper drops both, which is what the storage mapper must not do
        AccessAnnotated restBack = TREES.readValue(TREES.writeValueAsBytes(original), AccessAnnotated.class);
        assertThat(restBack.getWriteOnly()).isNull();
        assertThat(restBack.getReadOnly()).isNull();
    }

    @Test
    void anEmptyValueNamesTheType() {
        assertThatThrownBy(() -> StorageObjectMapper.read(new byte[0], User.class))
                .isInstanceOf(StoreException.class).hasMessageContaining("User");
    }

    @Test
    void aWrongShapeNamesTheType() {
        assertThatThrownBy(() -> StorageObjectMapper.read(withFormatByte("[1,2]"), User.class))
                .isInstanceOf(StoreException.class).hasMessageContaining("User");
    }

    @Test
    void theStoredFormatIsPinned() {
        AdminSettings settings = StorageFixtures.adminSettings();

        byte[] stored = StorageObjectMapper.write(settings);

        assertThat(stored[0]).isEqualTo((byte) 1);
        assertThat(new String(stored, 1, stored.length - 1, StandardCharsets.UTF_8)).isEqualTo(PINNED_ADMIN_SETTINGS_JSON);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyStoredDomainType")
    void theGoldenFileStillReads(String name, Object filled) throws IOException {
        byte[] golden = goldenBytes(name);

        Object back = StorageObjectMapper.read(withFormatByte(golden), filled.getClass());
        assertThat(back).usingRecursiveComparison().isEqualTo(filled);

        byte[] written = StorageObjectMapper.write(filled);
        JsonNode writtenTree = TREES.readTree(written, 1, written.length - 1);
        assertThat(writtenTree).isEqualTo(TREES.readTree(golden));
    }

    @Test
    void everyStoredTypeHasAGoldenFile() throws URISyntaxException {
        File[] files = new File(StorageObjectMapperTest.class.getResource(GOLDEN_DIRECTORY).toURI()).listFiles();
        Set<String> onDisk = new TreeSet<>();
        for (File file : files) {
            onDisk.add(file.getName());
        }
        Set<String> expected = new TreeSet<>();
        StorageFixtures.everyStoredDomainType().keySet().forEach(name -> expected.add(name + ".json"));

        assertThat(onDisk).isEqualTo(expected);
    }

    private static final String PINNED_ADMIN_SETTINGS_JSON = 
            "{\"id\":\"12345678-9abc-def0-0000-000000000003\",\"createdTime\":1700000000123,\"key\":\"mail\",\"jsonValue\":{\"smtpHost\":\"localhost\",\"smtpPort\":25,\"password\":\"mail-secret\",\"tls\":true}}";

    private static byte[] goldenBytes(String name) throws IOException {
        try (InputStream in = StorageObjectMapperTest.class.getResourceAsStream(GOLDEN_DIRECTORY + "/" + name + ".json")) {
            assertThat(in).as("golden file %s.json", name).isNotNull();
            return in.readAllBytes();
        }
    }

    private static byte[] withFormatByte(String json) {
        return withFormatByte(json.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] withFormatByte(byte[] json) {
        byte[] stored = new byte[json.length + 1];
        stored[0] = 1;
        System.arraycopy(json, 0, stored, 1, json.length);
        return stored;
    }

    /**
     * Every non-static field of the object and of its superclasses is neither null nor a default, and so is every
     * field of the domain objects, collections and maps it holds. Adds the class of each domain object to
     * {@code reached}.
     */
    private static void assertFilled(Object object, String path, Set<Class<?>> reached) throws IllegalAccessException {
        reached.add(object.getClass());
        for (Class<?> type = object.getClass(); type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(object);
                String fieldPath = path + "." + type.getSimpleName() + "." + field.getName();
                assertThat(isFilled(value)).as("%s is %s", fieldPath, value).isTrue();
                descend(value, fieldPath, reached);
            }
        }
    }

    private static void descend(Object value, String path, Set<Class<?>> reached) throws IllegalAccessException {
        if (value instanceof Collection<?> collection) {
            for (Object element : collection) {
                descend(element, path + "[]", reached);
            }
        } else if (value instanceof Map<?, ?> map) {
            for (Object element : map.values()) {
                descend(element, path + "{}", reached);
            }
        } else if (isDomainObject(value)) {
            assertFilled(value, path, reached);
        }
    }

    /**
     * The type, its superclasses and every interface any of them implements.
     */
    private static List<Class<?>> supertypes(Class<?> type) {
        List<Class<?>> all = new ArrayList<>();
        List<Class<?>> pending = new ArrayList<>(List.of(type));
        while (!pending.isEmpty()) {
            Class<?> next = pending.removeLast();
            if (next == null || next == Object.class || all.contains(next)) {
                continue;
            }
            all.add(next);
            pending.add(next.getSuperclass());
            pending.addAll(List.of(next.getInterfaces()));
        }
        return all;
    }

    private static boolean isDomainObject(Object value) {
        return value != null && !value.getClass().isEnum() && value.getClass().getName().startsWith("org.thingsboard.");
    }

    private static boolean isFilled(Object value) {
        return switch (value) {
            case null -> false;
            case Boolean b -> b;
            case Number n -> n.doubleValue() != 0;
            case CharSequence s -> !s.isEmpty();
            case Collection<?> c -> !c.isEmpty();
            case Map<?, ?> m -> !m.isEmpty();
            case JsonNode n -> !n.isNull() && !n.isEmpty();
            default -> !value.getClass().isArray() || java.lang.reflect.Array.getLength(value) > 0;
        };
    }

    public static class AccessAnnotated {

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private String writeOnly;
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private String readOnly;

        public String getWriteOnly() {
            return writeOnly;
        }

        public void setWriteOnly(String writeOnly) {
            this.writeOnly = writeOnly;
        }

        public String getReadOnly() {
            return readOnly;
        }

        public void setReadOnly(String readOnly) {
            this.readOnly = readOnly;
        }

    }

}
