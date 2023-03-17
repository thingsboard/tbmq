package org.thingsboard.mqtt.broker.common.data.queue;

public enum KafkaConsumerGroupState {

    STABLE("Stable"),
    COMPLETING_REBALANCE("Completing Rebalance"),
    PREPARING_REBALANCE("Preparing Rebalance"),
    EMPTY("Empty"),
    DEAD("Dead"),
    UNKNOWN("Unknown"),
    ;

    private final String name;

    KafkaConsumerGroupState(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static KafkaConsumerGroupState toState(String consumerGroupState) {
        switch (consumerGroupState) {
            case "":
                return COMPLETING_REBALANCE;
            default:
                return UNKNOWN;
        }
    }

}
