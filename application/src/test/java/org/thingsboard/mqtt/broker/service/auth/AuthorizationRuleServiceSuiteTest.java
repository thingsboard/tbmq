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
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SslMqttCredentials;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthorizationRule;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class AuthorizationRuleServiceSuiteTest {
    private AuthorizationRuleService authorizationRuleService;

    @Before
    public void init() {
        this.authorizationRuleService = new DefaultAuthorizationRuleService();
    }

    /*
        parseSslAuthorizationRule tests
     */
    @Test
    public void testSuccessfulCredentialsParse_Ssl() throws AuthenticationException {
        SslMqttCredentials sslMqttCredentials = new SslMqttCredentials(
                "parent.com",
                Map.of(".*abc-123.*", List.of("test/.*"))
        );
        List<AuthorizationRule> authorizationRules = authorizationRuleService.parseSslAuthorizationRule(sslMqttCredentials, "123456abc-1234321.ab.abc");
        Assert.assertEquals(1, authorizationRules.size());
        Assert.assertEquals("test/.*", authorizationRules.get(0).getPatterns().get(0).pattern());
    }

    @Test
    public void testSuccessfulCredentialsParse_Ssl_MultiplePossibleKeys() throws AuthenticationException {
        SslMqttCredentials sslMqttCredentials = new SslMqttCredentials(
                "parent.com",
                Map.of(
                        ".*abc-p01.*", List.of("1/.*", "5/.*"),
                        ".*qwer1234.*", List.of("2/.*"),
                        ".*4321.*", List.of("3/.*"),
                        ".*nonexistent.*", List.of("4/.*")
                )
        );
        List<AuthorizationRule> authorizationRules = authorizationRuleService.parseSslAuthorizationRule(sslMqttCredentials, "qwer1234-abc-p01.4321.ab.abc");
        Set<String> patterns = authorizationRules.stream()
                .map(AuthorizationRule::getPatterns).collect(Collectors.toList())
                .stream().flatMap(List::stream)
                .map(Pattern::pattern)
                .collect(Collectors.toSet());
        Assert.assertEquals(4, patterns.size());
        Assert.assertEquals(Set.of("1/.*", "5/.*", "2/.*", "3/.*"), patterns);
    }

    @Test(expected = AuthenticationException.class)
    public void testEmptyRules() throws AuthenticationException {
        SslMqttCredentials sslMqttCredentials = new SslMqttCredentials("parent.com", Map.of());
        authorizationRuleService.parseSslAuthorizationRule(sslMqttCredentials, "123456789");
    }

    @Test(expected = AuthenticationException.class)
    public void testPatternDontMatch_Ssl() throws AuthenticationException {
        SslMqttCredentials sslMqttCredentials = new SslMqttCredentials("parent.com", Map.of("key", List.of("test/.*")));
        authorizationRuleService.parseSslAuthorizationRule(sslMqttCredentials, "123456789");
    }

    /*
        parseBasicAuthorizationRule tests
     */
    @Test
    public void testSuccessfulCredentialsParse_Basic() throws AuthenticationException {
        BasicMqttCredentials basicMqttCredentials = new BasicMqttCredentials("test", "test", null, "test/.*");
        AuthorizationRule authorizationRule = authorizationRuleService.parseBasicAuthorizationRule(basicMqttCredentials);
        Assert.assertTrue(authorizationRule.getPatterns().stream().map(Pattern::pattern).collect(Collectors.toList()).contains("test/.*"));
    }

    /*
            validateAuthorizationRule tests
    */
    @Test
    public void testSuccessfulRuleValidation() {
        List<AuthorizationRule> authorizationRules = List.of(
                new AuthorizationRule(List.of(Pattern.compile("1/.*"))),
                new AuthorizationRule(List.of(Pattern.compile("2/.*")))
        );
        Assert.assertTrue(authorizationRuleService.isAuthorized("1/", authorizationRules));
        Assert.assertTrue(authorizationRuleService.isAuthorized("1/123", authorizationRules));
        Assert.assertTrue(authorizationRuleService.isAuthorized("2/", authorizationRules));
        Assert.assertTrue(authorizationRuleService.isAuthorized("2/123", authorizationRules));

        Assert.assertFalse(authorizationRuleService.isAuthorized("3/123", authorizationRules));
    }

    @Test
    public void testSuccessfulRuleValidation_ruleIntersection() {
        List<AuthorizationRule> authorizationRules = List.of(
                new AuthorizationRule(List.of(Pattern.compile(".*"))),
                new AuthorizationRule(List.of(Pattern.compile("1/.*"))),
                new AuthorizationRule(List.of(Pattern.compile("2/.*")))
        );
        Assert.assertTrue(authorizationRuleService.isAuthorized("1/123", authorizationRules));
    }

    @Test
    public void testSuccessfulRuleValidation_NoRule() {
        Assert.assertFalse(authorizationRuleService.isAuthorized("123", Collections.emptyList()));
    }
}
