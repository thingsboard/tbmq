/**
 * Copyright © 2016-2022 The Thingsboard Authors
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

import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SslMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.dto.ShortMqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.MqttClientCredentialsUtil;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.service.DataValidator;
import org.thingsboard.mqtt.broker.dao.util.exception.DbExceptionUtil;
import org.thingsboard.mqtt.broker.dao.util.protocol.ProtocolUtil;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import static org.thingsboard.mqtt.broker.dao.service.Validator.validatePageLink;

@Service
@Slf4j
@RequiredArgsConstructor
public class MqttClientCredentialsServiceImpl implements MqttClientCredentialsService {

    private final MqttClientCredentialsDao mqttClientCredentialsDao;
    private final CacheManager cacheManager;

    @Override
    public MqttClientCredentials saveCredentials(MqttClientCredentials mqttClientCredentials) {
        if (mqttClientCredentials.getCredentialsType() == null) {
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
        if (mqttClientCredentials.getClientType() == null) {
            mqttClientCredentials.setClientType(ClientType.DEVICE);
        }
        log.trace("Executing saveCredentials [{}]", mqttClientCredentials);
        credentialsValidator.validate(mqttClientCredentials);
        try {
            MqttClientCredentials currentCredentials = getCurrentCredentialsById(mqttClientCredentials.getId());
            MqttClientCredentials savedMqttClientCredentials = mqttClientCredentialsDao.save(mqttClientCredentials);
            evictCache(currentCredentials);
            return savedMqttClientCredentials;
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
        MqttClientCredentials clientCredentials = mqttClientCredentialsDao.findById(id);
        mqttClientCredentialsDao.removeById(id);
        if (clientCredentials != null) {
            evictCache(clientCredentials);
        }
    }

    @Override
    public List<MqttClientCredentials> findMatchingCredentials(List<String> credentialIds) {
        log.trace("Executing findMatchingCredentials [{}]", credentialIds);
        List<MqttClientCredentials> result = Lists.newArrayList();
        List<String> credentialIdsFromDb = Lists.newArrayList();

        Cache cache = getCache();
        for (var credentialsId : credentialIds) {
            var clientCredentials = cache.get(credentialsId, MqttClientCredentials.class);
            if (clientCredentials != null) {
                result.add(clientCredentials);
            } else {
                credentialIdsFromDb.add(credentialsId);
            }
        }

        var clientCredentialsFromDb = mqttClientCredentialsDao.findAllByCredentialsIds(credentialIdsFromDb);
        clientCredentialsFromDb.forEach(credentials -> cache.put(credentials.getCredentialsId(), credentials));

        result.addAll(clientCredentialsFromDb);
        return result;
    }

    @Override
    public PageData<ShortMqttClientCredentials> getCredentials(PageLink pageLink) {
        log.trace("Executing getCredentials, pageLink [{}]", pageLink);
        validatePageLink(pageLink);
        PageData<MqttClientCredentials> pageData = mqttClientCredentialsDao.findAll(pageLink);
        List<ShortMqttClientCredentials> shortMqttCredentials = pageData.getData().stream()
                .map(MqttClientCredentialsUtil::toShortMqttClientCredentials)
                .collect(Collectors.toList());
        return new PageData<>(shortMqttCredentials, pageData.getTotalPages(), pageData.getTotalElements(), pageData.hasNext());
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
        if (StringUtils.isEmpty(mqttCredentials.getClientId())) {
            mqttClientCredentials.setCredentialsId(ProtocolUtil.usernameCredentialsId(mqttCredentials.getUserName()));
        } else if (StringUtils.isEmpty(mqttCredentials.getUserName())) {
            mqttClientCredentials.setCredentialsId(ProtocolUtil.clientIdCredentialsId(mqttCredentials.getClientId()));
        } else {
            mqttClientCredentials.setCredentialsId(ProtocolUtil.mixedCredentialsId(mqttCredentials.getUserName(), mqttCredentials.getClientId()));
        }

        PubSubAuthorizationRules authRules = mqttCredentials.getAuthRules();
        if (authRules == null) {
            throw new DataValidationException("AuthRules are null!");
        }
        compileAuthRules(authRules);
    }

    private void preprocessSslMqttCredentials(MqttClientCredentials mqttClientCredentials) {
        SslMqttCredentials mqttCredentials = getMqttCredentials(mqttClientCredentials, SslMqttCredentials.class);
        if (StringUtils.isEmpty(mqttCredentials.getParentCertCommonName())) {
            throw new DataValidationException("Parent certificate's common name should be specified!");
        }
        if (CollectionUtils.isEmpty(mqttCredentials.getAuthRulesMapping())) {
            throw new DataValidationException("Authorization rules mapping should be specified!");
        }
        mqttCredentials.getAuthRulesMapping().forEach((certificateMatcherRegex, authRules) -> {
            try {
                Pattern.compile(certificateMatcherRegex);
            } catch (PatternSyntaxException e) {
                throw new DataValidationException("Certificate matcher regex [" + certificateMatcherRegex + "] must be a valid regex");
            }
            if (authRules == null) {
                throw new DataValidationException("AuthRules are null!");
            }
            compileAuthRules(authRules);
        });

        String credentialsId = ProtocolUtil.sslCredentialsId(mqttCredentials.getParentCertCommonName());
        mqttClientCredentials.setCredentialsId(credentialsId);
    }

    private void compileAuthRules(PubSubAuthorizationRules authRules) {
        compileAuthRules(authRules.getPubAuthRulePatterns(), "Publish auth rule patterns should be a valid regexes!");
        compileAuthRules(authRules.getSubAuthRulePatterns(), "Subscribe auth rule patterns should be a valid regexes!");
    }

    private void compileAuthRules(List<String> authRules, String message) {
        if (!CollectionUtils.isEmpty(authRules)) {
            try {
                authRules.forEach(Pattern::compile);
            } catch (PatternSyntaxException e) {
                throw new DataValidationException(message);
            }
        }
    }

    private <T> T getMqttCredentials(MqttClientCredentials mqttClientCredentials, Class<T> credentialsClassType) {
        try {
            return MqttClientCredentialsUtil.getMqttCredentials(mqttClientCredentials, credentialsClassType);
        } catch (IllegalArgumentException e) {
            throw new DataValidationException("Could not parse client credentials!", e);
        }
    }

    private MqttClientCredentials getCurrentCredentialsById(UUID id) {
        return id != null ? getCredentialsById(id).orElse(null) : null;
    }

    private void evictCache(MqttClientCredentials clientCredentials) {
        Cache cache = getCache();
        if (clientCredentials != null) {
            cache.evictIfPresent(clientCredentials.getCredentialsId());
        }
    }

    private Cache getCache() {
        return cacheManager.getCache(CacheConstants.MQTT_CLIENT_CREDENTIALS_CACHE);
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
