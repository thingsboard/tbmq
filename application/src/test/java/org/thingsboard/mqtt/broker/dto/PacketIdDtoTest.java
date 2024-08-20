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
package org.thingsboard.mqtt.broker.dto;


import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PacketIdDtoTest {

    private static final int MAX_PACKET_ID = 0xffff;
    private PacketIdDto packetIdDto;

    @Before
    public void setUp() {
        packetIdDto = new PacketIdDto(0);
    }

    @Test
    public void testGetNextPacketIdStartWithZero() {
        for (int i = 1; i <= 10; i++) {
            assertThat(packetIdDto.getNextPacketId()).isEqualTo(i);
        }
        assertThat(packetIdDto.getCurrentPacketId()).isEqualTo(10);
    }

    @Test
    public void testGetNextPacketIdMaxValueReached() {
        packetIdDto = new PacketIdDto(MAX_PACKET_ID - 9);  // Start at 0xfff6
        for (int i = 0xfff7; i <= MAX_PACKET_ID; i++) {
            assertThat(packetIdDto.getNextPacketId()).isEqualTo(i);
        }
        assertThat(packetIdDto.getCurrentPacketId()).isEqualTo(MAX_PACKET_ID);
    }

    @Test
    public void testGetNextPacketIdMaxValueExceeded() {
        packetIdDto = new PacketIdDto(MAX_PACKET_ID - 1);  // Start at 0xfffe
        assertThat(packetIdDto.getNextPacketId()).isEqualTo(0xffff); // Max packet ID
        assertThat(packetIdDto.getNextPacketId()).isEqualTo(1);
        assertThat(packetIdDto.getNextPacketId()).isEqualTo(2);
    }

    @Test
    public void testGetNextPacketIdMaxValueExceededStartWithMaxValue() {
        packetIdDto = new PacketIdDto(MAX_PACKET_ID);  // Start at 0xffff
        assertThat(packetIdDto.getNextPacketId()).isEqualTo(1);
        assertThat(packetIdDto.getNextPacketId()).isEqualTo(2);
    }

}
