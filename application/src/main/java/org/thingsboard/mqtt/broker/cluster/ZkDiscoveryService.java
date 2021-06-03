/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.cluster;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.RetryForever;
import org.apache.curator.utils.CloseableUtils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent.Type.CHILD_REMOVED;

@Service
@ConditionalOnProperty(prefix = "zk", value = "enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
@RequiredArgsConstructor
public class ZkDiscoveryService implements PathChildrenCacheListener {
    private volatile boolean stopped = true;

    private final ZkConfiguration zkConfiguration;
    private final ServiceInfoProvider serviceInfoProvider;
    private final NodeStateUpdateService nodeStateUpdateService;

    private final ExecutorService reconnectExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("zk-discovery"));


    private CuratorFramework client;
    private PathChildrenCache cache;
    private String nodePath;
    private String zkNodesDir;

    // TODO: need to make sure that each Node has a unique 'serviceId' at one point of a time

    // TODO: store ServiceInfos in memory (maybe along with related topics e.g. DisconnectCommand, Publish DownLink)

    @PostConstruct
    public void init() {
        log.info("Initializing...");

        log.info("Initializing discovery service using ZK connect string: {}", zkConfiguration.getUrl());

        zkNodesDir = zkConfiguration.getZkDir() + "/nodes";
        initZkClient();
    }

    private List<QueueProtos.ServiceInfo> getOtherServers() {
        return cache.getCurrentData().stream()
                .filter(cd -> !cd.getPath().equals(nodePath))
                .map(cd -> {
                    try {
                        return QueueProtos.ServiceInfo.parseFrom(cd.getData());
                    } catch (NoSuchElementException | InvalidProtocolBufferException e) {
                        log.error("Failed to decode ZK node", e);
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(value = 1)
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (stopped) {
            log.debug("Ignoring application ready event. Service is stopped.");
            return;
        }

        if (client.getState() != CuratorFrameworkState.STARTED) {
            log.debug("Ignoring application ready event, ZK client is not started, ZK client state [{}]", client.getState());
            return;
        }

        log.info("Received application ready event. Starting current ZK node.");
        publishCurrentServer();

        loadNodes();
    }

    private boolean currentServerExists() {
        QueueProtos.ServiceInfo self = serviceInfoProvider.getServiceInfo();
        QueueProtos.ServiceInfo registeredServerInfo = getRegisteredServerInfo();
        return self.equals(registeredServerInfo);
    }

    private QueueProtos.ServiceInfo getRegisteredServerInfo() {
        if (nodePath == null) {
            return null;
        }
        try {
            return QueueProtos.ServiceInfo.parseFrom(client.getData().forPath(nodePath));
        } catch (KeeperException.NoNodeException e) {
            log.info("ZK node does not exist: {}", nodePath);
        } catch (Exception e) {
            log.error("Couldn't check if ZK node exists", e);
        }
        return null;
    }

    private ConnectionStateListener checkReconnect() {
        return (client, newState) -> {
            log.info("ZK state changed: {}", newState);
            // TODO: if LOST or SUSPENDED should disconnect all Kafka consumers
            if (newState == ConnectionState.LOST) {
                scheduleReconnect();
            }
        };
    }

    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);
    private void scheduleReconnect() {
        boolean inProcess = isReconnecting.compareAndExchange(false, true);
        if (inProcess) {
            log.debug("Reconnect scheduler is currently running.");
            return;
        }

        reconnectExecutor.execute(() -> {
            while (isReconnecting.get()) {
                log.info("Trying to reconnect");
                try {
                    destroyZkClient();
                    initZkClient();
                    publishCurrentServer();
                    isReconnecting.getAndSet(false);
                } catch (Exception e) {
                    log.error("Failed to reconnect to ZK: {}", e.getMessage(), e);
                    try {
                        Thread.sleep(zkConfiguration.getReconnectScheduledDelayMs());
                    } catch (InterruptedException interruptedException) {
                        log.trace("Failed to wait for ");
                    }
                }
            }
        });
    }

    private void initZkClient() {
        try {
            client = CuratorFrameworkFactory.newClient(zkConfiguration.getUrl(), zkConfiguration.getSessionTimeoutMs(),
                    zkConfiguration.getConnectionTimeoutMs(), new RetryForever(zkConfiguration.getRetryIntervalMs()));
            client.start();
            client.blockUntilConnected();
            cache = new PathChildrenCache(client, zkNodesDir, true);
            cache.getListenable().addListener(this);
            cache.start();
            stopped = false;
            log.info("ZK client connected");
        } catch (Exception e) {
            log.error("Failed to connect to ZK: {}", e.getMessage(), e);
            CloseableUtils.closeQuietly(cache);
            CloseableUtils.closeQuietly(client);
            throw new RuntimeException(e);
        }
    }

    private void destroyZkClient() {
        stopped = true;
        try {
            unpublishCurrentServer();
        } catch (Exception e) {
        }
        CloseableUtils.closeQuietly(cache);
        CloseableUtils.closeQuietly(client);
        log.info("ZK client disconnected");
    }

    public synchronized void publishCurrentServer() {
        QueueProtos.ServiceInfo self = serviceInfoProvider.getServiceInfo();
        if (currentServerExists()) {
            log.info("[{}] ZK node for current instance already exists, NOT creating new one: {}", self.getServiceId(), nodePath);
            return;
        }

        try {
            log.info("[{}] Creating ZK node for current instance", self.getServiceId());
            nodePath = client.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath(zkNodesDir + "/", self.toByteArray());
            log.info("[{}] Created ZK node for current instance: {}", self.getServiceId(), nodePath);
            client.getConnectionStateListenable().addListener(checkReconnect());
        } catch (Exception e) {
            log.error("Failed to create ZK node", e);
            throw new RuntimeException(e);
        }
    }

    private void unpublishCurrentServer() {
        try {
            if (nodePath != null) {
                client.delete().forPath(nodePath);
            }
        } catch (Exception e) {
            log.error("Failed to delete ZK node {}", nodePath, e);
            throw new RuntimeException(e);
        }
    }

    @PreDestroy
    public void destroy() {
        destroyZkClient();
        reconnectExecutor.shutdownNow();
        log.info("Stopped discovery service");
    }

    // TODO: need to wait till clients gets disconnected on the node that is shutting down normally
    @Override
    public void childEvent(CuratorFramework curatorFramework, PathChildrenCacheEvent pathChildrenCacheEvent) throws Exception {
        if (stopped) {
            log.debug("Ignoring {}. Service is stopped.", pathChildrenCacheEvent);
            return;
        }
        if (client.getState() != CuratorFrameworkState.STARTED) {
            log.debug("Ignoring {}, ZK client is not started, ZK client state [{}]", pathChildrenCacheEvent, client.getState());
            return;
        }
        ChildData data = pathChildrenCacheEvent.getData();
        if (data == null) {
            log.debug("Ignoring {} due to empty child data", pathChildrenCacheEvent);
            return;
        }

        if (data.getData() == null) {
            log.debug("Ignoring {} due to empty child's data", pathChildrenCacheEvent);
            return;
        }

        if (nodePath != null && nodePath.equals(data.getPath())) {
            if (pathChildrenCacheEvent.getType() == CHILD_REMOVED) {
                log.info("ZK node for current instance is somehow deleted.");
                publishCurrentServer();
            }
            log.debug("Ignoring event about current server {}", pathChildrenCacheEvent);
            return;
        }

        QueueProtos.ServiceInfo instance;
        try {
            instance = QueueProtos.ServiceInfo.parseFrom(data.getData());
        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to decode server instance for node {}", data.getPath(), e);
            throw e;
        }
        log.info("Processing [{}] event for [{}]", pathChildrenCacheEvent.getType(), instance.getServiceId());
        switch (pathChildrenCacheEvent.getType()) {
            case CHILD_ADDED:
            case CHILD_UPDATED:
            case CHILD_REMOVED:
                loadNodes();
                break;
            default:
                break;
        }
    }

    /**
     * A single entry point to recalculate partitions
     * Synchronized to ensure that other servers info is up to date
     * */
    private synchronized void loadNodes() {
        nodeStateUpdateService.loadNodes(getOtherServers().stream().map(QueueProtos.ServiceInfo::getServiceId).collect(Collectors.toList()));
    }
}
