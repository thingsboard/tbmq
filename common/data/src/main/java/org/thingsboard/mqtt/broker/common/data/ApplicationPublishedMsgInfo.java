package org.thingsboard.mqtt.broker.common.data;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@EqualsAndHashCode
@NoArgsConstructor
public class ApplicationPublishedMsgInfo {
    private Long offset;
    private int packetId;
}
