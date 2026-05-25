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
package org.thingsboard.mqtt.broker.service.mqtt.sparkplug;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SparkplugTopicUtilTest {

    @Test
    public void givenNbirthTopic_whenToCertificateTopic_thenReturnsCertificateTopic() {
        String result = SparkplugTopicUtil.toCertificateTopic("spBv1.0/G1/NBIRTH/E1");

        assertThat(result).isEqualTo("$sparkplug/certificates/spBv1.0/G1/NBIRTH/E1");
    }

    @Test
    public void givenDbirthTopic_whenToCertificateTopic_thenReturnsCertificateTopic() {
        String result = SparkplugTopicUtil.toCertificateTopic("spBv1.0/G1/DBIRTH/E1/D1");

        assertThat(result).isEqualTo("$sparkplug/certificates/spBv1.0/G1/DBIRTH/E1/D1");
    }

    @Test
    public void givenNdataTopic_whenToCertificateTopic_thenReturnsNull() {
        assertThat(SparkplugTopicUtil.toCertificateTopic("spBv1.0/G1/NDATA/E1")).isNull();
    }

    @Test
    public void givenDdataTopic_whenToCertificateTopic_thenReturnsNull() {
        assertThat(SparkplugTopicUtil.toCertificateTopic("spBv1.0/G1/DDATA/E1/D1")).isNull();
    }

    @Test
    public void givenNcmdTopic_whenToCertificateTopic_thenReturnsNull() {
        assertThat(SparkplugTopicUtil.toCertificateTopic("spBv1.0/G1/NCMD/E1")).isNull();
    }

    @Test
    public void givenDcmdTopic_whenToCertificateTopic_thenReturnsNull() {
        assertThat(SparkplugTopicUtil.toCertificateTopic("spBv1.0/G1/DCMD/E1/D1")).isNull();
    }

    @Test
    public void givenNdeathTopic_whenToCertificateTopic_thenReturnsNull() {
        assertThat(SparkplugTopicUtil.toCertificateTopic("spBv1.0/G1/NDEATH/E1")).isNull();
    }

    @Test
    public void givenDdeathTopic_whenToCertificateTopic_thenReturnsNull() {
        assertThat(SparkplugTopicUtil.toCertificateTopic("spBv1.0/G1/DDEATH/E1/D1")).isNull();
    }

    @Test
    public void givenStateTopic_whenToCertificateTopic_thenReturnsNull() {
        assertThat(SparkplugTopicUtil.toCertificateTopic("spBv1.0/STATE/my_primary_host")).isNull();
    }

    @Test
    public void givenNonSparkplugTopic_whenToCertificateTopic_thenReturnsNull() {
        assertThat(SparkplugTopicUtil.toCertificateTopic("sensors/temperature")).isNull();
        assertThat(SparkplugTopicUtil.toCertificateTopic("my/G1/NBIRTH/E1")).isNull();
    }

    @Test
    public void givenAlreadyCertificateNbirthTopic_whenToCertificateTopic_thenReturnsNullToPreventLoop() {
        assertThat(SparkplugTopicUtil.toCertificateTopic("$sparkplug/certificates/spBv1.0/G1/NBIRTH/E1")).isNull();
    }

    @Test
    public void givenAlreadyCertificateDbirthTopic_whenToCertificateTopic_thenReturnsNullToPreventLoop() {
        assertThat(SparkplugTopicUtil.toCertificateTopic("$sparkplug/certificates/spBv1.0/G1/DBIRTH/E1/D1")).isNull();
    }

    @Test
    public void givenNbirthTopicWithWildcard_whenToCertificateTopic_thenReturnsNull() {
        assertThat(SparkplugTopicUtil.toCertificateTopic("spBv1.0/+/NBIRTH/E1")).isNull();
        assertThat(SparkplugTopicUtil.toCertificateTopic("spBv1.0/G1/NBIRTH/#")).isNull();
    }

    @Test
    public void givenNbirthTopicWithMissingSegments_whenToCertificateTopic_thenReturnsNull() {
        assertThat(SparkplugTopicUtil.toCertificateTopic("spBv1.0/G1/NBIRTH")).isNull();
        assertThat(SparkplugTopicUtil.toCertificateTopic("spBv1.0/G1/NBIRTH/E1/extra")).isNull();
    }

    @Test
    public void givenDbirthTopicWithMissingSegments_whenToCertificateTopic_thenReturnsNull() {
        assertThat(SparkplugTopicUtil.toCertificateTopic("spBv1.0/G1/DBIRTH/E1")).isNull();
        assertThat(SparkplugTopicUtil.toCertificateTopic("spBv1.0/G1/DBIRTH/E1/D1/extra")).isNull();
    }

    @Test
    public void givenNbirthTopicWithEmptySegment_whenToCertificateTopic_thenReturnsNull() {
        assertThat(SparkplugTopicUtil.toCertificateTopic("spBv1.0//NBIRTH/E1")).isNull();
        assertThat(SparkplugTopicUtil.toCertificateTopic("spBv1.0/G1/NBIRTH/")).isNull();
    }

    @Test
    public void givenNullTopic_whenToCertificateTopic_thenReturnsNull() {
        assertThat(SparkplugTopicUtil.toCertificateTopic(null)).isNull();
    }

    @Test
    public void givenEmptyTopic_whenToCertificateTopic_thenReturnsNull() {
        assertThat(SparkplugTopicUtil.toCertificateTopic("")).isNull();
    }
}
