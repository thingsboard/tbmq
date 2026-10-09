/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2026 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package org.thingsboard.mqtt.broker.service.mail;

import freemarker.template.Configuration;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.common.data.queue.ServiceType;
import org.thingsboard.mqtt.broker.config.ThingsboardMessageConfiguration;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.service.system.ResourceUsageAlert;
import org.thingsboard.mqtt.broker.service.system.UsageLevel;

import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.CPU_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DISK_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.MEMORY_USAGE;

class DefaultMailServiceTest {

    private final MessageSource messages = new ThingsboardMessageConfiguration().messageSource();
    private final JavaMailSenderImpl mailSender = mock(JavaMailSenderImpl.class);
    private final MailExecutorService mailExecutorService = new MailExecutorService();
    private DefaultMailService mailService;

    @BeforeEach
    void setUp() {
        Configuration freemarkerConfig = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        freemarkerConfig.setClassForTemplateLoading(DefaultMailService.class, "/templates");
        ReflectionTestUtils.setField(mailExecutorService, "mailExecutorThreadPoolSize", 1);
        mailExecutorService.init();
        mailService = new DefaultMailService(messages, freemarkerConfig, mock(AdminSettingsService.class),
                mailExecutorService, mock(PasswordResetExecutorService.class));
        ReflectionTestUtils.setField(mailService, "mailSender", mailSender);
        ReflectionTestUtils.setField(mailService, "mailFrom", "tbmq@example.com");
        ReflectionTestUtils.setField(mailService, "timeout", 5_000L);
        when(mailSender.createMimeMessage()).thenAnswer(__ -> new MimeMessage((Session) null));
    }

    @AfterEach
    void tearDown() {
        mailExecutorService.destroy();
    }

    @Test
    void givenBrokerAlertWithLink_whenSent_thenSubjectAndBodyDescribeIt() throws Exception {
        ResourceUsageAlert alert = new ResourceUsageAlert("tbmq1", ServiceType.TBMQ, CPU_USAGE, 91L, UsageLevel.CRITICAL, 85);

        mailService.sendResourceUsageAlertEmail(alert, "http://tbmq.local/monitoring/cluster?nodeId=tbmq1", "ops@example.com");

        MimeMessage message = sentMessage();
        assertThat(message.getSubject()).isEqualTo("TBMQ - CPU usage is critical on tbmq1 (91%)");
        assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly("ops@example.com");
        assertThat(message.getContent().toString())
                .contains("CPU usage is critical on tbmq1</h2>")
                .contains("CPU usage on the TBMQ node <b>tbmq1</b> is <b>91%</b>, which is above the critical threshold of 85%.")
                .contains("href=\"http://tbmq.local/monitoring/cluster?nodeId=tbmq1\"")
                .doesNotContain("Open Monitoring &rarr; Cluster");
    }

    @Test
    void givenExecutorAlertWithoutLink_whenSent_thenBodyNamesTheTypeAndPointsAtTheClusterPage() throws Exception {
        ResourceUsageAlert alert = new ResourceUsageAlert("ie1", ServiceType.TBMQ_INTEGRATION_EXECUTOR, MEMORY_USAGE, 80L, UsageLevel.HIGH, 75);

        mailService.sendResourceUsageAlertEmail(alert, null, "ops@example.com");

        MimeMessage message = sentMessage();
        assertThat(message.getSubject()).isEqualTo("TBMQ - Memory usage is high on ie1 (80%)");
        assertThat(message.getContent().toString())
                .contains("Memory usage on the TBMQ Integration Executor node <b>ie1</b> is <b>80%</b>")
                .contains("Open Monitoring &rarr; Cluster")
                .doesNotContain("btn-primary");
    }

    @Test
    void givenEveryUsageMetric_whenNamed_thenItHasAMessage() {
        Stream.of(CPU_USAGE, MEMORY_USAGE, DISK_USAGE).forEach(metric -> assertThatCode(
                () -> messages.getMessage("resource.usage.metric." + metric, null, Locale.US)).doesNotThrowAnyException());
    }

    private MimeMessage sentMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

}
