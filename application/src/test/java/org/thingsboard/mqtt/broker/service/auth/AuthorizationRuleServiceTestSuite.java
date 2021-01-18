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
package org.thingsboard.mqtt.broker.service.auth;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SslMqttCredentials;
import org.thingsboard.mqtt.broker.dao.util.mapping.JacksonUtil;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.exception.AuthorizationException;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthorizationRule;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class AuthorizationRuleServiceTestSuite {
    private AuthorizationRuleService authorizationRuleService;

    @Before
    public void init() {
        this.authorizationRuleService = new DefaultAuthorizationRuleService();
    }


    /*
        parseAuthorizationRule tests
     */
    @Test
    public void testSuccessfulCredentialsParse() throws AuthenticationException {
        SslMqttCredentials sslMqttCredentials = new SslMqttCredentials(
                "qwer1234-abc-p01.4321.ab.abc",
                ".*(abc-p01).*",
                Map.of("abc-p01", "test/.*")
        );
        AuthorizationRule authorizationRule = authorizationRuleService.parseAuthorizationRule(JacksonUtil.toString(sslMqttCredentials));
        Assert.assertEquals("test/.*", authorizationRule.getPattern().pattern());
    }

    @Test
    public void testSuccessfulCredentialsParse_MultiplePossibleKeys() throws AuthenticationException {
        SslMqttCredentials sslMqttCredentials = new SslMqttCredentials(
                "qwer1234-abc-p01.4321.ab.abc",
                ".*(abc-p01|123-qwe|qqq).*",
                Map.of("abc-p01", "test/.*")
        );
        AuthorizationRule authorizationRule = authorizationRuleService.parseAuthorizationRule(JacksonUtil.toString(sslMqttCredentials));
        Assert.assertEquals("test/.*", authorizationRule.getPattern().pattern());
    }

    @Test(expected = AuthenticationException.class)
    public void testNonExistentKey() throws AuthenticationException {
        SslMqttCredentials sslMqttCredentials = new SslMqttCredentials(
                "qwer1234-abc-p01.4321.ab.abc",
                "(.*)(abc-p01|123-qwe|qqq)(.*)",
                Map.of("not_valid_key", "test/.*")
        );
        authorizationRuleService.parseAuthorizationRule(JacksonUtil.toString(sslMqttCredentials));
    }

    @Test(expected = AuthenticationException.class)
    public void testPatternDontMatch() throws AuthenticationException {
        SslMqttCredentials sslMqttCredentials = new SslMqttCredentials(
                "qwer1234-abc-p01.4321.ab.abc",
                "(.*)(not_in_common_name)(.*)",
                Map.of("key", "test/.*")
        );
        authorizationRuleService.parseAuthorizationRule(JacksonUtil.toString(sslMqttCredentials));
    }

    /*
            validateAuthorizationRule tests
    */
    @Test
    public void testSuccessfulRuleValidation_Basic() throws AuthorizationException {
        AuthorizationRule authorizationRule = new AuthorizationRule(Pattern.compile("test/.*"));
        authorizationRuleService.validateAuthorizationRule(authorizationRule,
                Arrays.asList("test/AAA", "test/BBB/CCC", "test/", "test/#", "test/+/AAA", "test/+/+"));
    }

    @Test
    public void testSuccessfulRuleValidation_NoRule() throws AuthorizationException {
        authorizationRuleService.validateAuthorizationRule(null, Collections.singleton("test"));
    }

    @Test(expected = AuthorizationException.class)
    public void testFailedRuleValidation_Basic() throws AuthorizationException {
        AuthorizationRule authorizationRule = new AuthorizationRule(Pattern.compile("test/.*"));
        authorizationRuleService.validateAuthorizationRule(authorizationRule,
                Arrays.asList("test/AAA", "test/BBB/CCC", "not_test/AAA", "test/"));
    }
}
