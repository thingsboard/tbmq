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
package org.thingsboard.mqtt.broker.service.install;

import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.DependsOn;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderDao;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsDao;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.standalone.store.ManagementDataStep;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The standalone edition fills admin settings and auth providers in its management data initializer. A bean that reads
 * them while it is created must be created after it, or it sees an empty store.
 * <p>
 * The scan guard has blind spots: it sees only {@code @PostConstruct} methods and the fields and constructor
 * parameters declared on the class itself, not inherited fields, {@code InitializingBean}, {@code @EventListener},
 * {@code SmartLifecycle} or an indirect read through another service.
 */
class ManagementDataInitializerDependsOnTest {

    private static final String INITIALIZER_BEAN = "managementDataInitializer";

    private static final Set<Class<?>> MANAGEMENT_DATA_TYPES = Set.of(
            AdminSettingsService.class, AdminSettingsDao.class, MqttAuthProviderService.class, MqttAuthProviderDao.class);

    @ParameterizedTest
    @ValueSource(strings = {
            "org.thingsboard.mqtt.broker.service.mail.DefaultMailService",
            "org.thingsboard.mqtt.broker.service.auth.DefaultAuthorizationRoutingService",
            "org.thingsboard.mqtt.broker.service.auth.DefaultEnhancedAuthenticationService",
            "org.thingsboard.mqtt.broker.service.auth.providers.basic.BasicMqttClientAuthProvider",
            "org.thingsboard.mqtt.broker.service.auth.providers.ssl.SslMqttClientAuthProvider",
            "org.thingsboard.mqtt.broker.service.auth.providers.jwt.JwtMqttClientAuthProvider",
            "org.thingsboard.mqtt.broker.service.auth.providers.http.HttpMqttClientAuthProvider"
    })
    void givenABeanThatReadsManagementDataAtStartup_whenInspected_thenItDependsOnTheInitializer(String className) throws ClassNotFoundException {
        Class<?> beanClass = Class.forName(className);

        DependsOn dependsOn = beanClass.getAnnotation(DependsOn.class);

        assertThat(dependsOn).as("@DependsOn of %s", className).isNotNull();
        assertThat(dependsOn.value()).as("@DependsOn of %s", className).contains(INITIALIZER_BEAN);
    }

    @Test
    void givenEveryClassWithAPostConstructAndAManagementDataDependency_whenScanned_thenItDependsOnTheInitializer() {
        List<String> missing = scanClasses().stream()
                .filter(c -> !ManagementDataStep.class.isAssignableFrom(c) && !ManagementDataInitializer.class.isAssignableFrom(c))
                .filter(ManagementDataInitializerDependsOnTest::hasPostConstruct)
                .filter(ManagementDataInitializerDependsOnTest::usesManagementData)
                .filter(c -> !dependsOnInitializer(c))
                .map(Class::getName)
                .sorted()
                .toList();

        assertThat(missing)
                .as("classes with a @PostConstruct and an admin settings or auth provider dependency, without @DependsOn(\"%s\")", INITIALIZER_BEAN)
                .isEmpty();
    }

    @Test
    void givenTheScan_whenRun_thenItFindsTheKnownReaders() {
        List<String> found = scanClasses().stream()
                .filter(ManagementDataInitializerDependsOnTest::hasPostConstruct)
                .filter(ManagementDataInitializerDependsOnTest::usesManagementData)
                .map(Class::getSimpleName)
                .toList();

        assertThat(found).contains("DefaultMailService", "DefaultAuthorizationRoutingService", "BasicMqttClientAuthProvider");
    }

    /**
     * Every class of the application module's main classes, from the class files next to {@link ManagementDataInitializer}.
     */
    private static List<Class<?>> scanClasses() {
        try {
            Path root = Path.of(ManagementDataInitializer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            assertThat(root).as("the application classes directory").isDirectory();
            try (Stream<Path> files = Files.walk(root)) {
                return files.filter(path -> path.toString().endsWith(".class"))
                        .map(path -> root.relativize(path).toString())
                        .filter(name -> !name.equals("module-info.class") && !name.startsWith("META-INF"))
                        .map(name -> name.substring(0, name.length() - ".class".length()).replace(File.separatorChar, '.'))
                        .map(ManagementDataInitializerDependsOnTest::load)
                        .toList();
            }
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("Cannot scan the application classes", e);
        }
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className, false, ManagementDataInitializerDependsOnTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean hasPostConstruct(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            if (Arrays.stream(c.getDeclaredMethods()).anyMatch(ManagementDataInitializerDependsOnTest::isPostConstruct)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPostConstruct(Method method) {
        return method.isAnnotationPresent(PostConstruct.class);
    }

    /**
     * Only the fields and constructors the class declares itself: every controller inherits the services of
     * {@code BaseController}, and most of them do not read them at startup.
     */
    private static boolean usesManagementData(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).anyMatch(field -> MANAGEMENT_DATA_TYPES.contains(field.getType()))
                || Arrays.stream(type.getDeclaredConstructors())
                .anyMatch(constructor -> Arrays.stream(constructor.getParameterTypes()).anyMatch(MANAGEMENT_DATA_TYPES::contains));
    }

    private static boolean dependsOnInitializer(Class<?> type) {
        DependsOn dependsOn = type.getAnnotation(DependsOn.class);
        return dependsOn != null && Arrays.asList(dependsOn.value()).contains(INITIALIZER_BEAN);
    }

}
