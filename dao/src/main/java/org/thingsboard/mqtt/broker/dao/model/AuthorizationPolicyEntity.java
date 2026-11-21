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
package org.thingsboard.mqtt.broker.dao.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLJsonPGObjectJsonbType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.security.AuthorizationPolicy;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.util.mapping.JsonConverter;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = ModelConstants.AUTHORIZATION_POLICY_COLUMN_FAMILY_NAME)
public class AuthorizationPolicyEntity extends BaseSqlEntity<AuthorizationPolicy>
        implements BaseEntity<AuthorizationPolicy> {

    public static final Map<String, String> authorizationPolicyColumnMap = Map.of("name", "name");

    @Column(name = ModelConstants.AUTHORIZATION_POLICY_NAME_PROPERTY, unique = true)
    private String name;

    @Convert(converter = JsonConverter.class)
    @JdbcType(PostgreSQLJsonPGObjectJsonbType.class)
    @Column(name = ModelConstants.AUTHORIZATION_POLICY_RULES_PROPERTY, columnDefinition = "jsonb")
    private JsonNode authorizationRules;

    @Convert(converter = JsonConverter.class)
    @Column(name = ModelConstants.AUTHORIZATION_POLICY_ADDITIONAL_INFO_PROPERTY)
    private JsonNode additionalInfo;

    public AuthorizationPolicyEntity() {
    }

    public AuthorizationPolicyEntity(AuthorizationPolicy policy) {
        if (policy.getId() != null) {
            setId(policy.getId());
        }
        setCreatedTime(policy.getCreatedTime());
        this.name = policy.getName();
        this.authorizationRules = JacksonUtil.valueToTree(policy.getAuthorizationRules());
        this.additionalInfo = policy.getAdditionalInfo();
    }

    @Override
    public AuthorizationPolicy toData() {
        AuthorizationPolicy policy = new AuthorizationPolicy();
        policy.setId(id);
        policy.setCreatedTime(createdTime);
        policy.setName(name);
        policy.setAuthorizationRules(JacksonUtil.toValue(authorizationRules, PubSubAuthorizationRules.class));
        policy.setAdditionalInfo(additionalInfo);
        return policy;
    }
}
