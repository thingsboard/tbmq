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
package org.thingsboard.mqtt.broker.dao.client;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SslMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.service.DataValidator;
import org.thingsboard.mqtt.broker.dao.util.exception.DbExceptionUtil;
import org.thingsboard.mqtt.broker.dao.util.mapping.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.util.protocol.ProtocolUtil;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.thingsboard.mqtt.broker.dao.service.Validator.validatePageLink;

@Service
@Slf4j
public class MqttClientCredentialsServiceImpl implements MqttClientCredentialsService {

    @Autowired
    private MqttClientCredentialsDao mqttClientCredentialsDao;

    // TODO: move encoder out of DAO level
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    public MqttClientCredentials saveCredentials(MqttClientCredentials mqttClientCredentials) {
        if(mqttClientCredentials.getCredentialsType() == null){
            throw new DataValidationException("MQTT Client credentials type should be specified");
        }
        switch (mqttClientCredentials.getCredentialsType()) {
            case MQTT_BASIC:
                preprocessBasicMqttCredentials(mqttClientCredentials);
                break;
            case SSL:
                preprocessSslMqttCredentials(mqttClientCredentials);
                break;
            default:
                throw new DataValidationException("Unknown credentials type!");
        }
        log.trace("Executing saveCredentials [{}]", mqttClientCredentials);
        credentialsValidator.validate(mqttClientCredentials);
        try {
            return mqttClientCredentialsDao.save(mqttClientCredentials);
        } catch (Exception t) {
            ConstraintViolationException e = DbExceptionUtil.extractConstraintViolationException(t).orElse(null);
            if (e != null && e.getConstraintName() != null
                    && e.getConstraintName().equalsIgnoreCase("mqtt_client_credentials_id_unq_key")) {
                throw new DataValidationException("Specified credentials are already registered!");
            } else {
                throw t;
            }
        }
    }

    @Override
    public void deleteCredentials(UUID id) {
        log.trace("Executing deleteCredentials [{}]", id);
        mqttClientCredentialsDao.removeById(id);
    }

    @Override
    public List<MqttClientCredentials> findMatchingCredentials(List<String> credentialIds) {
        log.trace("Executing findMatchingCredentials [{}]", credentialIds);
        return mqttClientCredentialsDao.findAllByCredentialsIds(credentialIds);
    }

    @Override
    public PageData<MqttClientCredentials> getCredentials(PageLink pageLink) {
        log.trace("Executing getCredentials, pageLink [{}]", pageLink);
        validatePageLink(pageLink);
        return mqttClientCredentialsDao.findAll(pageLink);
    }

    @Override
    public Optional<MqttClientCredentials> getCredentialsById(UUID id) {
        log.trace("Executing getCredentialsById [{}]", id);
        return Optional.ofNullable(mqttClientCredentialsDao.findById(id));
    }

    private void preprocessBasicMqttCredentials(MqttClientCredentials mqttClientCredentials) {
        BasicMqttCredentials mqttCredentials = getMqttCredentials(mqttClientCredentials, BasicMqttCredentials.class);
        if (StringUtils.isEmpty(mqttCredentials.getClientId()) && StringUtils.isEmpty(mqttCredentials.getUserName())) {
            throw new DataValidationException("Both mqtt client id and user name are empty!");
        }
        if (mqttCredentials.getPassword() != null) {
            mqttCredentials.setPassword(passwordEncoder.encode(mqttCredentials.getPassword()));
            mqttClientCredentials.setCredentialsValue(JacksonUtil.toString(mqttCredentials));
        }
        if (StringUtils.isEmpty(mqttCredentials.getClientId())) {
            mqttClientCredentials.setCredentialsId(ProtocolUtil.usernameCredentialsId(mqttCredentials.getUserName()));
        } else if (StringUtils.isEmpty(mqttCredentials.getUserName())) {
            mqttClientCredentials.setCredentialsId(ProtocolUtil.clientIdCredentialsId(mqttCredentials.getClientId()));
        } else {
            mqttClientCredentials.setCredentialsId(ProtocolUtil.mixedCredentialsId(mqttCredentials.getUserName(), mqttCredentials.getClientId()));
        }

        if (mqttCredentials.getAuthorizationRulePattern() != null) {
            try {
                Pattern.compile(mqttCredentials.getAuthorizationRulePattern());
            } catch (PatternSyntaxException e) {
                throw new DataValidationException("Authorization rule pattern should be a valid regex!");
            }
        }
    }

    private void preprocessSslMqttCredentials(MqttClientCredentials mqttClientCredentials) {
        SslMqttCredentials mqttCredentials = getMqttCredentials(mqttClientCredentials, SslMqttCredentials.class);
        if (StringUtils.isEmpty(mqttCredentials.getParentCertCommonName())) {
            throw new DataValidationException("Parent certificate's common name should be specified!");
        }
        if (mqttCredentials.getAuthorizationRulesMapping() == null || mqttCredentials.getAuthorizationRulesMapping().isEmpty()) {
            throw new DataValidationException("Authorization rules mapping should be specified!");
        }
        mqttCredentials.getAuthorizationRulesMapping().forEach((certificateMatcherRegex, topicRule) -> {
            try {
                Pattern.compile(certificateMatcherRegex);
            } catch (PatternSyntaxException e) {
                throw new DataValidationException("Certificate matcher regex [" + certificateMatcherRegex + "] must be a valid regex");
            }
            try {
                Pattern.compile(topicRule);
            } catch (PatternSyntaxException e) {
                throw new DataValidationException("Topic authorization rule [" + topicRule + "] must be a valid regex");
            }
        });

        String credentialsId = ProtocolUtil.sslCredentialsId(mqttCredentials.getParentCertCommonName());
        mqttClientCredentials.setCredentialsId(credentialsId);
    }

    private <T> T getMqttCredentials(MqttClientCredentials mqttClientCredentials, Class<T> credentialsClassType) {
        T credentials;
        try {
            credentials = JacksonUtil.fromString(mqttClientCredentials.getCredentialsValue(), credentialsClassType);
            if (credentials == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new DataValidationException("Invalid credentials body for mqtt credentials!");
        }
        return credentials;
    }

    private final DataValidator<MqttClientCredentials> credentialsValidator =
            new DataValidator<>() {
                @Override
                protected void validateCreate(MqttClientCredentials mqttClientCredentials) {
                    if (mqttClientCredentialsDao.findByCredentialsId(mqttClientCredentials.getCredentialsId()) != null) {
                        throw new DataValidationException("Such MQTT Client credentials are already created!");
                    }
                }

                @Override
                protected void validateUpdate(MqttClientCredentials mqttClientCredentials) {
                    if (mqttClientCredentialsDao.findById(mqttClientCredentials.getId()) == null) {
                        throw new DataValidationException("Unable to update non-existent MQTT Client credentials!");
                    }
                    MqttClientCredentials existingCredentials = mqttClientCredentialsDao.findByCredentialsId(mqttClientCredentials.getCredentialsId());
                    if (existingCredentials != null && !existingCredentials.getId().equals(mqttClientCredentials.getId())) {
                        throw new DataValidationException("New MQTT Client credentials are already created!");
                    }
                }

                @Override
                protected void validateDataImpl(MqttClientCredentials mqttClientCredentials) {
                    if (mqttClientCredentials.getName() == null) {
                        throw new DataValidationException("MQTT Client credentials name should be specified!");
                    }
                    if (mqttClientCredentials.getCredentialsType() == null) {
                        throw new DataValidationException("MQTT Client credentials type should be specified!");
                    }
                    if (StringUtils.isEmpty(mqttClientCredentials.getCredentialsId())) {
                        throw new DataValidationException("MQTT Client credentials id should be specified!");
                    }
                }
            };

}
