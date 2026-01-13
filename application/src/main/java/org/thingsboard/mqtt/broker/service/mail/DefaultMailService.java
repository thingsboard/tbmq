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
package org.thingsboard.mqtt.broker.service.mail;

import com.fasterxml.jackson.databind.JsonNode;
import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.context.MessageSource;
import org.springframework.core.NestedRuntimeException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.SysAdminSettingType;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.dao.exception.IncorrectParameterException;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultMailService implements MailService {

    private static final String MAIL_PROP = "mail.";
    private static final String TARGET_EMAIL = "targetEmail";
    private static final String UTF_8 = "UTF-8";
    private static final long DEFAULT_TIMEOUT = 10_000;

    private final MessageSource messages;
    private final Configuration freemarkerConfig;
    private final AdminSettingsService adminSettingsService;
    private final MailExecutorService mailExecutorService;
    private final PasswordResetExecutorService passwordResetExecutorService;

    private JavaMailSenderImpl mailSender;

    private String mailFrom;
    private long timeout;

    @PostConstruct
    private void init() {
        updateMailConfiguration();
        freemarkerConfig.setClassForTemplateLoading(this.getClass(), "/templates");
    }

    @Override
    public void updateMailConfiguration() {
        AdminSettings settings = adminSettingsService.findAdminSettingsByKey(SysAdminSettingType.MAIL.getKey());
        if (settings != null) {
            JsonNode jsonConfig = settings.getJsonValue();
            mailSender = createMailSender(jsonConfig);
            mailFrom = jsonConfig.get("mailFrom").asText();
            timeout = jsonConfig.get("timeout").asLong(DEFAULT_TIMEOUT);
        } else {
            throw new IncorrectParameterException("Failed to update mail configuration. Settings not found!");
        }
    }

    @Override
    public void sendResetPasswordEmail(String passwordResetLink, String email) throws ThingsboardException {

        String subject = messages.getMessage("reset.password.subject", null, Locale.US);
        Map<String, Object> model = new HashMap<>();
        model.put("passwordResetLink", passwordResetLink);
        model.put(TARGET_EMAIL, email);

        String message = mergeTemplateIntoString("tbmq.reset.password.ftl", model);

        sendMail(mailSender, mailFrom, email, subject, message, timeout);
    }

    @Override
    public void sendResetPasswordEmailAsync(String passwordResetLink, String email) {
        passwordResetExecutorService.execute(() -> {
            try {
                this.sendResetPasswordEmail(passwordResetLink, email);
            } catch (ThingsboardException e) {
                log.error("Error occurred: ", e);
            }
        });
    }

    @Override
    public void sendPasswordWasResetEmail(String loginLink, String email) throws ThingsboardException {

        String subject = messages.getMessage("password.was.reset.subject", null, Locale.US);

        Map<String, Object> model = new HashMap<>();
        model.put("loginLink", loginLink);
        model.put(TARGET_EMAIL, email);

        String message = mergeTemplateIntoString("tbmq.password.was.reset.ftl", model);

        sendMail(mailSender, mailFrom, email, subject, message, timeout);
    }

    @Override
    public void sendEmail(String email, String subject, String message) throws ThingsboardException {
        sendMail(mailSender, mailFrom, email, subject, message, timeout);
    }

    @Override
    public void sendTestMail(JsonNode jsonConfig, String email) throws ThingsboardException {
        JavaMailSenderImpl testMailSender = createMailSender(jsonConfig);
        String mailFrom = jsonConfig.get("mailFrom").asText();
        String subject = messages.getMessage("test.message.subject", null, Locale.US);
        long timeout = jsonConfig.get("timeout").asLong(DEFAULT_TIMEOUT);

        Map<String, Object> model = new HashMap<>();
        model.put(TARGET_EMAIL, email);

        String message = mergeTemplateIntoString("tbmq.test.ftl", model);

        sendMail(testMailSender, mailFrom, email, subject, message, timeout);
    }

    private String mergeTemplateIntoString(String templateLocation,
                                           Map<String, Object> model) throws ThingsboardException {
        try {
            Template template = freemarkerConfig.getTemplate(templateLocation);
            return FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private JavaMailSenderImpl createMailSender(JsonNode jsonConfig) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(jsonConfig.get("smtpHost").asText());
        mailSender.setPort(parsePort(jsonConfig.get("smtpPort").asText()));
        mailSender.setUsername(jsonConfig.get("username").asText());
        mailSender.setPassword(jsonConfig.get("password").asText());
        mailSender.setJavaMailProperties(createJavaMailProperties(jsonConfig));
        return mailSender;
    }

    private Properties createJavaMailProperties(JsonNode jsonConfig) {
        Properties javaMailProperties = new Properties();
        String protocol = jsonConfig.get("smtpProtocol").asText();
        javaMailProperties.put("mail.transport.protocol", protocol);
        javaMailProperties.put(MAIL_PROP + protocol + ".host", jsonConfig.get("smtpHost").asText());
        javaMailProperties.put(MAIL_PROP + protocol + ".port", jsonConfig.get("smtpPort").asText());
        javaMailProperties.put(MAIL_PROP + protocol + ".timeout", jsonConfig.get("timeout").asText());
        javaMailProperties.put(MAIL_PROP + protocol + ".auth", String.valueOf(StringUtils.isNotEmpty(jsonConfig.get("username").asText())));
        boolean enableTls = false;
        if (jsonConfig.has("enableTls")) {
            if (jsonConfig.get("enableTls").isBoolean() && jsonConfig.get("enableTls").booleanValue()) {
                enableTls = true;
            } else if (jsonConfig.get("enableTls").isTextual()) {
                enableTls = "true".equalsIgnoreCase(jsonConfig.get("enableTls").asText());
            }
        }
        javaMailProperties.put(MAIL_PROP + protocol + ".starttls.enable", enableTls);
        if (enableTls && jsonConfig.has("tlsVersion") && !jsonConfig.get("tlsVersion").isNull()) {
            String tlsVersion = jsonConfig.get("tlsVersion").asText();
            if (StringUtils.isNoneEmpty(tlsVersion)) {
                javaMailProperties.put(MAIL_PROP + protocol + ".ssl.protocols", tlsVersion);
            }
        }

        boolean enableProxy = jsonConfig.has("enableProxy") && jsonConfig.get("enableProxy").asBoolean();

        if (enableProxy) {
            javaMailProperties.put(MAIL_PROP + protocol + ".proxy.host", jsonConfig.get("proxyHost").asText());
            javaMailProperties.put(MAIL_PROP + protocol + ".proxy.port", jsonConfig.get("proxyPort").asText());
            String proxyUser = jsonConfig.get("proxyUser").asText();
            if (StringUtils.isNoneEmpty(proxyUser)) {
                javaMailProperties.put(MAIL_PROP + protocol + ".proxy.user", proxyUser);
            }
            String proxyPassword = jsonConfig.get("proxyPassword").asText();
            if (StringUtils.isNoneEmpty(proxyPassword)) {
                javaMailProperties.put(MAIL_PROP + protocol + ".proxy.password", proxyPassword);
            }
        }
        return javaMailProperties;
    }

    private int parsePort(String strPort) {
        try {
            return Integer.parseInt(strPort);
        } catch (NumberFormatException e) {
            throw new IncorrectParameterException(String.format("Invalid smtp port value: %s", strPort));
        }
    }

    private void sendMail(JavaMailSenderImpl mailSender, String mailFrom, String email,
                          String subject, String message, long timeout) throws ThingsboardException {
        try {
            MimeMessage mimeMsg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMsg, UTF_8);
            helper.setFrom(mailFrom);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(message, true);

            sendMailWithTimeout(mailSender, helper.getMimeMessage(), timeout);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private void sendMailWithTimeout(JavaMailSender mailSender, MimeMessage msg, long timeout) {
        try {
            mailExecutorService.submit(() -> mailSender.send(msg)).get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.debug("Error during mail submission", e);
            throw new RuntimeException("Timeout!");
        } catch (Exception e) {
            throw new RuntimeException(ExceptionUtils.getRootCause(e));
        }
    }

    protected ThingsboardException handleException(Exception exception) {
        String message;
        if (exception instanceof NestedRuntimeException) {
            message = ((NestedRuntimeException) exception).getMostSpecificCause().getMessage();
        } else {
            message = exception.getMessage();
        }
        log.warn("Unable to send mail!", exception);
        return new ThingsboardException(String.format("Unable to send mail: %s", message), ThingsboardErrorCode.GENERAL);
    }
}
