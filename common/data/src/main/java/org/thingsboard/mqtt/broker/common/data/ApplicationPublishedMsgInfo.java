package org.thingsboard.mqtt.broker.common.data;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@ToString
@EqualsAndHashCode
@NoArgsConstructor
public class ApplicationPublishedMsgInfo {
    private Long offset;
    private int packetId;
}
