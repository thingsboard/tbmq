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
package org.thingsboard.mqtt.broker.testsupport.lite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.SysAdminSettingType;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.security.Authority;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderDao;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsDao;
import org.thingsboard.mqtt.broker.dao.store.StoreSeedState;
import org.thingsboard.mqtt.broker.dao.user.UserCredentialsDao;
import org.thingsboard.mqtt.broker.dao.user.UserDao;
import org.thingsboard.mqtt.broker.dao.user.UserServiceImpl;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionDao;
import org.thingsboard.mqtt.broker.standalone.store.ManagementDataStep;

import java.util.List;
import java.util.UUID;

/**
 * Gives a test context the management data of {@code sql/system-data.sql} (module {@code dao-sql}, test resources, which
 * the golden file {@code sql/system-data-rows.json} next to it pins to what the SQL file inserts):
 * the system administrator and its credentials, the three settings and the five auth providers, all disabled. The rows
 * are written through the DAO beans, so each lands in whichever backend its DAO uses; a row that its DAO already finds,
 * as after a restart over the same store, is left as it is.
 * <p>
 * The constants below mirror that SQL file: the ids, the created times, the password hash and the JSON values.
 * {@code SystemDataTestStepMirrorTest} compares what the step saves with that golden file.
 * <p>
 * The step runs after the install seed. When this start seeded the store ({@link StoreSeedState#seededInThisStart()}),
 * the step first removes the seeded rows that the file does not hold, or holds with other values: the sysadmin's default
 * WebSocket connection, the system WebSocket credentials, the five admin settings and the five auth providers; the
 * insert pass then writes the file's settings and providers. The seeded sysadmin is kept: it has the file's email and
 * password, under an id of its own, and the step clears its password change mark. On a start over a store seeded before, only the insert pass runs.
 * <p>
 * With {@code lite.test.system-data.enabled=false} the step does nothing, for the tests that start from the real
 * defaults. The broker of the kill -9 test runs without the test classpath, so {@code LiteBrokerProcess} copies this
 * class (and any nested class) next to the launcher, where the component scan of the broker finds it.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SystemDataTestStep implements ManagementDataStep {

    // sql/system-data.sql
    private static final long SYSTEM_CREATED_TIME = 1592576748000L;
    private static final long AUTH_PROVIDER_CREATED_TIME = 1748975222000L;

    private static final UUID SYSADMIN_ID = UUID.fromString("5a797660-4612-11e7-a919-92ebcb67fe33");
    private static final String SYSADMIN_EMAIL = "sysadmin@thingsboard.org";
    private static final UUID SYSADMIN_CREDENTIALS_ID = UUID.fromString("61441950-4612-11e7-a919-92ebcb67fe33");
    private static final String SYSADMIN_PASSWORD_HASH = "$2a$10$5JTB8/hxWc9WAy62nCGSxeefl3KWmipA9nFpVdDa0/xfIseeBB4Bu";

    private static final UUID GENERAL_SETTINGS_ID = UUID.fromString("6a2266e4-4612-11e7-a919-92ebcb67fe33");
    private static final String GENERAL_SETTINGS_KEY = "general";
    private static final String GENERAL_SETTINGS_JSON = """
            {
              "baseUrl": "http://localhost:8083"
            }""";

    private static final UUID MAIL_SETTINGS_ID = UUID.fromString("6eaaefa6-4612-11e7-a919-92ebcb67fe33");
    private static final String MAIL_SETTINGS_KEY = "mail";
    private static final String MAIL_SETTINGS_JSON = """
            {
              "mailFrom": "Thingsboard <sysadmin@localhost.localdomain>",
              "smtpProtocol": "smtp",
              "smtpHost": "localhost",
              "smtpPort": "25",
              "timeout": "10000",
              "enableTls": false,
              "tlsVersion": "TLSv1.2",
              "username": "",
              "password": ""
            }""";

    private static final UUID MQTT_AUTHORIZATION_SETTINGS_ID = UUID.fromString("7ca448f2-780e-4b38-ac15-c064a8f20bb5");
    private static final String MQTT_AUTHORIZATION_SETTINGS_KEY = "mqttAuthorization";
    private static final String MQTT_AUTHORIZATION_SETTINGS_JSON = "{\"priorities\":[\"MQTT_BASIC\",\"X_509\",\"JWT\"]}";

    private static final UUID BASIC_PROVIDER_ID = UUID.fromString("bc0a90bf-de56-4953-92ca-7ef7b159c2fd");
    private static final String BASIC_PROVIDER_JSON = "{\"type\": \"MQTT_BASIC\"}";

    private static final UUID X509_PROVIDER_ID = UUID.fromString("e5b71665-0a22-4398-8f93-5c7c3c145a7d");
    private static final String X509_PROVIDER_JSON = "{\"type\": \"X_509\", \"skipValidityCheckForClientCert\": false}";

    private static final UUID JWT_PROVIDER_ID = UUID.fromString("5590d101-628f-414c-9960-5966a9c42c31");
    private static final String JWT_PROVIDER_JSON = """
            {
              "type": "JWT",
              "authClaims": {},
              "clientTypeClaims": {},
              "authRules": {},
              "defaultClientType": "DEVICE",
              "jwtVerifierConfiguration": {
                "jwtVerifierType": "ALGORITHM_BASED",
                "jwtSignAlgorithmConfiguration": {
                  "secret": "please-change-this-32-char-jwt-secret",
                  "algorithm": "HMAC_BASED"
                }
              }
            }""";

    private static final UUID SCRAM_PROVIDER_ID = UUID.fromString("3a7ffd44-eb9c-4334-951d-a8bb3cb4804f");
    private static final String SCRAM_PROVIDER_JSON = "{\"type\": \"SCRAM\"}";

    private static final UUID HTTP_PROVIDER_ID = UUID.fromString("7d5b83a2-9f31-4e4b-8e1c-5d9f0c2a4b6e");
    // the SQL file holds the escapes of the request body as they are, so they are escaped once more here
    private static final String HTTP_PROVIDER_JSON = """
            {
              "type": "HTTP",
              "restEndpointUrl": "http://localhost:8080/auth",
              "requestMethod": "POST",
              "requestBody": "{\\n  \\"clientId\\": \\"${clientId}\\",\\n  \\"username\\": \\"${username}\\",\\n  \\"password\\": \\"${password}\\"\\n}",
              "headers": {
                "Content-Type": "application/json"
              },
              "credentials": {
                "type": "anonymous"
              },
              "readTimeoutMs": 5000,
              "maxParallelRequestsCount": 100,
              "maxInMemoryBufferSizeInKb": 256,
              "defaultClientType": "DEVICE",
              "authRules": {
                "pub": [],
                "sub": []
              }
            }""";

    // the settings the install seed writes
    private static final List<String> SEEDED_SETTINGS_KEYS = List.of(GENERAL_SETTINGS_KEY, MAIL_SETTINGS_KEY,
            SysAdminSettingType.CONNECTIVITY.getKey(), SysAdminSettingType.WEBSOCKET.getKey(), MQTT_AUTHORIZATION_SETTINGS_KEY);

    private final UserDao userDao;
    private final UserCredentialsDao userCredentialsDao;
    private final AdminSettingsDao adminSettingsDao;
    private final MqttAuthProviderDao mqttAuthProviderDao;
    private final MqttClientCredentialsService mqttClientCredentialsService;
    private final WebSocketConnectionDao webSocketConnectionDao;
    private final StoreSeedState storeSeedState;
    private final boolean enabled;

    public SystemDataTestStep(UserDao userDao, UserCredentialsDao userCredentialsDao, AdminSettingsDao adminSettingsDao,
                              MqttAuthProviderDao mqttAuthProviderDao, MqttClientCredentialsService mqttClientCredentialsService,
                              WebSocketConnectionDao webSocketConnectionDao, StoreSeedState storeSeedState,
                              @Value("${lite.test.system-data.enabled:true}") boolean enabled) {
        this.userDao = userDao;
        this.userCredentialsDao = userCredentialsDao;
        this.adminSettingsDao = adminSettingsDao;
        this.mqttAuthProviderDao = mqttAuthProviderDao;
        this.mqttClientCredentialsService = mqttClientCredentialsService;
        this.webSocketConnectionDao = webSocketConnectionDao;
        this.storeSeedState = storeSeedState;
        this.enabled = enabled;
    }

    @Override
    public void apply() {
        if (!enabled) {
            return;
        }
        if (storeSeedState.seededInThisStart()) {
            removeTheInstallSeedBeyondTheSysadmin();
        }
        insertSysadmin();
        insertSettings(GENERAL_SETTINGS_ID, GENERAL_SETTINGS_KEY, GENERAL_SETTINGS_JSON, SYSTEM_CREATED_TIME);
        insertSettings(MAIL_SETTINGS_ID, MAIL_SETTINGS_KEY, MAIL_SETTINGS_JSON, SYSTEM_CREATED_TIME);
        insertSettings(MQTT_AUTHORIZATION_SETTINGS_ID, MQTT_AUTHORIZATION_SETTINGS_KEY, MQTT_AUTHORIZATION_SETTINGS_JSON, AUTH_PROVIDER_CREATED_TIME);
        insertAuthProvider(BASIC_PROVIDER_ID, MqttAuthProviderType.MQTT_BASIC, BASIC_PROVIDER_JSON);
        insertAuthProvider(X509_PROVIDER_ID, MqttAuthProviderType.X_509, X509_PROVIDER_JSON);
        insertAuthProvider(JWT_PROVIDER_ID, MqttAuthProviderType.JWT, JWT_PROVIDER_JSON);
        insertAuthProvider(SCRAM_PROVIDER_ID, MqttAuthProviderType.SCRAM, SCRAM_PROVIDER_JSON);
        insertAuthProvider(HTTP_PROVIDER_ID, MqttAuthProviderType.HTTP, HTTP_PROVIDER_JSON);
    }

    /**
     * The default WebSocket connection goes before the system WebSocket credentials it refers to.
     */
    private void removeTheInstallSeedBeyondTheSysadmin() {
        User sysadmin = userDao.findByEmail(SYSADMIN_EMAIL);
        if (sysadmin != null) {
            WebSocketConnection connection = webSocketConnectionDao.findByUserIdAndName(sysadmin.getId(),
                    BrokerConstants.WEB_SOCKET_DEFAULT_CONNECTION_NAME);
            if (connection != null) {
                webSocketConnectionDao.removeById(connection.getId());
            }
        }
        MqttClientCredentials systemCredentials = mqttClientCredentialsService.findSystemWebSocketCredentials();
        if (systemCredentials != null) {
            mqttClientCredentialsService.deleteCredentials(systemCredentials);
        }
        SEEDED_SETTINGS_KEYS.forEach(adminSettingsDao::removeByKey);
        for (MqttAuthProviderType type : MqttAuthProviderType.values()) {
            MqttAuthProvider provider = mqttAuthProviderDao.findByType(type);
            if (provider != null) {
                mqttAuthProviderDao.removeById(provider.getId());
            }
        }
    }

    private void insertSysadmin() {
        User sysadmin = userDao.findByEmail(SYSADMIN_EMAIL);
        if (sysadmin == null) {
            sysadmin = new User(SYSADMIN_ID);
            sysadmin.setCreatedTime(SYSTEM_CREATED_TIME);
            sysadmin.setEmail(SYSADMIN_EMAIL);
            sysadmin.setAuthority(Authority.SYS_ADMIN);
            userDao.save(sysadmin);
        }
        // the seeded sysadmin is on the default password and marked to change it, which the shared tests that sign in
        // with that password must not meet
        if (sysadmin.getAdditionalInfo() != null && sysadmin.getAdditionalInfo().has(UserServiceImpl.PASSWORD_CHANGE_REQUIRED)) {
            sysadmin.removeAdditionalInfoField(UserServiceImpl.PASSWORD_CHANGE_REQUIRED);
            userDao.save(sysadmin);
        }
        // the sysadmin of a seeded store has an id of its own
        UUID sysadminId = sysadmin.getId();
        if (userCredentialsDao.findByUserId(sysadminId) == null) {
            UserCredentials credentials = new UserCredentials();
            credentials.setId(SYSADMIN_CREDENTIALS_ID);
            credentials.setCreatedTime(SYSTEM_CREATED_TIME);
            credentials.setUserId(sysadminId);
            credentials.setEnabled(true);
            credentials.setPassword(SYSADMIN_PASSWORD_HASH);
            userCredentialsDao.save(credentials);
        }
    }

    private void insertSettings(UUID id, String key, String json, long createdTime) {
        if (adminSettingsDao.findByKey(key) != null) {
            return;
        }
        AdminSettings settings = new AdminSettings();
        settings.setId(id);
        settings.setCreatedTime(createdTime);
        settings.setKey(key);
        settings.setJsonValue(JacksonUtil.toJsonNode(json));
        adminSettingsDao.save(settings);
    }

    private void insertAuthProvider(UUID id, MqttAuthProviderType type, String configurationJson) {
        if (mqttAuthProviderDao.findByType(type) != null) {
            return;
        }
        MqttAuthProvider provider = new MqttAuthProvider();
        provider.setId(id);
        provider.setCreatedTime(AUTH_PROVIDER_CREATED_TIME);
        provider.setEnabled(false);
        provider.setType(type);
        provider.setConfiguration(JacksonUtil.fromString(configurationJson, MqttAuthProviderConfiguration.class));
        provider.setAdditionalInfo(JacksonUtil.newObjectNode());
        mqttAuthProviderDao.save(provider);
    }

}
