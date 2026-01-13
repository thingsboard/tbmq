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
package org.thingsboard.mqtt.broker.integration.service.integration.http;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.util.UrlUtils;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TbHttpClientTest {

    EventLoopGroup eventLoop;
    TbHttpClient client;

    @BeforeEach
    public void setUp() throws Exception {
        client = mock(TbHttpClient.class);
        when(client.getSharedOrCreateEventLoopGroup(any())).thenCallRealMethod();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (eventLoop != null) {
            eventLoop.shutdownGracefully();
        }
    }

    @Test
    public void givenSharedEventLoop_whenGetEventLoop_ThenReturnShared() {
        eventLoop = mock(EventLoopGroup.class);
        assertThat(client.getSharedOrCreateEventLoopGroup(eventLoop), is(eventLoop));
    }

    @Test
    public void givenNull_whenGetEventLoop_ThenReturnShared() {
        eventLoop = client.getSharedOrCreateEventLoopGroup(null);
        assertThat(eventLoop, instanceOf(NioEventLoopGroup.class));
    }

    @Test
    public void testBuildSimpleUri() {
        String url = "http://localhost:8080/";
        URI uri = UrlUtils.buildEncodedUri(url);
        Assertions.assertEquals(url, uri.toString());
    }

    @Test
    public void testBuildUriWithoutProtocol() {
        String url = "localhost:8080/";
        assertThatThrownBy(() -> UrlUtils.buildEncodedUri(url));
    }

    @Test
    public void testBuildInvalidUri() {
        String url = "aaa";
        assertThatThrownBy(() -> UrlUtils.buildEncodedUri(url));
    }

    @Test
    public void testBuildUriWithSpecialSymbols() {
        String url = "http://192.168.1.1/data?d={\"a\": 12}";
        String expected = "http://192.168.1.1/data?d=%7B%22a%22:%2012%7D";
        URI uri = UrlUtils.buildEncodedUri(url);
        Assertions.assertEquals(expected, uri.toString());
    }

}
