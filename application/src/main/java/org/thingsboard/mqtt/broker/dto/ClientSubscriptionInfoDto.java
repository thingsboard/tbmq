package org.thingsboard.mqtt.broker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;

import java.util.Comparator;
import java.util.function.Function;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientSubscriptionInfoDto {

    private String clientId;
    private SubscriptionInfoDto subscription;

    public static Comparator<ClientSubscriptionInfoDto> getComparator(SortOrder sortOrder) {
        return switch (sortOrder.getProperty()) {
            case "clientId" -> getStrComparator(sortOrder.getDirection(), ClientSubscriptionInfoDto::getClientId);
            case "topicFilter" ->
                    getStrComparator(sortOrder.getDirection(), csi -> csi.getSubscription().getTopicFilter());
            case "qos" -> getIntComparator(sortOrder.getDirection(), csi -> csi.getSubscription().getQos().value());
            case "noLocal" ->
                    getBoolComparator(sortOrder.getDirection(), csi -> csi.getSubscription().getOptions().isNoLocal());
            case "retainAsPublish" ->
                    getBoolComparator(sortOrder.getDirection(), csi -> csi.getSubscription().getOptions().isRetainAsPublish());
            case "retainHandling" ->
                    getIntComparator(sortOrder.getDirection(), csi -> csi.getSubscription().getOptions().getRetainHandling());
            case "subscriptionId" ->
                    getIntComparator(sortOrder.getDirection(), csi -> csi.getSubscription().getSubscriptionId());
            default -> null;
        };
    }

    private static Comparator<ClientSubscriptionInfoDto> getStrComparator(SortOrder.Direction direction,
                                                                          Function<ClientSubscriptionInfoDto, String> func) {
        if (direction == SortOrder.Direction.DESC) {
            return Comparator.comparing(func, Comparator.reverseOrder());
        } else {
            return Comparator.comparing(func);
        }
    }

    private static Comparator<ClientSubscriptionInfoDto> getIntComparator(SortOrder.Direction direction,
                                                                          Function<ClientSubscriptionInfoDto, Integer> func) {
        if (direction == SortOrder.Direction.DESC) {
            return Comparator.comparing(func, Comparator.reverseOrder());
        } else {
            return Comparator.comparing(func);
        }
    }

    private static Comparator<ClientSubscriptionInfoDto> getBoolComparator(SortOrder.Direction direction,
                                                                           Function<ClientSubscriptionInfoDto, Boolean> func) {
        if (direction == SortOrder.Direction.DESC) {
            return Comparator.comparing(func, Comparator.reverseOrder());
        } else {
            return Comparator.comparing(func);
        }
    }
}
