package org.thingsboard.mqtt.broker.service.mqtt.client.blocked;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;
import org.thingsboard.mqtt.broker.dto.BlockedClientDto;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientQuery;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientType;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.ClientIdBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.IpAddressBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.RegexBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.RegexMatchTarget;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.UsernameBlockedClient;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class BlockedClientPageServiceImplTest {

    BlockedClientService blockedClientService;
    BlockedClientPageServiceImpl blockedClientPageService;

    @BeforeEach
    public void setUp() {
        blockedClientService = mock(BlockedClientService.class);
        blockedClientPageService = spy(new BlockedClientPageServiceImpl(blockedClientService));
    }

    private Map<BlockedClientType, Map<String, BlockedClient>> getBlockedClientsMap(List<BlockedClient> clients) {
        Map<BlockedClientType, Map<String, BlockedClient>> map = new EnumMap<>(BlockedClientType.class);
        for (BlockedClient client : clients) {
            map.computeIfAbsent(client.getType(), k -> new HashMap<>()).put(client.getKey(), client);
        }
        return map;
    }

    @Test
    void givenBlockedClients_whenGetPaged_thenCorrectPageDataReturned() {
        var blockedClientMap = getBlockedClientsMap(List.of(
                new ClientIdBlockedClient(1000, "desc1", "alpha"),
                new ClientIdBlockedClient(2000, "desc2", "beta"),
                new ClientIdBlockedClient(3000, "desc3", "gamma"),
                new UsernameBlockedClient(4000, "desc4", "user1"),
                new UsernameBlockedClient(5000, "desc5", "adminUser"),
                new IpAddressBlockedClient(6000, "desc6", "192.168.0.1"),
                new IpAddressBlockedClient(7000, "desc7", "10.0.0.42"),
                new RegexBlockedClient(8000, "desc8", "^test_.*", RegexMatchTarget.BY_CLIENT_ID),
                new RegexBlockedClient(9000, "desc9", ".*@example\\.com", RegexMatchTarget.BY_USERNAME),
                new RegexBlockedClient(10000, "desc10", "^192\\.168\\..*", RegexMatchTarget.BY_IP_ADDRESS)
        ));
        doReturn(blockedClientMap).when(blockedClientService).getBlockedClients();

        PageData<BlockedClientDto> blockedClients = blockedClientPageService.getBlockedClients(
                new PageLink(5, 0));
        List<BlockedClientDto> data = blockedClients.getData();

        assertEquals(5, data.size());
        assertEquals(10, blockedClients.getTotalElements());
        assertEquals(2, blockedClients.getTotalPages());
        assertTrue(blockedClients.hasNext());
    }

    @Test
    void givenBlockedClients_whenSearchedByText_thenOnlyMatchingClientsReturned() {
        var blockedClientMap = getBlockedClientsMap(List.of(
                new ClientIdBlockedClient(1000, "desc1", "test-alpha"),
                new ClientIdBlockedClient(2000, "desc2", "beta-test"),
                new ClientIdBlockedClient(3000, "desc3", "gammaTest"),
                new UsernameBlockedClient(4000, "desc4", "test-user1"),
                new UsernameBlockedClient(5000, "desc5", "adminTestUser"),
                new IpAddressBlockedClient(6000, "desc6", "test.192.168.0.1"),
                new IpAddressBlockedClient(7000, "desc7", "10.0.0.42-test"),
                new RegexBlockedClient(8000, "desc8", ".*test.*", RegexMatchTarget.BY_CLIENT_ID),
                new RegexBlockedClient(9000, "desc9", "test@domain.com", RegexMatchTarget.BY_USERNAME),
                new RegexBlockedClient(10000, "desc10", "^192\\.168\\..*", RegexMatchTarget.BY_IP_ADDRESS) // <-- does NOT contain "test"
        ));

        doReturn(blockedClientMap).when(blockedClientService).getBlockedClients();

        PageLink pageLink = new PageLink(10, 0, "test");
        PageData<BlockedClientDto> result = blockedClientPageService.getBlockedClients(pageLink);
        List<BlockedClientDto> data = result.getData();
        assertEquals(9, data.size());
    }

    @Test
    void givenBlockedClients_whenSortedByValueDesc_thenCorrectOrderReturned() {
        var blockedClientMap = getBlockedClientsMap(List.of(
                new ClientIdBlockedClient(1000, "desc1", "zulu"),
                new ClientIdBlockedClient(2000, "desc2", "yankee"),
                new ClientIdBlockedClient(3000, "desc3", "xray"),
                new UsernameBlockedClient(4000, "desc4", "whiskey"),
                new UsernameBlockedClient(5000, "desc5", "victor"),
                new IpAddressBlockedClient(6000, "desc6", "uniform"),
                new IpAddressBlockedClient(7000, "desc7", "tango"),
                new RegexBlockedClient(8000, "desc8", "sierra", RegexMatchTarget.BY_CLIENT_ID),
                new RegexBlockedClient(9000, "desc9", "romeo", RegexMatchTarget.BY_USERNAME),
                new RegexBlockedClient(10000, "desc10", "quebec", RegexMatchTarget.BY_IP_ADDRESS)
        ));
        doReturn(blockedClientMap).when(blockedClientService).getBlockedClients();

        SortOrder sortOrder = new SortOrder("value", SortOrder.Direction.DESC);
        PageLink pageLink = new PageLink(10, 0, null, sortOrder);

        PageData<BlockedClientDto> result = blockedClientPageService.getBlockedClients(pageLink);
        List<String> actual = result.getData().stream().map(BlockedClientDto::getValue).toList();
        List<String> expected = List.of("zulu", "yankee", "xray", "whiskey", "victor", "uniform", "tango", "sierra", "romeo", "quebec");

        assertEquals(expected, actual);
    }


    @Test
    void givenBlockedClients_whenFilteredByExactType_thenOnlyMatchingTypeReturned() {
        BlockedClientQuery query = new BlockedClientQuery();
        query.setTypes(Set.of(BlockedClientType.USERNAME));
        query.setPageLink(new TimePageLink(10, 0));
        var blockedClientMap = getBlockedClientsMap(List.of(
                new ClientIdBlockedClient(1000, "desc1", "c1"),
                new UsernameBlockedClient(2000, "desc2", "u1"),
                new IpAddressBlockedClient(3000, "desc3", "1.1.1.1")
        ));
        doReturn(blockedClientMap).when(blockedClientService).getBlockedClients();

        var result = blockedClientPageService.getBlockedClients(query);
        assertEquals(1, result.getTotalElements());
        assertEquals(BlockedClientType.USERNAME, result.getData().get(0).getType());
    }

    @Test
    void givenBlockedClients_whenFilteredByTextSearch_thenOnlyMatchingTextReturned() {
        BlockedClientQuery query = new BlockedClientQuery();
        query.setPageLink(new TimePageLink(10, 0, "user"));
        var blockedClientMap = getBlockedClientsMap(List.of(
                new UsernameBlockedClient(1000, "desc", "admin"),
                new UsernameBlockedClient(2000, "desc", "user-login"),
                new UsernameBlockedClient(3000, "desc", "visitor")
        ));
        doReturn(blockedClientMap).when(blockedClientService).getBlockedClients();

        var result = blockedClientPageService.getBlockedClients(query);
        assertEquals(1, result.getTotalElements());
        assertEquals("user-login", result.getData().get(0).getValue());
    }

    @Test
    void givenBlockedClients_whenFilteredByValueSubstring_thenOnlyMatchesReturned() {
        BlockedClientQuery query = new BlockedClientQuery();
        query.setValue("test");
        query.setPageLink(new TimePageLink(10, 0));
        var blockedClientMap = getBlockedClientsMap(List.of(
                new ClientIdBlockedClient(1000, "desc", "test-1"),
                new ClientIdBlockedClient(2000, "desc", "client-2"),
                new UsernameBlockedClient(3000, "desc", "tester")
        ));
        doReturn(blockedClientMap).when(blockedClientService).getBlockedClients();

        var result = blockedClientPageService.getBlockedClients(query);
        assertEquals(2, result.getTotalElements());
    }

    @Test
    void givenBlockedClients_whenFilteredByTimeRange_thenOnlyInRangeReturned() {
        BlockedClientQuery query = new BlockedClientQuery();
        query.setPageLink(new TimePageLink(10, 0, null, null, 2000L, 5000L));
        var blockedClientMap = getBlockedClientsMap(List.of(
                new ClientIdBlockedClient(1000L, "desc", "early"),
                new ClientIdBlockedClient(3000L, "desc", "middle"),
                new ClientIdBlockedClient(6000L, "desc", "late")
        ));
        doReturn(blockedClientMap).when(blockedClientService).getBlockedClients();

        var result = blockedClientPageService.getBlockedClients(query);
        assertEquals(1, result.getTotalElements());
        assertEquals("middle", result.getData().get(0).getValue());
    }

    @Test
    void givenBlockedClients_whenFilteredByRegexMatchTarget_thenOnlyMatchingTargetsReturned() {
        BlockedClientQuery query = new BlockedClientQuery();
        query.setRegexMatchTargets(List.of(RegexMatchTarget.BY_USERNAME));
        query.setPageLink(new TimePageLink(10, 0));
        var blockedClientMap = getBlockedClientsMap(List.of(
                new RegexBlockedClient(1000L, "desc", ".*admin.*", RegexMatchTarget.BY_USERNAME),
                new RegexBlockedClient(2000L, "desc", ".*client.*", RegexMatchTarget.BY_CLIENT_ID),
                new RegexBlockedClient(3000L, "desc", ".*ip.*", RegexMatchTarget.BY_IP_ADDRESS)
        ));
        doReturn(blockedClientMap).when(blockedClientService).getBlockedClients();

        var result = blockedClientPageService.getBlockedClients(query);
        assertEquals(1, result.getTotalElements());
        assertEquals(RegexMatchTarget.BY_USERNAME, result.getData().get(0).getRegexMatchTarget());
    }

    @Test
    void givenBlockedClients_whenNoFilters_thenAllReturned() {
        BlockedClientQuery query = new BlockedClientQuery();
        query.setPageLink(new TimePageLink(10, 0));
        var blockedClientMap = getBlockedClientsMap(List.of(
                new ClientIdBlockedClient(1000L, "desc", "alpha"),
                new UsernameBlockedClient(2000L, "desc", "user"),
                new IpAddressBlockedClient(3000L, "desc", "1.1.1.1")
        ));
        doReturn(blockedClientMap).when(blockedClientService).getBlockedClients();

        var result = blockedClientPageService.getBlockedClients(query);
        assertEquals(3, result.getTotalElements());
    }

    @Test
    void givenBlockedClients_whenFilteredByMultipleCriteria_thenOnlyMatchingClientsReturned() {
        BlockedClientQuery query = new BlockedClientQuery();
        query.setTypes(Set.of(BlockedClientType.REGEX));
        query.setRegexMatchTargets(List.of(RegexMatchTarget.BY_CLIENT_ID));
        query.setValue("abc");
        query.setPageLink(new TimePageLink(10, 0, "abc", null, 1000L, 6000L));

        var blockedClientMap = getBlockedClientsMap(List.of(
                new RegexBlockedClient(1000L, "desc1", "abc.*", RegexMatchTarget.BY_CLIENT_ID),      // ✅ match
                new RegexBlockedClient(3000L, "desc2", "def.*", RegexMatchTarget.BY_CLIENT_ID),      // ⛔ value doesn't match
                new RegexBlockedClient(4000L, "desc3", "abc.*", RegexMatchTarget.BY_USERNAME),       // ⛔ match target doesn't match
                new RegexBlockedClient(7000L, "desc4", "abc.*", RegexMatchTarget.BY_IP_ADDRESS),     // ⛔ match target doesn't match
                new ClientIdBlockedClient(5000L, "desc5", "abc-client")                              // ⛔ type doesn't match
        ));

        doReturn(blockedClientMap).when(blockedClientService).getBlockedClients();
        var result = blockedClientPageService.getBlockedClients(query);

        assertEquals(1, result.getTotalElements());
        BlockedClientDto match = result.getData().get(0);
        assertEquals("abc.*", match.getValue());
        assertEquals(BlockedClientType.REGEX, match.getType());
        assertEquals(RegexMatchTarget.BY_CLIENT_ID, match.getRegexMatchTarget());
    }

}
