/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.CacheNameResolver;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ClientCredentialsQuery;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ScramMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SslMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.dto.ClientCredentialsInfoDto;
import org.thingsboard.mqtt.broker.common.data.dto.ShortMqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.common.util.MqttClientCredentialsUtil;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.service.DataValidator;
import org.thingsboard.mqtt.broker.dao.util.exception.DbExceptionUtil;
import org.thingsboard.mqtt.broker.dao.util.protocol.ProtocolUtil;

import java.util.ArrayList;
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
    private final CacheNameResolver cacheNameResolver;

    @Override
    public MqttClientCredentials saveCredentials(MqttClientCredentials mqttClientCredentials) {
        if (mqttClientCredentials.getCredentialsType() == null) {
            throw new DataValidationException("MQTT Client credentials type should be specified");
        }
        switch (mqttClientCredentials.getCredentialsType()) {
            case MQTT_BASIC -> preprocessBasicMqttCredentials(mqttClientCredentials);
            case SSL -> preprocessSslMqttCredentials(mqttClientCredentials);
            case SCRAM -> preprocessScramMqttCredentials(mqttClientCredentials);
            default -> throw new DataValidationException("Unknown credentials type!");
        }
        if (mqttClientCredentials.getClientType() == null) {
            mqttClientCredentials.setClientType(ClientType.DEVICE);
        }
        if (log.isTraceEnabled()) {
            log.trace("Executing saveCredentials [{}]", mqttClientCredentials);
        }
        credentialsValidator.validate(mqttClientCredentials);
        try {
            MqttClientCredentials currentCredentials = getCurrentCredentialsById(mqttClientCredentials.getId());
            MqttClientCredentials savedMqttClientCredentials = mqttClientCredentialsDao.save(mqttClientCredentials);

            evictCache(mqttClientCredentials, currentCredentials);
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
    public MqttClientCredentials saveSystemWebSocketCredentials() {
        MqttClientCredentials mqttClientCredentials = new MqttClientCredentials();
        mqttClientCredentials.setName(BrokerConstants.WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_NAME);
        mqttClientCredentials.setClientType(ClientType.DEVICE);
        mqttClientCredentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        mqttClientCredentials.setCredentialsValue(JacksonUtil.toString(BasicMqttCredentials.newInstance(BrokerConstants.WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_USERNAME)));
        preprocessBasicMqttCredentials(mqttClientCredentials);
        return mqttClientCredentialsDao.save(mqttClientCredentials);
    }

    @Override
    public void deleteCredentials(UUID id) {
        if (log.isTraceEnabled()) {
            log.trace("Executing deleteCredentials [{}]", id);
        }
        MqttClientCredentials clientCredentials = mqttClientCredentialsDao.findById(id);
        if (clientCredentials == null) {
            return;
        }
        mqttClientCredentialsDao.removeById(id);
        evictCache(clientCredentials, clientCredentials);
    }

    @Override
    public MqttClientCredentials findSystemWebSocketCredentials() {
        if (log.isTraceEnabled()) {
            log.trace("Executing findSystemWebSocketCredentials");
        }
        return mqttClientCredentialsDao.findSystemWebSocketCredentials();
    }

    @Override
    public MqttClientCredentials findCredentialsByName(String name) {
        if (log.isTraceEnabled()) {
            log.trace("Executing findCredentialsByName");
        }
        return mqttClientCredentialsDao.findCredentialsByName(name);
    }

    @Override
    public List<MqttClientCredentials> findMatchingCredentials(List<String> credentialIds) {
        if (log.isTraceEnabled()) {
            log.trace("Executing findMatchingCredentials [{}]", credentialIds);
        }
        List<MqttClientCredentials> result = Lists.newArrayList();
        List<String> credentialIdsFromDb = Lists.newArrayList();

        Cache cache = getCache(CacheConstants.MQTT_CLIENT_CREDENTIALS_CACHE);
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
        if (log.isTraceEnabled()) {
            log.trace("Executing getCredentials, pageLink [{}]", pageLink);
        }
        validatePageLink(pageLink);
        return toShortMqttClientCredentialsPageData(mqttClientCredentialsDao.findAll(pageLink));
    }

    @Override
    public PageData<ShortMqttClientCredentials> getCredentialsV2(ClientCredentialsQuery query) {
        if (log.isTraceEnabled()) {
            log.trace("Executing getCredentialsV2, query [{}]", query);
        }
        validatePageLink(query.getPageLink());
        return toShortMqttClientCredentialsPageData(mqttClientCredentialsDao.findAllV2(query));
    }

    private PageData<ShortMqttClientCredentials> toShortMqttClientCredentialsPageData(PageData<MqttClientCredentials> pageData) {
        List<ShortMqttClientCredentials> shortMqttCredentials = pageData.getData().stream()
                .map(MqttClientCredentialsUtil::toShortMqttClientCredentials)
                .collect(Collectors.toList());
        return new PageData<>(shortMqttCredentials, pageData.getTotalPages(), pageData.getTotalElements(), pageData.hasNext());
    }

    @Override
    public Optional<MqttClientCredentials> getCredentialsById(UUID id) {
        if (log.isTraceEnabled()) {
            log.trace("Executing getCredentialsById [{}]", id);
        }
        return Optional.ofNullable(mqttClientCredentialsDao.findById(id));
    }

    @Override
    public ClientCredentialsInfoDto getClientCredentialsInfo() {
        List<MqttClientCredentials> allMqttClientCredentials = new ArrayList<>();

        PageLink pageLink = new PageLink(BrokerConstants.DEFAULT_PAGE_SIZE);
        PageData<MqttClientCredentials> batch;
        boolean hasNextBatch;
        do {
            batch = mqttClientCredentialsDao.findAll(pageLink);
            allMqttClientCredentials.addAll(batch.getData());

            hasNextBatch = batch.hasNext();
            pageLink = pageLink.nextPageLink();
        } while (hasNextBatch);

        int totalCount = allMqttClientCredentials.size();
        long deviceCredentialsCount = allMqttClientCredentials
                .stream()
                .filter(credentials -> ClientType.DEVICE == credentials.getClientType())
                .count();
        long applicationCredentialsCount = totalCount - deviceCredentialsCount;

        return new ClientCredentialsInfoDto(deviceCredentialsCount, applicationCredentialsCount, totalCount);
    }

    @Override
    public boolean existsByCredentialsType(ClientCredentialsType credentialsType) {
        if (log.isTraceEnabled()) {
            log.trace("Executing existsByCredentialsType [{}]", credentialsType);
        }
        return mqttClientCredentialsDao.existsByCredentialsType(credentialsType);
    }

    @Override
    public List<MqttClientCredentials> findByCredentialsType(ClientCredentialsType type) {
        log.trace("Executing findByCredentialsType [{}]", type);
        return mqttClientCredentialsDao.findByCredentialsType(type);
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
        validateAndCompileAuthRules(mqttCredentials.getAuthRules());
    }

    private void preprocessSslMqttCredentials(MqttClientCredentials mqttClientCredentials) {
        SslMqttCredentials mqttCredentials = getMqttCredentials(mqttClientCredentials, SslMqttCredentials.class);
        if (StringUtils.isEmpty(mqttCredentials.getCertCnPattern())) {
            throw new DataValidationException("Certificate common name pattern should be specified!");
        }
        if (mqttCredentials.isCertCnIsRegex()) {
            String certCnPattern = mqttCredentials.getCertCnPattern();
            try {
                Pattern.compile(certCnPattern);
            } catch (PatternSyntaxException e) {
                throw new DataValidationException("Certificate common name pattern [" + certCnPattern + "] must be a valid regex");
            }
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
            validateAndCompileAuthRules(authRules);
        });

        String credentialsId = ProtocolUtil.sslCredentialsId(mqttCredentials.getCertCnPattern());
        mqttClientCredentials.setCredentialsId(credentialsId);
    }

    private void preprocessScramMqttCredentials(MqttClientCredentials mqttClientCredentials) {
        ScramMqttCredentials mqttCredentials = getMqttCredentials(mqttClientCredentials, ScramMqttCredentials.class);
        if (StringUtils.isEmpty(mqttCredentials.getUserName())) {
            throw new DataValidationException("User name is empty!");
        }
        mqttClientCredentials.setCredentialsId(ProtocolUtil.scramCredentialsId(mqttCredentials.getUserName()));
        mqttClientCredentials.setCredentialsValue(JacksonUtil.toString(mqttCredentials));
        validateAndCompileAuthRules(mqttCredentials.getAuthRules());
    }

    private void validateAndCompileAuthRules(PubSubAuthorizationRules authRules) {
        if (authRules == null) {
            throw new DataValidationException("AuthRules are null!");
        }
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

    private void evictCache(MqttClientCredentials newCredentials, MqttClientCredentials currentCredentials) {
        evictMqttClientCredentialsCache(currentCredentials);
        invalidateBasicCredentialsPasswordCache(currentCredentials);
        invalidateSslRegexBasedCredentialsCache(newCredentials, currentCredentials);
    }

    private void evictMqttClientCredentialsCache(MqttClientCredentials clientCredentials) {
        if (clientCredentials != null) {
            getCache(CacheConstants.MQTT_CLIENT_CREDENTIALS_CACHE).evictIfPresent(clientCredentials.getCredentialsId());
        }
    }

    private void invalidateBasicCredentialsPasswordCache(MqttClientCredentials clientCredentials) {
        if (clientCredentials != null && ClientCredentialsType.MQTT_BASIC == clientCredentials.getCredentialsType()) {
            getCache(CacheConstants.BASIC_CREDENTIALS_PASSWORD_CACHE).invalidate();
        }
    }

    private void invalidateSslRegexBasedCredentialsCache(MqttClientCredentials newCredentials, MqttClientCredentials currentCredentials) {
        if (isSslType(newCredentials) || (currentCredentials != null && isSslType(currentCredentials))) {
            getCache(CacheConstants.SSL_REGEX_BASED_CREDENTIALS_CACHE).evictIfPresent(ClientCredentialsType.SSL);
        }
    }

    private Cache getCache(String cacheName) {
        return cacheNameResolver.getCache(cacheName);
    }

    private final DataValidator<MqttClientCredentials> credentialsValidator =
            new DataValidator<>() {
                @Override
                protected void validateCreate(MqttClientCredentials mqttClientCredentials) {
                    if (mqttClientCredentialsDao.findByCredentialsId(mqttClientCredentials.getCredentialsId()) != null) {
                        throw new DataValidationException("Such MQTT Client credentials are already created!");
                    }
                    if (BrokerConstants.WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_NAME.equals(mqttClientCredentials.getName())) {
                        throw new DataValidationException("It is forbidden to save credentials with System WebSocket MQTT client credentials name!");
                    }
                }

                @Override
                protected void validateUpdate(MqttClientCredentials mqttClientCredentials) {
                    MqttClientCredentials byId = mqttClientCredentialsDao.findById(mqttClientCredentials.getId());
                    if (byId == null) {
                        throw new DataValidationException("Unable to update non-existent MQTT Client credentials!");
                    }
                    if (BrokerConstants.WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_NAME.equals(byId.getName())) {
                        if (!BrokerConstants.WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_NAME.equals(mqttClientCredentials.getName())) {
                            throw new DataValidationException("It is forbidden to update System WebSocket MQTT client credentials name!");
                        }
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

    private boolean isSslType(MqttClientCredentials clientCredentials) {
        return ClientCredentialsType.SSL == clientCredentials.getCredentialsType();
    }
}
