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
package org.thingsboard.mqtt.broker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.thingsboard.mqtt.broker.exception.ThingsboardErrorResponseHandler;
import org.thingsboard.mqtt.broker.service.security.auth.jwt.JwtAuthenticationProvider;
import org.thingsboard.mqtt.broker.service.security.auth.jwt.JwtTokenAuthenticationProcessingFilter;
import org.thingsboard.mqtt.broker.service.security.auth.jwt.RefreshTokenAuthenticationProvider;
import org.thingsboard.mqtt.broker.service.security.auth.jwt.RefreshTokenProcessingFilter;
import org.thingsboard.mqtt.broker.service.security.auth.jwt.SkipPathRequestMatcher;
import org.thingsboard.mqtt.broker.service.security.auth.jwt.extractor.TokenExtractor;
import org.thingsboard.mqtt.broker.service.security.auth.rest.RestAuthenticationProvider;
import org.thingsboard.mqtt.broker.service.security.auth.rest.RestLoginProcessingFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Order(SecurityProperties.BASIC_AUTH_ORDER)
public class SecurityConfiguration {

    public static final String JWT_TOKEN_HEADER_PARAM = "X-Authorization";
    public static final String JWT_TOKEN_HEADER_PARAM_V2 = "Authorization";
    public static final String FORM_BASED_LOGIN_ENTRY_POINT = "/api/auth/login";
    public static final String TOKEN_REFRESH_ENTRY_POINT = "/api/auth/token";
    protected static final String[] NON_TOKEN_BASED_AUTH_ENTRY_POINTS = new String[]{"/index.html", "/static/**", "/api/noauth/**"};
    public static final String WEBJARS_ENTRY_POINT = "/webjars/**";
    public static final String TOKEN_BASED_AUTH_ENTRY_POINT = "/api/**";

    @Autowired
    private ThingsboardErrorResponseHandler restAccessDeniedHandler;

    @Autowired
    @Qualifier("defaultAuthenticationSuccessHandler")
    private AuthenticationSuccessHandler successHandler;

    @Autowired
    @Qualifier("defaultAuthenticationFailureHandler")
    private AuthenticationFailureHandler failureHandler;

    @Autowired
    private RestAuthenticationProvider restAuthenticationProvider;
    @Autowired
    private JwtAuthenticationProvider jwtAuthenticationProvider;
    @Autowired
    private RefreshTokenAuthenticationProvider refreshTokenAuthenticationProvider;
    @Autowired
    private TokenExtractor tokenExtractor;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Bean
    public AuthenticationManager authenticationManager(ObjectPostProcessor<Object> objectPostProcessor) throws Exception {
        DefaultAuthenticationEventPublisher eventPublisher = objectPostProcessor
                .postProcess(new DefaultAuthenticationEventPublisher());
        var auth = new AuthenticationManagerBuilder(objectPostProcessor);
        auth.authenticationEventPublisher(eventPublisher);
        auth.authenticationProvider(restAuthenticationProvider);
        auth.authenticationProvider(jwtAuthenticationProvider);
        auth.authenticationProvider(refreshTokenAuthenticationProvider);
        return auth.build();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.headers().cacheControl().and().frameOptions().disable()
                .and()
                .cors()
                .and()
                .csrf().disable()
                .exceptionHandling()
                .and()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                .antMatchers(WEBJARS_ENTRY_POINT).permitAll() // Webjars
                .antMatchers(FORM_BASED_LOGIN_ENTRY_POINT).permitAll() // Login end-point
                .antMatchers(TOKEN_REFRESH_ENTRY_POINT).permitAll() // Token refresh end-point
                .antMatchers(NON_TOKEN_BASED_AUTH_ENTRY_POINTS).permitAll() // static resources, user activation and password reset end-points
                .and()
                .authorizeRequests()
                .antMatchers(TOKEN_BASED_AUTH_ENTRY_POINT).authenticated() // Protected API End-points
                .and()
                .exceptionHandling().accessDeniedHandler(restAccessDeniedHandler)
                .and()
                .addFilterBefore(buildRestLoginProcessingFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(buildJwtTokenAuthenticationProcessingFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(buildRefreshTokenProcessingFilter(), UsernamePasswordAuthenticationFilter.class)
        ;
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean(CorsFilter.class)
    public CorsFilter corsFilter(@Autowired MvcCorsProperties mvcCorsProperties) {
        if (mvcCorsProperties.getMappings().size() == 0) {
            return new CorsFilter(new UrlBasedCorsConfigurationSource());
        } else {
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.setCorsConfigurations(mvcCorsProperties.getMappings());
            return new CorsFilter(source);
        }
    }

    @Bean
    protected RestLoginProcessingFilter buildRestLoginProcessingFilter() throws Exception {
        RestLoginProcessingFilter filter = new RestLoginProcessingFilter(FORM_BASED_LOGIN_ENTRY_POINT, successHandler, failureHandler, objectMapper);
        filter.setAuthenticationManager(this.authenticationManager);
        return filter;
    }

    @Bean
    protected JwtTokenAuthenticationProcessingFilter buildJwtTokenAuthenticationProcessingFilter() throws Exception {
        List<String> pathsToSkip = new ArrayList<>(Arrays.asList(NON_TOKEN_BASED_AUTH_ENTRY_POINTS));
        pathsToSkip.addAll(Arrays.asList(TOKEN_REFRESH_ENTRY_POINT, FORM_BASED_LOGIN_ENTRY_POINT, WEBJARS_ENTRY_POINT));
        SkipPathRequestMatcher matcher = new SkipPathRequestMatcher(pathsToSkip, TOKEN_BASED_AUTH_ENTRY_POINT);
        JwtTokenAuthenticationProcessingFilter filter
                = new JwtTokenAuthenticationProcessingFilter(failureHandler, tokenExtractor, matcher);
        filter.setAuthenticationManager(this.authenticationManager);
        return filter;
    }

    @Bean
    protected RefreshTokenProcessingFilter buildRefreshTokenProcessingFilter() throws Exception {
        RefreshTokenProcessingFilter filter = new RefreshTokenProcessingFilter(TOKEN_REFRESH_ENTRY_POINT, successHandler, failureHandler, objectMapper);
        filter.setAuthenticationManager(this.authenticationManager);
        return filter;
    }

}
