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
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.SysAdminSettingType;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsDao;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractAdminSettingsContractTest extends AbstractContractTest {

    private final List<String> createdKeys = new ArrayList<>();

    @Autowired
    protected AdminSettingsService adminSettingsService;
    @Autowired
    protected AdminSettingsDao adminSettingsDao;

    @After
    public void removeCreatedSettings() {
        createdKeys.forEach(adminSettingsDao::removeByKey);
        createdKeys.clear();
    }

    @Test
    public void saveFindByKeyAndRemoveByKey() {
        AdminSettings saved = saveNew(newSettings(uniqueKey(), "{\"a\":1}"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedTime()).isPositive();
        assertThat(adminSettingsService.findAdminSettingsByKey(saved.getKey())).isEqualTo(saved);
        assertThat(adminSettingsService.findAdminSettingsById(saved.getId())).isEqualTo(saved);
        assertThat(adminSettingsDao.findByKey(saved.getKey())).isEqualTo(saved);

        adminSettingsService.deleteAdminSettingsByKey(saved.getKey());

        assertThat(adminSettingsDao.findByKey(saved.getKey())).isNull();
        assertThat(adminSettingsDao.findById(saved.getId())).isNull();
    }

    @Test
    public void theJsonValueIsReadBackAsSaved() {
        String json = "{\"text\":\"value\",\"number\":12,\"flag\":true,\"nested\":{\"list\":[1,\"two\",null]}}";
        AdminSettings saved = saveNew(newSettings(uniqueKey(), json));

        AdminSettings found = adminSettingsService.findAdminSettingsByKey(saved.getKey());

        assertThat(found.getJsonValue()).isEqualTo(JacksonUtil.toJsonNode(json));
    }

    @Test
    public void aSecondSettingWithTheSameKeyIsRefusedByTheService() {
        AdminSettings first = saveNew(newSettings(uniqueKey(), "{}"));

        assertThatThrownBy(() -> adminSettingsService.saveAdminSettings(newSettings(first.getKey(), "{}")))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Admin settings with such key is already created!");
    }

    @Test
    public void anUpdateKeepsTheIdAndTheCreatedTime() {
        AdminSettings saved = saveNew(newSettings(uniqueKey(), "{\"a\":1}"));

        assertThat(saved.getCreatedTime()).isPositive();
        AdminSettings update = new AdminSettings(saved.getId());
        update.setCreatedTime(saved.getCreatedTime());
        update.setKey(saved.getKey());
        update.setJsonValue(JacksonUtil.toJsonNode("{\"a\":2}"));
        AdminSettings updated = adminSettingsService.saveAdminSettings(update);

        assertThat(updated.getId()).isEqualTo(saved.getId());
        assertThat(updated.getCreatedTime()).isEqualTo(saved.getCreatedTime());
        assertThat(adminSettingsService.findAdminSettingsByKey(saved.getKey()).getCreatedTime()).isEqualTo(saved.getCreatedTime());
        assertThat(adminSettingsService.findAdminSettingsByKey(saved.getKey()).getJsonValue())
                .isEqualTo(JacksonUtil.toJsonNode("{\"a\":2}"));
        assertThat(adminSettingsDao.find().stream().filter(s -> s.getKey().equals(saved.getKey()))).hasSize(1);
    }

    @Test
    public void anUpdateOfANonExistentSettingIsRefused() {
        AdminSettings update = new AdminSettings(UUID.randomUUID());
        update.setKey(uniqueKey());
        update.setJsonValue(JacksonUtil.toJsonNode("{}"));

        assertThatThrownBy(() -> adminSettingsService.saveAdminSettings(update))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Unable to update non-existent Admin Settings!");
    }

    @Test
    public void theKeyCannotBeChangedByAnUpdate() {
        AdminSettings saved = saveNew(newSettings(uniqueKey(), "{}"));

        AdminSettings update = new AdminSettings(saved.getId());
        update.setKey(uniqueKey());
        update.setJsonValue(JacksonUtil.toJsonNode("{}"));

        assertThatThrownBy(() -> adminSettingsService.saveAdminSettings(update))
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Updating key of admin settings is prohibited!");
    }

    @Test
    public void theMailPasswordIsKeptWhenAnUpdateOmitsIt() {
        String mailKey = SysAdminSettingType.MAIL.getKey();
        AdminSettings original = adminSettingsDao.findByKey(mailKey);
        adminSettingsDao.removeByKey(mailKey);
        try {
            AdminSettings created = adminSettingsService.saveAdminSettings(newSettings(mailKey,
                    "{\"mailFrom\":\"a@localhost\",\"password\":\"secret\"}"));

            AdminSettings update = new AdminSettings(created.getId());
            update.setKey(mailKey);
            update.setJsonValue(JacksonUtil.toJsonNode("{\"mailFrom\":\"b@localhost\"}"));
            adminSettingsService.saveAdminSettings(update);

            AdminSettings found = adminSettingsService.findAdminSettingsByKey(mailKey);
            assertThat(found.getJsonValue().get("mailFrom").asText()).isEqualTo("b@localhost");
            assertThat(found.getJsonValue().get("password").asText()).isEqualTo("secret");
        } finally {
            adminSettingsDao.removeByKey(mailKey);
            if (original != null) {
                adminSettingsDao.save(original);
            }
        }
    }

    @Test
    public void removeByKeyOfAMissingKeyDoesNothing() {
        assertThatCode(() -> adminSettingsDao.removeByKey(uniqueKey())).doesNotThrowAnyException();
    }

    @Test
    public void removeByIdOfAMissingIdReportsTrue() {
        assertThat(adminSettingsDao.removeById(UUID.randomUUID())).isTrue();
    }

    @Test
    public void findByIdOfAMissingIdIsNull() {
        assertThat(adminSettingsDao.findById(UUID.randomUUID())).isNull();
    }

    @Test
    public void removeByIdRemovesTheSetting() {
        AdminSettings saved = saveNew(newSettings(uniqueKey(), "{}"));

        assertThat(adminSettingsDao.removeById(saved.getId())).isTrue();

        assertThat(adminSettingsDao.findById(saved.getId())).isNull();
        assertThat(adminSettingsDao.findByKey(saved.getKey())).isNull();
    }

    @Test
    public void findListsEverySetting() {
        AdminSettings first = saveNew(newSettings(uniqueKey(), "{}"));
        AdminSettings second = saveNew(newSettings(uniqueKey(), "{}"));

        assertThat(adminSettingsDao.find()).contains(first, second);
    }

    @Test
    public void findByIdAsyncReturnsTheSetting() throws Exception {
        AdminSettings saved = saveNew(newSettings(uniqueKey(), "{\"a\":1}"));

        assertThat(adminSettingsDao.findByIdAsync(saved.getId()).get()).isEqualTo(saved);
    }

    protected AdminSettings saveNew(AdminSettings adminSettings) {
        AdminSettings saved = adminSettingsService.saveAdminSettings(adminSettings);
        createdKeys.add(saved.getKey());
        return saved;
    }

    protected static AdminSettings newSettings(String key, String json) {
        AdminSettings adminSettings = new AdminSettings();
        adminSettings.setKey(key);
        adminSettings.setJsonValue(JacksonUtil.toJsonNode(json));
        return adminSettings;
    }

    protected static String uniqueKey() {
        return "contract-" + UUID.randomUUID();
    }

}
