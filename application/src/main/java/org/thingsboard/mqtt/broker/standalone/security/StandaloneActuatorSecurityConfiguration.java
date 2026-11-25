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
package org.thingsboard.mqtt.broker.standalone.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;

/**
 * The actuator of the Lite edition: health and the links page are open, every other endpoint (info, prometheus, ...)
 * needs {@code Authorization: Bearer <token>} for {@code standalone.security.metrics-token}. With no token set those
 * endpoints answer 401 whatever {@code METRICS_ENDPOINTS_EXPOSE} lists.
 * <p>
 * The chain is ordered before the shared REST chain, which matches every request and so has to stay last.
 */
@Slf4j
@Configuration
@StandaloneComponent
public class StandaloneActuatorSecurityConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain actuatorFilterChain(HttpSecurity http,
                                            @Value("${standalone.security.metrics-token:}") String metricsToken) throws Exception {
        MetricsTokenAuthorizationManager tokenManager = new MetricsTokenAuthorizationManager(metricsToken);
        if (metricsToken == null || metricsToken.isBlank()) {
            log.info("No metrics token is set (TBMQ_METRICS_TOKEN): every actuator endpoint is refused, except health and the links page");
        }
        http.securityMatcher(EndpointRequest.toAnyEndpoint())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(EndpointRequest.toLinks(), EndpointRequest.to(HealthEndpoint.class)).permitAll()
                        .anyRequest().access(tokenManager));
        return http.build();
    }

}
