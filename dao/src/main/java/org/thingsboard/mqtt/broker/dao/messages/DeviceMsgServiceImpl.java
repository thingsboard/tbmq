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
package org.thingsboard.mqtt.broker.dao.messages;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.ScriptOutputType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cache.CacheProperties;
import org.thingsboard.mqtt.broker.cache.LettuceConnectionManager;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class DeviceMsgServiceImpl implements DeviceMsgService {

    private static final String ADD_MESSAGES_SCRIPT_SHA = "b871298e156d1e8490eef02fdc96cc28d4442e36";
    private static final String GET_MESSAGES_SCRIPT_SHA = "e083e5645a5f268448aca2ec1d3150ee6de510ef";
    private static final String REMOVE_MESSAGES_SCRIPT_SHA = "efd7dd0e8b3ba4862b53691798fd5ff1f8f6e629";
    private static final String REMOVE_MESSAGE_SCRIPT_SHA = "af40a579a941a140cace4e5243fc0921a4c7b4b0";
    private static final String UPDATE_PACKET_TYPE_SCRIPT_SHA = "86164ab58880b91c4aee396bb5701fd6af1b0258";

    // TODO: consider why we set score = lastPacketId if set is empty instead of set it to 0.
    private static final String ADD_MESSAGES_SCRIPT = """
            local messagesKey = KEYS[1]
            local lastPacketIdKey = KEYS[2]
            local maxMessagesSize = tonumber(ARGV[1])
            local messages = cjson.decode(ARGV[2])
            local defaultTtl = tonumber(ARGV[3])
            -- Fetch the last packetId from the key-value store
            local lastPacketId = tonumber(redis.call('GET', lastPacketIdKey)) or 0
            
            -- Get the current maximum score in the sorted set
            local maxScoreElement = redis.call('ZRANGE', messagesKey, 0, 0, 'REV', 'WITHSCORES')
            
            -- Check if the maxScoreElement is non-empty
            local score
            if #maxScoreElement > 0 then
               score = tonumber(maxScoreElement[2])
            else
               score = lastPacketId
            end
            
            -- Track the first packet ID
            local previousPacketId = lastPacketId
            
            -- Add each message to the sorted set and as a separate key
            for _, msg in ipairs(messages) do
                lastPacketId = lastPacketId + 1
                if lastPacketId > 0xffff then
                    lastPacketId = 1
                end
                msg.packetId = lastPacketId
                score = score + 1
                local msgKey = messagesKey .. "_" .. lastPacketId
                local msgJson = cjson.encode(msg)
                local msgExpiryInterval = msg.msgExpiryInterval or defaultTtl
                -- Store the message as a separate key with TTL
                redis.call('SET', msgKey, msgJson, 'EX', msgExpiryInterval)
                -- Add the key to the sorted set using packetId as the score
                redis.call('ZADD', messagesKey, score, msgKey)
            end
            -- Update the last packetId in the key-value store
            redis.call('SET', lastPacketIdKey, lastPacketId)
            -- Get the elements to be trimmed
            local numElementsToRemove = redis.call('ZCARD', messagesKey) - maxMessagesSize
            if numElementsToRemove > 0 then
                local trimmedElements = redis.call('ZRANGE', messagesKey, 0, numElementsToRemove - 1)
                for _, key in ipairs(trimmedElements) do
                    redis.call('DEL', key)
                    redis.call('ZREM', messagesKey, key)
                end
            end
            return previousPacketId
            """;
    private static final String GET_MESSAGES_SCRIPT = """
            local messagesKey = KEYS[1]
            local maxMessagesSize = tonumber(ARGV[1])
            -- Get the range of elements from the sorted set
            local elements = redis.call('ZRANGE', messagesKey, 0, -1)
            local messages = {}
            for _, key in ipairs(elements) do
                -- Check if the key still exists
                if redis.call('EXISTS', key) == 1 then
                    local msgJson = redis.call('GET', key)
                    table.insert(messages, msgJson)
                else
                    -- If the key does not exist, remove it from the sorted set
                    redis.call('ZREM', messagesKey, key)
                end
            end
            return messages
            """;
    private static final String REMOVE_MESSAGES_SCRIPT = """
            local messagesKey = KEYS[1]
            local lastPacketIdKey = KEYS[2]
            -- Get all elements from the sorted set
            local elements = redis.call('ZRANGE', messagesKey, 0, -1)
            -- Delete each associated key entry
            for _, key in ipairs(elements) do
                redis.call('DEL', key)
            end
            -- Delete the sorted set
            redis.call('DEL', messagesKey)
            -- Delete the last packet id key
            redis.call('DEL', lastPacketIdKey)
            return "OK"
            """;
    private static final String REMOVE_MESSAGE_SCRIPT = """
            local messagesKey = KEYS[1]
            local packetId = ARGV[1]
            -- Construct the message key
            local msgKey = messagesKey .. "_" .. packetId
            -- Remove the message from the sorted set
            redis.call('ZREM', messagesKey, msgKey)
            -- Delete the message key
            redis.call('DEL', msgKey)
            return "OK"
            """;
    private static final String UPDATE_PACKET_TYPE_SCRIPT = """
            local messagesKey = KEYS[1]
            local packetId = ARGV[1]
            -- Construct the message key
            local msgKey = messagesKey .. "_" .. packetId
            -- Fetch the message from the key-value store
            local msgJson = redis.call('GET', msgKey)
            if not msgJson then
                return "OK" -- Message not found
            end
            -- Decode the JSON message
            local msg = cjson.decode(msgJson)
            -- Update the packet type
            msg.packetType = "PUBREL"
            -- Encode the updated message back to JSON
            local updatedMsgJson = cjson.encode(msg)
            -- Save the updated message back to the key-value store
            redis.call('SET', msgKey, updatedMsgJson)
            return "OK"
            """;

    @Value("${mqtt.persistent-session.device.persisted-messages.ttl}")
    private int defaultTtl;

    @Value("${mqtt.persistent-session.device.persisted-messages.limit}")
    private int messagesLimit;

    private final LettuceConnectionManager connectionManager;
    private final CacheProperties cacheProperties;
    private final ConcurrentMap<String, CompletableFuture<Void>> scriptLoadingMap;

    public DeviceMsgServiceImpl(LettuceConnectionManager connectionManager, CacheProperties cacheProperties) {
        this.connectionManager = connectionManager;
        this.cacheProperties = cacheProperties;
        this.scriptLoadingMap = new ConcurrentHashMap<>();
    }

    private byte[] defaultTtlBytes;
    private byte[] messagesLimitBytes;
    private String cachePrefix;

    @PostConstruct
    public void init() {
        if (messagesLimit > 0xffff) {
            throw new IllegalArgumentException("Persisted messages limit can't be greater than 65535!");
        }
        try {
            defaultTtlBytes = intToBytes(defaultTtl);
            messagesLimitBytes = intToBytes(messagesLimit);
            cachePrefix = cacheProperties.getCachePrefix();

            loadScript(ADD_MESSAGES_SCRIPT_SHA, ADD_MESSAGES_SCRIPT);
            loadScript(GET_MESSAGES_SCRIPT_SHA, GET_MESSAGES_SCRIPT);
            loadScript(REMOVE_MESSAGES_SCRIPT_SHA, REMOVE_MESSAGES_SCRIPT);
            loadScript(REMOVE_MESSAGE_SCRIPT_SHA, REMOVE_MESSAGE_SCRIPT);
            loadScript(UPDATE_PACKET_TYPE_SCRIPT_SHA, UPDATE_PACKET_TYPE_SCRIPT);
        } catch (Exception e) {
            throw new RuntimeException("Failed to init persisted device messages cache service!", e);
        }
    }

    @Override
    public CompletionStage<Integer> saveAndReturnPreviousPacketId(String clientId, List<DevicePublishMsg> devicePublishMessages, boolean failOnConflict) {
        if (log.isTraceEnabled()) {
            log.trace("Save persisted messages, clientId - {}, devicePublishMessages size - {}", clientId, devicePublishMessages.size());
        }
        byte[] messagesCacheKeyBytes = ClientIdMessagesCacheKey.toBytesKey(clientId, cachePrefix);
        byte[] lastPacketIdKeyBytes = ClientIdLastPacketIdCacheKey.toBytesKey(clientId, cachePrefix);
        byte[] messagesBytes = JacksonUtil.writeValueAsBytes(devicePublishMessages);
        RedisFuture<Long> prevPacketIdFuture = connectionManager.evalShaAsync(ADD_MESSAGES_SCRIPT_SHA, ScriptOutputType.INTEGER,
                new byte[][]{messagesCacheKeyBytes, lastPacketIdKeyBytes}, messagesLimitBytes, messagesBytes, defaultTtlBytes);
        return prevPacketIdFuture.exceptionallyCompose(throwable -> {
            if (throwable instanceof RedisNoScriptException) {
                CompletableFuture<Void> loadScriptFuture = processLoadScriptAsync(throwable, ADD_MESSAGES_SCRIPT_SHA, ADD_MESSAGES_SCRIPT);
                CompletableFuture<Long> retryFuture = loadScriptFuture.thenCompose(__ ->
                        connectionManager.evalShaAsync(ADD_MESSAGES_SCRIPT_SHA, ScriptOutputType.INTEGER,
                                new byte[][]{messagesCacheKeyBytes, lastPacketIdKeyBytes}, messagesLimitBytes, messagesBytes, defaultTtlBytes));
                return retryFuture.exceptionallyCompose(retryThrowable -> {
                    log.debug("Falling back to eval due to exception on retry sha evaluation of saveAndReturnPreviousPacketId: ", retryThrowable);
                    return connectionManager.evalAsync(ADD_MESSAGES_SCRIPT, ScriptOutputType.INTEGER,
                            new byte[][]{messagesCacheKeyBytes, lastPacketIdKeyBytes}, messagesLimitBytes, messagesBytes, defaultTtlBytes);
                });
            }
            throw new CompletionException(throwable);
        }).thenApply(prevPacketId -> prevPacketId != null ? prevPacketId.intValue() : 0);
    }

    @Override
    public CompletionStage<List<DevicePublishMsg>> findPersistedMessages(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("Find persisted messages, clientId - {}", clientId);
        }
        byte[] messagesCacheKeyBytes = ClientIdMessagesCacheKey.toBytesKey(clientId, cachePrefix);
        RedisFuture<List<byte[]>> messagesFuture = connectionManager.evalShaAsync(GET_MESSAGES_SCRIPT_SHA, ScriptOutputType.MULTI,
                new byte[][]{messagesCacheKeyBytes}, messagesLimitBytes);
        return messagesFuture.exceptionallyCompose(throwable -> {
            if (throwable instanceof RedisNoScriptException) {
                CompletableFuture<Void> loadScriptFuture = processLoadScriptAsync(throwable, GET_MESSAGES_SCRIPT_SHA, GET_MESSAGES_SCRIPT);
                CompletableFuture<List<byte[]>> retryFuture = loadScriptFuture.thenCompose(__ ->
                        connectionManager.evalShaAsync(GET_MESSAGES_SCRIPT_SHA, ScriptOutputType.MULTI,
                                new byte[][]{messagesCacheKeyBytes}, messagesLimitBytes));
                return retryFuture.exceptionallyCompose(retryThrowable -> {
                    log.debug("Falling back to eval due to exception on retry sha evaluation of findPersistedMessages: ", retryThrowable);
                    return connectionManager.evalAsync(GET_MESSAGES_SCRIPT, ScriptOutputType.MULTI,
                            new byte[][]{messagesCacheKeyBytes}, messagesLimitBytes);
                });
            }
            throw new CompletionException(throwable);
        }).thenApply(messages ->
                messages.stream()
                        .map(messageInBytes -> JacksonUtil.fromBytes(messageInBytes, DevicePublishMsg.class))
                        .toList());
    }

    @Override
    public CompletionStage<String> removePersistedMessages(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("Removing persisted messages, clientId - {}", clientId);
        }
        byte[] messagesCacheKeyBytes = ClientIdMessagesCacheKey.toBytesKey(clientId, cachePrefix);
        byte[] lastPacketIdKeyBytes = ClientIdLastPacketIdCacheKey.toBytesKey(clientId, cachePrefix);
        RedisFuture<String> removeFuture = connectionManager.evalShaAsync(REMOVE_MESSAGES_SCRIPT_SHA, ScriptOutputType.STATUS, messagesCacheKeyBytes, lastPacketIdKeyBytes);
        return removeFuture.exceptionallyCompose(throwable -> {
            if (throwable instanceof RedisNoScriptException) {
                CompletableFuture<Void> loadScriptFuture = processLoadScriptAsync(throwable, REMOVE_MESSAGES_SCRIPT_SHA, REMOVE_MESSAGES_SCRIPT);
                CompletableFuture<String> retryFuture = loadScriptFuture.thenCompose(__ ->
                        connectionManager.evalShaAsync(REMOVE_MESSAGES_SCRIPT_SHA, ScriptOutputType.STATUS, messagesCacheKeyBytes, lastPacketIdKeyBytes));
                return retryFuture.exceptionallyCompose(retryThrowable -> {
                    log.debug("Falling back to eval due to exception on retry sha evaluation of removePersistedMessages: ", retryThrowable);
                    return connectionManager.evalAsync(REMOVE_MESSAGES_SCRIPT, ScriptOutputType.STATUS, messagesCacheKeyBytes, lastPacketIdKeyBytes);
                });
            }
            throw new CompletionException(throwable);
        });
    }

    @Override
    public CompletionStage<String> removePersistedMessage(String clientId, int packetId) {
        if (log.isTraceEnabled()) {
            log.trace("Removing persisted message, clientId - {}, packetId - {}", clientId, packetId);
        }
        byte[] messagesCacheKeyBytes = ClientIdMessagesCacheKey.toBytesKey(clientId, cachePrefix);
        byte[] packetIdBytes = intToBytes(packetId);
        RedisFuture<String> removeFuture = connectionManager.evalShaAsync(REMOVE_MESSAGE_SCRIPT_SHA, ScriptOutputType.STATUS,
                new byte[][]{messagesCacheKeyBytes}, packetIdBytes);
        return removeFuture.exceptionallyCompose(throwable -> {
            if (throwable instanceof RedisNoScriptException) {
                CompletableFuture<Void> loadScriptFuture = processLoadScriptAsync(throwable, REMOVE_MESSAGE_SCRIPT_SHA, REMOVE_MESSAGE_SCRIPT);
                CompletableFuture<String> retryFuture = loadScriptFuture.thenCompose(__ ->
                        connectionManager.evalShaAsync(REMOVE_MESSAGE_SCRIPT_SHA, ScriptOutputType.STATUS,
                                new byte[][]{messagesCacheKeyBytes}, packetIdBytes));
                return retryFuture.exceptionallyCompose(retryThrowable -> {
                    log.debug("Falling back to eval due to exception on retry sha evaluation of removePersistedMessage: ", retryThrowable);
                    return connectionManager.evalAsync(Objects.requireNonNull(REMOVE_MESSAGE_SCRIPT), ScriptOutputType.STATUS,
                            new byte[][]{messagesCacheKeyBytes}, packetIdBytes);
                });
            }
            throw new CompletionException(throwable);
        });
    }

    @Override
    public CompletionStage<String> updatePacketReceived(String clientId, int packetId) {
        if (log.isTraceEnabled()) {
            log.trace("Update packet received, clientId - {}, packetId - {}", clientId, packetId);
        }
        byte[] messagesCacheKeyBytes = ClientIdMessagesCacheKey.toBytesKey(clientId, cachePrefix);
        byte[] packetIdBytes = intToBytes(packetId);
        RedisFuture<String> updateFuture = connectionManager.evalShaAsync(UPDATE_PACKET_TYPE_SCRIPT_SHA, ScriptOutputType.STATUS,
                new byte[][]{messagesCacheKeyBytes}, packetIdBytes);
        return updateFuture.exceptionallyCompose(throwable -> {
            if (throwable instanceof RedisNoScriptException) {
                CompletableFuture<Void> loadScriptFuture = processLoadScriptAsync(throwable, UPDATE_PACKET_TYPE_SCRIPT_SHA, UPDATE_PACKET_TYPE_SCRIPT);
                CompletableFuture<String> retryFuture = loadScriptFuture.thenCompose(__ ->
                        connectionManager.evalShaAsync(UPDATE_PACKET_TYPE_SCRIPT_SHA, ScriptOutputType.STATUS,
                                new byte[][]{messagesCacheKeyBytes}, packetIdBytes));
                return retryFuture.exceptionallyCompose(retryThrowable -> {
                    log.debug("Falling back to eval due to exception on retry sha evaluation of updatePacketReceived: ", retryThrowable);
                    return connectionManager.evalAsync(UPDATE_PACKET_TYPE_SCRIPT, ScriptOutputType.STATUS,
                            new byte[][]{messagesCacheKeyBytes}, packetIdBytes);
                });
            }
            throw new CompletionException(throwable);
        });
    }

    @Override
    public CompletionStage<Integer> getLastPacketId(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("Get last packet id, clientId - {}", clientId);
        }
        byte[] lastPacketIdKeyBytes = ClientIdLastPacketIdCacheKey.toBytesKey(clientId, cachePrefix);
        RedisFuture<byte[]> future = connectionManager.getAsync(lastPacketIdKeyBytes);
        return future.thenApply(value -> value == null ? 0 : bytesToInt(value));
    }

    @Override
    public CompletionStage<Long> removeLastPacketId(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("Remove last packet id, clientId - {}", clientId);
        }
        byte[] lastPacketIdKeyBytes = ClientIdLastPacketIdCacheKey.toBytesKey(clientId, cachePrefix);
        return connectionManager.delAsync(lastPacketIdKeyBytes);
    }

    @Override
    public CompletionStage<String> saveLastPacketId(String clientId, int lastPacketId) {
        if (log.isTraceEnabled()) {
            log.trace("Save last packet id, clientId - {}", clientId);
        }
        byte[] lastPacketIdKeyBytes = ClientIdLastPacketIdCacheKey.toBytesKey(clientId, cachePrefix);
        return connectionManager.setAsync(lastPacketIdKeyBytes, intToBytes(lastPacketId));
    }

    private CompletableFuture<Void> processLoadScriptAsync(Throwable throwable, String scriptSha, String script) {
        if (log.isDebugEnabled()) {
            log.debug("evalSha failed due to missing script, attempting to load the script: {}", scriptSha, throwable);
        }
        return scriptLoadingMap.computeIfAbsent(scriptSha, sha -> {
            CompletableFuture<Void> loadingFuture = new CompletableFuture<>();
            loadScriptAsync(scriptSha, script).handle((scriptLoadResult, loadThrowable) -> {
                if (loadThrowable == null) {
                    loadingFuture.complete(null);
                } else {
                    loadingFuture.completeExceptionally(loadThrowable);
                }
                return null;
            });
            loadingFuture.whenComplete((result, ex) -> scriptLoadingMap.remove(scriptSha));
            return loadingFuture;
        });
    }

    private RedisFuture<String> loadScriptAsync(String scriptSha, String script) {
        if (log.isDebugEnabled()) {
            log.debug("Loading LUA script async with expected SHA [{}], connection [{}]", scriptSha, connectionManager);
        }
        return connectionManager.scriptLoadAsync(script);
    }

    private void loadScript(String scriptSha, String script) {
        log.debug("Loading LUA script with expected SHA [{}], connection [{}]", scriptSha, connectionManager);
        String actualScriptSha = connectionManager.scriptLoad(script);
        if (!scriptSha.equals(actualScriptSha)) {
            log.error("SHA for script is wrong! Expected [{}] actual [{}] connection [{}]", scriptSha, actualScriptSha, connectionManager);
        }
    }

    private static byte[] intToBytes(int value) {
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }

    private static int bytesToInt(byte[] bytes) {
        return Integer.parseInt(new String(bytes, StandardCharsets.UTF_8));
    }
}
