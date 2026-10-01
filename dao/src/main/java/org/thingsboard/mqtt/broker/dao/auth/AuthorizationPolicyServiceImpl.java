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
package org.thingsboard.mqtt.broker.dao.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.TbCacheOps;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.AuthorizationPolicy;
import org.thingsboard.mqtt.broker.common.data.util.AuthRulesUtil;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsDao;
import org.thingsboard.mqtt.broker.dao.service.DataValidator;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.Optional;
import java.util.UUID;

import static org.thingsboard.mqtt.broker.dao.service.Validator.validatePageLink;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthorizationPolicyServiceImpl implements AuthorizationPolicyService {

    private final AuthorizationPolicyDao policyDao;
    private final MqttClientCredentialsDao credentialsDao;
    private final TbCacheOps cacheOps;

    @Override
    public AuthorizationPolicy savePolicy(AuthorizationPolicy policy) {
        policyValidator.validate(policy);
        AuthorizationPolicy saved = policyDao.save(policy);
        cacheOps.evictIfPresentSafe(CacheConstants.AUTHORIZATION_POLICY_CACHE, saved.getId());
        return saved;
    }

    @Override
    public void deletePolicy(UUID id) {
        if (credentialsDao.existsByAuthorizationPolicyId(id)) {
            throw new DataValidationException("Authorization policy is still used by MQTT client credentials!");
        }
        policyDao.removeById(id);
        cacheOps.evictIfPresentSafe(CacheConstants.AUTHORIZATION_POLICY_CACHE, id);
    }

    @Override
    public Optional<AuthorizationPolicy> getPolicyById(UUID id) {
        var lookup = cacheOps.lookup(CacheConstants.AUTHORIZATION_POLICY_CACHE, id, AuthorizationPolicy.class);
        if (lookup.status() == TbCacheOps.Status.HIT) {
            return Optional.of(lookup.value());
        }
        if (lookup.status() == TbCacheOps.Status.CACHED_NULL) {
            return Optional.empty();
        }
        AuthorizationPolicy policy = policyDao.findById(id);
        if (policy == null) {
            cacheOps.putNull(CacheConstants.AUTHORIZATION_POLICY_CACHE, id);
            return Optional.empty();
        }
        cacheOps.put(CacheConstants.AUTHORIZATION_POLICY_CACHE, id, policy);
        return Optional.of(policy);
    }

    @Override
    public PageData<AuthorizationPolicy> getPolicies(PageLink pageLink) {
        validatePageLink(pageLink);
        return policyDao.findAll(pageLink);
    }

    @Override
    public PubSubAuthorizationRules resolveRules(UUID policyId, PubSubAuthorizationRules embeddedRules) {
        if (policyId == null) {
            return embeddedRules;
        }
        return getPolicyById(policyId)
                .map(AuthorizationPolicy::getAuthorizationRules)
                .orElseThrow(() -> new DataValidationException("Authorization policy not found: " + policyId));
    }

    private final DataValidator<AuthorizationPolicy> policyValidator = new DataValidator<>() {
        @Override
        protected void validateCreate(AuthorizationPolicy policy) {
            if (policyDao.findByName(policy.getName()) != null) {
                throw new DataValidationException("Authorization policy with such name already exists!");
            }
        }

        @Override
        protected void validateUpdate(AuthorizationPolicy policy) {
            AuthorizationPolicy existing = policyDao.findById(policy.getId());
            if (existing == null) {
                throw new DataValidationException("Unable to update non-existent authorization policy!");
            }
            AuthorizationPolicy byName = policyDao.findByName(policy.getName());
            if (byName != null && !byName.getId().equals(policy.getId())) {
                throw new DataValidationException("Authorization policy with such name already exists!");
            }
        }

        @Override
        protected void validateDataImpl(AuthorizationPolicy policy) {
            if (StringUtils.isEmpty(policy.getName())) {
                throw new DataValidationException("Authorization policy name should be specified!");
            }
            // A policy can be shared by Basic, SCRAM, and X.509 credentials, so it must not
            // contain authentication-method-specific placeholders such as ${cn}.
            AuthRulesUtil.validateAndCompileAuthRules(policy.getAuthorizationRules());
        }
    };
}
