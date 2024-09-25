/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ClientTypeSslMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SslMqttCredentials;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class AuthRulePatternsServiceSuiteTest {

    private static final String CLIENT_ID = "clientId";

    private DefaultAuthorizationRuleService authorizationRuleService;

    @Before
    public void init() {
        this.authorizationRuleService = new DefaultAuthorizationRuleService();
    }

    /**
     * parseSslAuthorizationRule tests
     */

    @Test
    public void testSuccessfulCredentialsParse_Ssl1() throws AuthenticationException {
        SslMqttCredentials sslMqttCredentials = SslMqttCredentials.newInstance("parent.com", ".*abc-123.*", List.of("test/.*"));
        ClientTypeSslMqttCredentials credentials = newClientTypeSslMqttCredentials(sslMqttCredentials);
        List<AuthRulePatterns> authRulePatterns = authorizationRuleService.parseSslAuthorizationRule(credentials, "123456abc-1234321.ab.abc");
        Assert.assertEquals(1, authRulePatterns.size());
        Assert.assertEquals("test/.*", authRulePatterns.get(0).getPubPatterns().get(0).pattern());
        Assert.assertEquals("test/.*", authRulePatterns.get(0).getSubPatterns().get(0).pattern());
    }

    @Test
    public void testSuccessfulCredentialsParse_Ssl2() throws AuthenticationException {
        SslMqttCredentials sslMqttCredentials = new SslMqttCredentials("parent.com", Map.of(
                ".*abc-123.*", new PubSubAuthorizationRules(List.of("test1/.*"), List.of("test2/.*"))
        ));
        ClientTypeSslMqttCredentials credentials = newClientTypeSslMqttCredentials(sslMqttCredentials);
        List<AuthRulePatterns> authRulePatterns = authorizationRuleService.parseSslAuthorizationRule(credentials, "123456abc-1234321.ab.abc");
        Assert.assertEquals(1, authRulePatterns.size());
        Assert.assertEquals("test1/.*", authRulePatterns.get(0).getPubPatterns().get(0).pattern());
        Assert.assertEquals("test2/.*", authRulePatterns.get(0).getSubPatterns().get(0).pattern());
    }

    @Test
    public void testSuccessfulCredentialsParse_Ssl3() throws AuthenticationException {
        SslMqttCredentials sslMqttCredentials = new SslMqttCredentials("parent.com", Map.of(
                ".*", new PubSubAuthorizationRules(List.of("all/.*"), List.of("all/.*"))
        ));
        ClientTypeSslMqttCredentials credentials = newClientTypeSslMqttCredentials(sslMqttCredentials);
        List<AuthRulePatterns> authRulePatterns = authorizationRuleService.parseSslAuthorizationRule(credentials, "123456abc-1234321.ab.abc");
        Assert.assertEquals(1, authRulePatterns.size());
        Assert.assertEquals("all/.*", authRulePatterns.get(0).getPubPatterns().get(0).pattern());
        Assert.assertEquals("all/.*", authRulePatterns.get(0).getSubPatterns().get(0).pattern());

        List<AuthRulePatterns> authRulePatterns1 = authorizationRuleService.parseSslAuthorizationRule(credentials, "test.test-12345678999.qwerty");
        Assert.assertEquals(1, authRulePatterns1.size());
        Assert.assertEquals("all/.*", authRulePatterns1.get(0).getPubPatterns().get(0).pattern());
        Assert.assertEquals("all/.*", authRulePatterns1.get(0).getSubPatterns().get(0).pattern());
    }

    @Test
    public void testSuccessfulCredentialsParse_Ssl_MultiplePossibleKeys() throws AuthenticationException {
        SslMqttCredentials sslMqttCredentials = new SslMqttCredentials(
                "parent.com",
                Map.of(
                        ".*abc-p01.*", PubSubAuthorizationRules.newInstance(List.of("1/.*", "5/.*")),
                        ".*qwer1234.*", PubSubAuthorizationRules.newInstance(List.of("2/.*")),
                        ".*4321.*", PubSubAuthorizationRules.newInstance(List.of("3/.*")),
                        ".*nonexistent.*", PubSubAuthorizationRules.newInstance(List.of("4/.*"))
                )
        );
        ClientTypeSslMqttCredentials credentials = newClientTypeSslMqttCredentials(sslMqttCredentials);
        List<AuthRulePatterns> authRulePatterns = authorizationRuleService.parseSslAuthorizationRule(credentials, "qwer1234-abc-p01.4321.ab.abc");
        Set<String> patterns = authRulePatterns.stream()
                .map(AuthRulePatterns::getPubPatterns).toList()
                .stream().flatMap(List::stream)
                .map(Pattern::pattern)
                .collect(Collectors.toSet());
        Assert.assertEquals(4, patterns.size());
        Assert.assertEquals(Set.of("1/.*", "5/.*", "2/.*", "3/.*"), patterns);
    }

    @Test(expected = AuthenticationException.class)
    public void testEmptyRules() throws AuthenticationException {
        SslMqttCredentials sslMqttCredentials = new SslMqttCredentials("parent.com", Map.of());
        ClientTypeSslMqttCredentials credentials = newClientTypeSslMqttCredentials(sslMqttCredentials);
        authorizationRuleService.parseSslAuthorizationRule(credentials, "123456789");
    }

    @Test(expected = AuthenticationException.class)
    public void testPatternDontMatch_Ssl() throws AuthenticationException {
        SslMqttCredentials sslMqttCredentials = SslMqttCredentials.newInstance("parent.com", "key", List.of("test/.*"));
        ClientTypeSslMqttCredentials credentials = newClientTypeSslMqttCredentials(sslMqttCredentials);
        authorizationRuleService.parseSslAuthorizationRule(credentials, "123456789");
    }

    /**
     * parseBasicAuthorizationRule tests
     */

    @Test
    public void testSuccessfulCredentialsParse_Basic1() throws AuthenticationException {
        BasicMqttCredentials basicMqttCredentials = BasicMqttCredentials.newInstance("test", "test", null, List.of("test/.*"));
        AuthRulePatterns authRulePatterns = authorizationRuleService.parseAuthorizationRule(basicMqttCredentials);
        Assert.assertTrue(authRulePatterns.getPubPatterns().stream().map(Pattern::pattern).toList().contains("test/.*"));
        Assert.assertTrue(authRulePatterns.getSubPatterns().stream().map(Pattern::pattern).toList().contains("test/.*"));
    }

    @Test
    public void testSuccessfulCredentialsParse_Basic2() throws AuthenticationException {
        BasicMqttCredentials basicMqttCredentials = new BasicMqttCredentials("test", "test", null, new PubSubAuthorizationRules(
                List.of("test1/.*"), List.of("test2/.*")
        ));
        AuthRulePatterns authRulePatterns = authorizationRuleService.parseAuthorizationRule(basicMqttCredentials);
        Assert.assertTrue(authRulePatterns.getPubPatterns().stream().map(Pattern::pattern).toList().contains("test1/.*"));
        Assert.assertTrue(authRulePatterns.getSubPatterns().stream().map(Pattern::pattern).toList().contains("test2/.*"));
    }

    @Test
    public void testSuccessfulRuleValidation1() {
        List<AuthRulePatterns> authRulePatterns = List.of(
                AuthRulePatterns.newInstance(List.of(Pattern.compile("1/.*"))),
                AuthRulePatterns.newInstance(List.of(Pattern.compile("2/.*")))
        );
        Assert.assertTrue(authorizationRuleService.isPubAuthorized(CLIENT_ID, "1/", authRulePatterns));
        Assert.assertTrue(authorizationRuleService.isSubAuthorized("1/123", authRulePatterns));
        Assert.assertTrue(authorizationRuleService.isPubAuthorized(CLIENT_ID, "2/", authRulePatterns));
        Assert.assertTrue(authorizationRuleService.isSubAuthorized("2/123", authRulePatterns));

        Assert.assertFalse(authorizationRuleService.isPubAuthorized(CLIENT_ID, "3/123", authRulePatterns));
    }

    @Test
    public void testSuccessfulRuleValidation2() {
        List<AuthRulePatterns> authRulePatterns = List.of(
                AuthRulePatterns.newInstance(List.of(Pattern.compile("1/.*"))),
                AuthRulePatterns.newInstance(Collections.emptyList())
        );
        Assert.assertTrue(authorizationRuleService.isPubAuthorized(CLIENT_ID, "1/", authRulePatterns));
        Assert.assertTrue(authorizationRuleService.isSubAuthorized("1/123", authRulePatterns));
        Assert.assertFalse(authorizationRuleService.isPubAuthorized(CLIENT_ID, "2/", authRulePatterns));
        Assert.assertFalse(authorizationRuleService.isSubAuthorized("2/123", authRulePatterns));

        Assert.assertFalse(authorizationRuleService.isPubAuthorized(CLIENT_ID, "3/123", authRulePatterns));
    }

    @Test
    public void testSuccessfulRuleValidation3() {
        List<AuthRulePatterns> authRulePatterns = List.of(
                AuthRulePatterns.newInstance(Collections.emptyList()),
                AuthRulePatterns.newInstance(Collections.emptyList())
        );
        Assert.assertFalse(authorizationRuleService.isPubAuthorized(CLIENT_ID, "1/", authRulePatterns));
        Assert.assertFalse(authorizationRuleService.isSubAuthorized("1/123", authRulePatterns));
        Assert.assertFalse(authorizationRuleService.isPubAuthorized(CLIENT_ID, "2/", authRulePatterns));
        Assert.assertFalse(authorizationRuleService.isSubAuthorized("2/123", authRulePatterns));
        Assert.assertFalse(authorizationRuleService.isPubAuthorized(CLIENT_ID, "3/123", authRulePatterns));
    }

    @Test
    public void testSuccessfulRuleValidation4() {
        List<AuthRulePatterns> authRulePatterns = List.of(
                new AuthRulePatterns(List.of(Pattern.compile("2/.*")), List.of(Pattern.compile("1/.*"))),
                AuthRulePatterns.newInstance(Collections.emptyList())
        );
        Assert.assertFalse(authorizationRuleService.isPubAuthorized(CLIENT_ID, "1/", authRulePatterns));
        Assert.assertTrue(authorizationRuleService.isSubAuthorized("1/123", authRulePatterns));
        Assert.assertTrue(authorizationRuleService.isPubAuthorized(CLIENT_ID, "2/", authRulePatterns));
        Assert.assertFalse(authorizationRuleService.isSubAuthorized("2/123", authRulePatterns));

        Assert.assertFalse(authorizationRuleService.isPubAuthorized(CLIENT_ID, "3/123", authRulePatterns));
    }

    @Test
    public void testSuccessfulRuleValidation5() {
        boolean pubAuthorized = authorizationRuleService.isPubAuthorized("cl1", "tp1", null);
        Assert.assertTrue(pubAuthorized);
        pubAuthorized = authorizationRuleService.isPubAuthorized("cl1", "tp1", List.of());
        Assert.assertTrue(pubAuthorized);
    }

    @Test
    public void testSuccessfulRuleValidation_ruleIntersection() {
        List<AuthRulePatterns> authRulePatterns = List.of(
                AuthRulePatterns.newInstance(List.of(Pattern.compile(".*"))),
                AuthRulePatterns.newInstance(List.of(Pattern.compile("1/.*"))),
                AuthRulePatterns.newInstance(List.of(Pattern.compile("2/.*")))
        );
        Assert.assertTrue(authorizationRuleService.isSubAuthorized("1/123", authRulePatterns));
    }

    @Test
    public void testSuccessfulRuleValidation_NoRule() {
        Assert.assertFalse(authorizationRuleService.isSubAuthorized("123", Collections.emptyList()));
    }

    @Test
    public void testPubAuthAndEvict() {
        List<AuthRulePatterns> authRulePatterns = List.of(
                AuthRulePatterns.newInstance(List.of(Pattern.compile(".*")))
        );
        Assert.assertTrue(authorizationRuleService.isPubAuthorized(CLIENT_ID, "1/", authRulePatterns));

        Assert.assertEquals(1, authorizationRuleService.getPublishAuthMap().size());
        Assert.assertTrue(authorizationRuleService.getPublishAuthMap().get(CLIENT_ID).get("1/"));

        authorizationRuleService.evict(CLIENT_ID);

        Assert.assertEquals(0, authorizationRuleService.getPublishAuthMap().size());
    }

    private ClientTypeSslMqttCredentials newClientTypeSslMqttCredentials(SslMqttCredentials sslMqttCredentials) {
        return new ClientTypeSslMqttCredentials(ClientType.DEVICE, sslMqttCredentials, "credentialsName");
    }
}
