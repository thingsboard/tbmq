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
package org.thingsboard.mqtt.broker.dao.contract;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.basic.BasicMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderDao;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractMqttAuthProviderContractTest extends AbstractContractTest {

    private MqttAuthProvider basicAuthProvider;

    @Autowired
    protected MqttAuthProviderService mqttAuthProviderService;
    @Autowired
    protected MqttAuthProviderDao mqttAuthProviderDao;

    @Before
    public void setUp() {
        Optional<MqttAuthProvider> basicProviderOpt = mqttAuthProviderService.getAuthProviderByType(MqttAuthProviderType.MQTT_BASIC);
        assertThat(basicProviderOpt).isPresent();
        basicAuthProvider = basicProviderOpt.get();
    }

    @After
    public void tearDown() {
        if (basicAuthProvider == null) {
            return;
        }
        basicAuthProvider.setEnabled(false);
        basicAuthProvider.setType(MqttAuthProviderType.MQTT_BASIC);
        basicAuthProvider.setConfiguration(new BasicMqttAuthProviderConfiguration());
        mqttAuthProviderService.saveAuthProvider(basicAuthProvider);
    }

    @Test
    public void testUpdateMqttAuthProvider() {
        assertThat(basicAuthProvider).isNotNull();
        assertThat(basicAuthProvider.isEnabled()).isFalse();

        basicAuthProvider.setEnabled(true);
        assertThatCode(() -> {
            MqttAuthProvider updatedBasicAuthProvider = mqttAuthProviderService.saveAuthProvider(basicAuthProvider);
            assertThat(updatedBasicAuthProvider).isNotNull();
            assertThat(updatedBasicAuthProvider.getId()).isEqualTo(basicAuthProvider.getId());
            assertThat(updatedBasicAuthProvider.isEnabled()).isTrue();
        }).doesNotThrowAnyException();

        mqttAuthProviderService.disableAuthProvider(basicAuthProvider.getId());

        Optional<MqttAuthProvider> disabledBasicProviderOpt = mqttAuthProviderService.getAuthProviderById(basicAuthProvider.getId());
        assertThat(disabledBasicProviderOpt).isPresent();
        assertThat(disabledBasicProviderOpt.get().getId()).isEqualTo(basicAuthProvider.getId());
        assertThat(disabledBasicProviderOpt.get().isEnabled()).isFalse();
    }

    @Test
    public void testUpdateNonExistingMqttAuthProvider() {
        MqttAuthProvider mqttAuthProvider = getNewBasicMqttAuthProvider();
        mqttAuthProvider.setId(UUID.fromString("e993d875-2e5b-48f2-ba6b-63074800a3ce"));
        assertThatThrownBy(() -> mqttAuthProviderService.saveAuthProvider(mqttAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Unable to update non-existent MQTT auth provider!");
    }

    @Test
    public void testUpdateMqttAuthProviderType() {
        assertThat(basicAuthProvider).isNotNull();
        assertThat(basicAuthProvider.getType()).isEqualTo(MqttAuthProviderType.MQTT_BASIC);

        basicAuthProvider.setType(MqttAuthProviderType.X_509);

        assertThatThrownBy(() -> mqttAuthProviderService.saveAuthProvider(basicAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("MQTT auth provider type can't be changed!");
    }

    @Test
    public void testSaveMqttAuthProviderWithNullType() {
        MqttAuthProvider mqttAuthProvider = getNewBasicMqttAuthProvider(null, new BasicMqttAuthProviderConfiguration());
        assertThatThrownBy(() -> mqttAuthProviderService.saveAuthProvider(mqttAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("MQTT auth provider type should be specified!");
    }

    @Test
    public void testSaveMqttAuthProviderWithNullConfiguration() {
        MqttAuthProvider mqttAuthProvider = getNewBasicMqttAuthProvider(MqttAuthProviderType.MQTT_BASIC, null);
        assertThatThrownBy(() -> mqttAuthProviderService.saveAuthProvider(mqttAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("MQTT auth provider configuration should be specified!");
    }

    @Test
    public void testSaveMqttAuthProviderWithExistingType() {
        MqttAuthProvider anotherBasicMqttAuthProvider = getNewBasicMqttAuthProvider();
        assertThatThrownBy(() -> mqttAuthProviderService.saveAuthProvider(anotherBasicMqttAuthProvider))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("MQTT auth provider with such type already registered!");
    }

    @Test
    public void testFindMqttAuthProviderById() {
        assertThat(basicAuthProvider).isNotNull();
        assertThat(basicAuthProvider.getId()).isNotNull();

        Optional<MqttAuthProvider> foundAuthProvider = mqttAuthProviderService.getAuthProviderById(basicAuthProvider.getId());
        assertThat(foundAuthProvider.isPresent()).isTrue();
        assertThat(foundAuthProvider.get()).isEqualTo(basicAuthProvider);
    }

    @Test
    public void testFindMqttAuthProviderByType() {
        assertThat(basicAuthProvider).isNotNull();
        assertThat(basicAuthProvider.getId()).isNotNull();

        Optional<MqttAuthProvider> foundAuthProvider = mqttAuthProviderService.getAuthProviderByType(MqttAuthProviderType.MQTT_BASIC);
        assertThat(foundAuthProvider.isPresent()).isTrue();
        assertThat(foundAuthProvider.get()).isEqualTo(basicAuthProvider);
    }


    @Test
    public void testDeleteNonExistingMqttAuthProvider() {
        assertThat(mqttAuthProviderService.deleteAuthProvider(UUID.fromString("9b519fe5-4712-4926-9e24-debf24a7c660"))).isFalse();
    }

    @Test
    public void testEnableMqttAuthProvider() {
        assertThat(basicAuthProvider).isNotNull();
        assertThat(basicAuthProvider.getId()).isNotNull();
        assertThat(basicAuthProvider.isEnabled()).isFalse();

        assertThat(mqttAuthProviderService.enableAuthProvider(basicAuthProvider.getId())).isPresent();

        Optional<MqttAuthProvider> authProviderById = mqttAuthProviderService.getAuthProviderById(basicAuthProvider.getId());
        assertThat(authProviderById.isPresent()).isTrue();
        assertThat(authProviderById.get().isEnabled()).isTrue();
    }

    @Test
    public void testEnableAlreadyEnabledMqttAuthProvider() {
        assertThat(basicAuthProvider).isNotNull();
        assertThat(basicAuthProvider.getId()).isNotNull();
        assertThat(basicAuthProvider.isEnabled()).isFalse();

        basicAuthProvider.setEnabled(true);

        // save enabled
        MqttAuthProvider savedAuthProvider = mqttAuthProviderService.saveAuthProvider(basicAuthProvider);

        assertThat(savedAuthProvider).isNotNull();
        assertThat(savedAuthProvider.getType()).isEqualTo(MqttAuthProviderType.MQTT_BASIC);
        assertThat(savedAuthProvider.getConfiguration()).isNotNull();
        assertThat(savedAuthProvider.isEnabled()).isTrue();

        assertThat(mqttAuthProviderService.enableAuthProvider(savedAuthProvider.getId())).isEmpty();

        Optional<MqttAuthProvider> authProviderById = mqttAuthProviderService.getAuthProviderById(savedAuthProvider.getId());
        assertThat(authProviderById.isPresent()).isTrue();
        assertThat(authProviderById.get().isEnabled()).isTrue();
    }


    @Test
    public void testEnableNonExistingMqttAuthProvider() {
        assertThatThrownBy(() -> mqttAuthProviderService.enableAuthProvider(UUID.fromString("ab196e2f-e6bf-4138-8f4e-95de7748fc0a")))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Unable to enable non-existent MQTT auth provider!");
    }

    @Test
    public void testDisableMqttAuthProvider() {
        assertThat(basicAuthProvider).isNotNull();
        assertThat(basicAuthProvider.getId()).isNotNull();
        assertThat(basicAuthProvider.isEnabled()).isFalse();

        basicAuthProvider.setEnabled(true);

        // save enabled
        MqttAuthProvider savedAuthProvider = mqttAuthProviderService.saveAuthProvider(basicAuthProvider);

        assertThat(savedAuthProvider).isNotNull();
        assertThat(savedAuthProvider.getType()).isEqualTo(MqttAuthProviderType.MQTT_BASIC);
        assertThat(savedAuthProvider.getConfiguration()).isNotNull();
        assertThat(savedAuthProvider.isEnabled()).isTrue();

        assertThat(mqttAuthProviderService.disableAuthProvider(savedAuthProvider.getId())).isPresent();

        Optional<MqttAuthProvider> authProviderById = mqttAuthProviderService.getAuthProviderById(savedAuthProvider.getId());
        assertThat(authProviderById.isPresent()).isTrue();
        assertThat(authProviderById.get().isEnabled()).isFalse();
    }

    @Test
    public void testDisableAlreadyDisabledMqttAuthProvider() {
        assertThat(basicAuthProvider).isNotNull();
        assertThat(basicAuthProvider.getId()).isNotNull();
        assertThat(basicAuthProvider.isEnabled()).isFalse();

        assertThat(mqttAuthProviderService.disableAuthProvider(basicAuthProvider.getId())).isEmpty();

        Optional<MqttAuthProvider> authProviderById = mqttAuthProviderService.getAuthProviderById(basicAuthProvider.getId());
        assertThat(authProviderById.isPresent()).isTrue();
        assertThat(authProviderById.get().isEnabled()).isFalse();
    }

    @Test
    public void testDisableNonExistingMqttAuthProvider() {
        assertThatThrownBy(() -> mqttAuthProviderService.disableAuthProvider(UUID.fromString("6e2fb006-d5ad-4539-ad6a-203abff9521b")))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Unable to disable non-existent MQTT auth provider!");
    }


    @Test
    public void enableAndDisableReportWhetherTheStateChanged() {
        assertThat(basicAuthProvider.isEnabled()).isFalse();

        assertThat(mqttAuthProviderService.enableAuthProvider(basicAuthProvider.getId())).contains(MqttAuthProviderType.MQTT_BASIC);
        assertThat(mqttAuthProviderService.enableAuthProvider(basicAuthProvider.getId())).isEmpty();
        assertThat(mqttAuthProviderService.disableAuthProvider(basicAuthProvider.getId())).contains(MqttAuthProviderType.MQTT_BASIC);
        assertThat(mqttAuthProviderService.disableAuthProvider(basicAuthProvider.getId())).isEmpty();
    }

    @Test
    public void daoEnableByIdOfAMissingIdIsFalse() {
        assertThat(mqttAuthProviderDao.enableById(UUID.randomUUID())).isFalse();
        assertThat(mqttAuthProviderDao.disableById(UUID.randomUUID())).isFalse();
    }

    @Test
    public void daoEnableAndDisableByIdAreTrueForAnExistingProviderInAnyState() {
        UUID id = basicAuthProvider.getId();

        assertThat(mqttAuthProviderDao.enableById(id)).isTrue();
        assertThat(mqttAuthProviderDao.findById(id).isEnabled()).isTrue();
        assertThat(mqttAuthProviderDao.enableById(id)).isTrue();
        assertThat(mqttAuthProviderDao.findById(id).isEnabled()).isTrue();

        assertThat(mqttAuthProviderDao.disableById(id)).isTrue();
        assertThat(mqttAuthProviderDao.findById(id).isEnabled()).isFalse();
        assertThat(mqttAuthProviderDao.disableById(id)).isTrue();
        assertThat(mqttAuthProviderDao.findById(id).isEnabled()).isFalse();
    }

    @Test
    public void daoEnableByIdChangesOnlyTheEnabledFlag() {
        MqttAuthProvider before = mqttAuthProviderDao.findById(basicAuthProvider.getId());

        mqttAuthProviderDao.enableById(before.getId());

        MqttAuthProvider after = mqttAuthProviderDao.findById(before.getId());
        before.setEnabled(true);
        assertThat(after).isEqualTo(before);
    }

    @Test
    public void daoRemoveByIdOfAMissingIdReportsTrue() {
        assertThat(mqttAuthProviderDao.removeById(UUID.randomUUID())).isTrue();
    }

    @Test
    public void daoFindByIdOfAMissingIdIsNullAndFindByTypeFindsTheProvider() {
        assertThat(mqttAuthProviderDao.findById(UUID.randomUUID())).isNull();
        assertThat(mqttAuthProviderDao.findByType(MqttAuthProviderType.MQTT_BASIC)).isEqualTo(basicAuthProvider);
    }

    @Test
    public void daoFindListsEveryProvider() {
        List<MqttAuthProvider> all = mqttAuthProviderDao.find();

        assertThat(all).contains(basicAuthProvider);
        assertThat(all).extracting(MqttAuthProvider::getType).doesNotHaveDuplicates();
    }

    @Test
    public void findAllEnabledFiltersAndSearchesByType() {
        PageData<MqttAuthProvider> none = mqttAuthProviderDao.findAllEnabled(new PageLink(100));
        assertThat(none.getData()).noneMatch(p -> p.getType() == MqttAuthProviderType.MQTT_BASIC);
        assertThat(none.getData()).allMatch(MqttAuthProvider::isEnabled);

        mqttAuthProviderDao.enableById(basicAuthProvider.getId());

        PageData<MqttAuthProvider> enabled = mqttAuthProviderDao.findAllEnabled(new PageLink(100));
        assertThat(enabled.getData()).allMatch(MqttAuthProvider::isEnabled);
        assertThat(enabled.getData()).extracting(MqttAuthProvider::getType).contains(MqttAuthProviderType.MQTT_BASIC);
        assertThat(enabled.getTotalElements()).isEqualTo(enabled.getData().size());

        PageData<MqttAuthProvider> searched = mqttAuthProviderDao.findAllEnabled(new PageLink(100, 0, "BaSiC"));
        assertThat(searched.getData()).extracting(MqttAuthProvider::getType).containsExactly(MqttAuthProviderType.MQTT_BASIC);
        assertThat(searched.getTotalElements()).isEqualTo(1);

        PageData<MqttAuthProvider> unmatched = mqttAuthProviderDao.findAllEnabled(new PageLink(100, 0, "no-such-type"));
        assertThat(unmatched.getData()).isEmpty();
        assertThat(unmatched.getTotalElements()).isZero();
    }

    @Test
    public void findAllSearchesByTypeAndSortsByIt() {
        PageData<MqttAuthProvider> searched = mqttAuthProviderDao.findAll(new PageLink(100, 0, "basic"));
        assertThat(searched.getData()).extracting(MqttAuthProvider::getType).containsExactly(MqttAuthProviderType.MQTT_BASIC);

        List<MqttAuthProviderType> ascending = types(mqttAuthProviderDao.findAll(
                new PageLink(100, 0, null, new SortOrder("type", SortOrder.Direction.ASC))));
        assertThat(ascending).isSortedAccordingTo(Comparator.comparing(Enum::name));
        List<MqttAuthProviderType> descending = types(mqttAuthProviderDao.findAll(
                new PageLink(100, 0, null, new SortOrder("type", SortOrder.Direction.DESC))));
        assertThat(descending).isSortedAccordingTo(Comparator.comparing(MqttAuthProviderType::name).reversed());
    }

    @Test
    public void anUnknownSortPropertyIsRejectedAsAnInvalidApiUsage() {
        PageLink unknownSort = new PageLink(10, 0, null, new SortOrder("noSuchProperty", SortOrder.Direction.ASC));

        assertThatThrownBy(() -> mqttAuthProviderDao.findAll(unknownSort))
                .isExactlyInstanceOf(InvalidDataAccessApiUsageException.class);
        assertThatThrownBy(() -> mqttAuthProviderDao.findAllEnabled(unknownSort))
                .isExactlyInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    @Test
    public void findAllPagesTheProviders() {
        PageData<MqttAuthProvider> all = mqttAuthProviderDao.findAll(new PageLink(100));
        int total = (int) all.getTotalElements();
        assertThat(total).isGreaterThanOrEqualTo(1);

        PageData<MqttAuthProvider> first = mqttAuthProviderDao.findAll(new PageLink(1, 0));
        assertThat(first.getData()).hasSize(1);
        assertThat(first.getTotalElements()).isEqualTo(total);
        assertThat(first.getTotalPages()).isEqualTo(total);
        assertThat(first.hasNext()).isEqualTo(total > 1);
    }

    @Test
    public void everyConfigurationSubtypeRoundTrips() {
        for (MqttAuthProviderType type : MqttAuthProviderType.values()) {
            MqttAuthProvider existing = mqttAuthProviderDao.findByType(type);
            MqttAuthProvider provider = MqttAuthProvider.defaultAuthProvider(type, true);
            if (existing != null) {
                provider.setId(existing.getId());
                provider.setCreatedTime(existing.getCreatedTime());
            }
            try {
                MqttAuthProvider saved = mqttAuthProviderService.saveAuthProvider(provider);

                MqttAuthProvider found = mqttAuthProviderService.getAuthProviderById(saved.getId()).orElseThrow();
                // compared as JSON: some configuration classes (the HTTP credentials) have no equals
                assertThat(JacksonUtil.valueToTree(found)).as(type.name()).isEqualTo(JacksonUtil.valueToTree(saved));
                assertThat(JacksonUtil.valueToTree(found.getConfiguration())).as(type.name())
                        .isEqualTo(JacksonUtil.valueToTree(provider.getConfiguration()));
                assertThat(found.getConfiguration().getClass()).as(type.name()).isEqualTo(provider.getConfiguration().getClass());
                assertThat(found.getAdditionalInfo()).as(type.name()).isEqualTo(provider.getAdditionalInfo());
                assertThat(found.isEnabled()).as(type.name()).isTrue();
                assertThat(mqttAuthProviderService.getAuthProviderByType(type)).as(type.name()).isPresent();
            } finally {
                if (existing != null) {
                    mqttAuthProviderDao.save(existing);
                } else {
                    MqttAuthProvider created = mqttAuthProviderDao.findByType(type);
                    if (created != null) {
                        mqttAuthProviderDao.removeById(created.getId());
                    }
                }
            }
        }
    }

    private static List<MqttAuthProviderType> types(PageData<MqttAuthProvider> page) {
        return page.getData().stream().map(MqttAuthProvider::getType).toList();
    }

    private MqttAuthProvider getNewBasicMqttAuthProvider() {
        return getNewBasicMqttAuthProvider(MqttAuthProviderType.MQTT_BASIC, new BasicMqttAuthProviderConfiguration());
    }

    private MqttAuthProvider getNewBasicMqttAuthProvider(MqttAuthProviderType type, MqttAuthProviderConfiguration configuration) {
        var mqttAuthProvider = new MqttAuthProvider();
        mqttAuthProvider.setType(type);
        mqttAuthProvider.setConfiguration(configuration);
        return mqttAuthProvider;
    }

}
