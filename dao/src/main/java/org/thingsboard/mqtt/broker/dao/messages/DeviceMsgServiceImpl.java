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
package org.thingsboard.mqtt.broker.dao.messages;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.ScriptOutputType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cache.LettuceConnectionManager;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.util.DevicePublishMsgUtil;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

@Slf4j
@Service
public class DeviceMsgServiceImpl implements DeviceMsgService {

    private static final int IMPORT_TO_REDIS_BATCH_SIZE = 1000;

    private static final CSVFormat IMPORT_CSV_FORMAT = CSVFormat.Builder.create()
            .setHeader().setSkipHeaderRecord(true).build();

    private static final String ADD_MESSAGES_SCRIPT_SHA = "b871298e156d1e8490eef02fdc96cc28d4442e36";
    private static final String GET_MESSAGES_SCRIPT_SHA = "e083e5645a5f268448aca2ec1d3150ee6de510ef";
    private static final String REMOVE_MESSAGES_SCRIPT_SHA = "efd7dd0e8b3ba4862b53691798fd5ff1f8f6e629";
    private static final String REMOVE_MESSAGE_SCRIPT_SHA = "af40a579a941a140cace4e5243fc0921a4c7b4b0";
    private static final String UPDATE_PACKET_TYPE_SCRIPT_SHA = "86164ab58880b91c4aee396bb5701fd6af1b0258";
    private static final String MIGRATE_FROM_POSTGRES_TO_REDIS_SCRIPT_SHA = "76ea84e42d6ab98e646ef8f88b99efd568721926";

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
    private static final String MIGRATE_FROM_POSTGRES_TO_REDIS_SCRIPT = """
            local messagesKey = KEYS[1]
            local lastPacketIdKey = KEYS[2]
            local messages = cjson.decode(ARGV[1])
            local defaultTtl = tonumber(ARGV[2])
            local lastPacketId = 0
                        
            -- Get the current maximum score in the sorted set
            local maxScoreElement = redis.call('ZRANGE', messagesKey, 0, 0, 'REV', 'WITHSCORES')
            -- Check if the maxScoreElement is non-empty
            local score
            if #maxScoreElement > 0 then
               score = tonumber(maxScoreElement[2])
            else
               score = 0
            end
                        
            -- Add each message to the sorted set using packetId as the score
            for _, msg in ipairs(messages) do
                local msgKey = messagesKey .. "_" .. msg.packetId
                local msgJson = cjson.encode(msg)
                local msgExpiryInterval = msg.msgExpiryInterval or defaultTtl
                -- Store the message as a separate key with TTL
                redis.call('SET', msgKey, msgJson, 'EX', msgExpiryInterval)
                -- increase the score
                score = score + 1
                redis.call('ZADD', messagesKey, score, msgKey)
                -- Update lastPacketId with the current packetId
                lastPacketId = msg.packetId
            end

            -- Update the last packetId in the key-value store
            redis.call('SET', lastPacketIdKey, lastPacketId)
                        
            return "OK"
            """;

    @Value("${mqtt.persistent-session.device.persisted-messages.ttl}")
    private int defaultTtl;

    @Value("${mqtt.persistent-session.device.persisted-messages.limit}")
    private int messagesLimit;

    @Value("${cache.cache-prefix:}")
    protected String cachePrefix;

    private final LettuceConnectionManager connectionManager;
    private final boolean installProfileActive;
    private final ConcurrentMap<String, CompletableFuture<Void>> scriptLoadingMap;

    public DeviceMsgServiceImpl(LettuceConnectionManager connectionManager, Environment environment) {
        this.connectionManager = connectionManager;
        this.installProfileActive = Arrays.asList(environment.getActiveProfiles()).contains("install");
        this.scriptLoadingMap = new ConcurrentHashMap<>();
    }

    @PostConstruct
    public void init() {
        if (messagesLimit > 0xffff) {
            throw new IllegalArgumentException("Persisted messages limit can't be greater than 65535!");
        }
        try {
            loadScript(ADD_MESSAGES_SCRIPT_SHA, ADD_MESSAGES_SCRIPT);
            loadScript(GET_MESSAGES_SCRIPT_SHA, GET_MESSAGES_SCRIPT);
            loadScript(REMOVE_MESSAGES_SCRIPT_SHA, REMOVE_MESSAGES_SCRIPT);
            loadScript(REMOVE_MESSAGE_SCRIPT_SHA, REMOVE_MESSAGE_SCRIPT);
            loadScript(UPDATE_PACKET_TYPE_SCRIPT_SHA, UPDATE_PACKET_TYPE_SCRIPT);
            if (!installProfileActive) {
                return;
            }
            loadScript(MIGRATE_FROM_POSTGRES_TO_REDIS_SCRIPT_SHA, MIGRATE_FROM_POSTGRES_TO_REDIS_SCRIPT);
        } catch (Exception e) {
            throw new RuntimeException("Failed to init persisted device messages cache service!", e);
        }
    }

    @Override
    public CompletionStage<Integer> saveAndReturnPreviousPacketId(String clientId, List<DevicePublishMsg> devicePublishMessages, boolean failOnConflict) {
        if (log.isTraceEnabled()) {
            log.trace("Save persisted messages, clientId - {}, devicePublishMessages size - {}", clientId, devicePublishMessages.size());
        }
        String messagesCacheKeyStr = ClientIdMessagesCacheKey.toStringKey(clientId, cachePrefix);
        String lastPacketIdKeyStr = ClientIdLastPacketIdCacheKey.toStringKey(clientId, cachePrefix);
        String messagesStr = JacksonUtil.toString(devicePublishMessages);
        String messagesLimitStr = String.valueOf(messagesLimit);
        String defaultTtlStr = String.valueOf(defaultTtl);
        RedisFuture<Long> prevPacketIdFuture = connectionManager.evalShaAsync(ADD_MESSAGES_SCRIPT_SHA, ScriptOutputType.INTEGER,
                new String[]{messagesCacheKeyStr, lastPacketIdKeyStr}, messagesLimitStr, messagesStr, defaultTtlStr);
        return prevPacketIdFuture.exceptionallyCompose(throwable -> {
            if (throwable instanceof RedisNoScriptException) {
                CompletableFuture<Void> loadScriptFuture = processLoadScriptAsync(throwable, ADD_MESSAGES_SCRIPT_SHA, ADD_MESSAGES_SCRIPT);
                CompletableFuture<Long> retryFuture = loadScriptFuture.thenCompose(__ ->
                        connectionManager.evalShaAsync(ADD_MESSAGES_SCRIPT_SHA, ScriptOutputType.INTEGER,
                                new String[]{messagesCacheKeyStr, lastPacketIdKeyStr}, messagesLimitStr, messagesStr, defaultTtlStr));
                return retryFuture.exceptionallyCompose(retryThrowable -> {
                    log.debug("Falling back to eval due to exception on retry sha evaluation: ", retryThrowable);
                    return connectionManager.evalShaAsync(ADD_MESSAGES_SCRIPT, ScriptOutputType.INTEGER,
                            new String[]{messagesCacheKeyStr, lastPacketIdKeyStr}, messagesLimitStr, messagesStr, defaultTtlStr);
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
        String messagesCacheKeyStr = ClientIdMessagesCacheKey.toStringKey(clientId, cachePrefix);
        RedisFuture<List<String>> messagesFuture = connectionManager.evalShaAsync(GET_MESSAGES_SCRIPT_SHA, ScriptOutputType.MULTI,
                new String[]{messagesCacheKeyStr}, String.valueOf(messagesLimit));
        return messagesFuture.exceptionallyCompose(throwable -> {
            if (throwable instanceof RedisNoScriptException) {
                CompletableFuture<Void> loadScriptFuture = processLoadScriptAsync(throwable, GET_MESSAGES_SCRIPT_SHA, GET_MESSAGES_SCRIPT);
                CompletableFuture<List<String>> retryFuture = loadScriptFuture.thenCompose(__ ->
                        connectionManager.evalShaAsync(GET_MESSAGES_SCRIPT_SHA, ScriptOutputType.MULTI,
                                new String[]{messagesCacheKeyStr}, String.valueOf(messagesLimit)));
                return retryFuture.exceptionallyCompose(retryThrowable -> {
                    log.debug("Falling back to eval due to exception on retry sha evaluation: ", retryThrowable);
                    return connectionManager.evalShaAsync(GET_MESSAGES_SCRIPT, ScriptOutputType.MULTI,
                            new String[]{messagesCacheKeyStr}, String.valueOf(messagesLimit));
                });
            }
            throw new CompletionException(throwable);
        }).thenApply(messages ->
                messages.stream()
                        .map(messageStr -> JacksonUtil.fromString(messageStr, DevicePublishMsg.class))
                        .toList());
    }

    @Override
    public CompletionStage<String> removePersistedMessages(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("Removing persisted messages, clientId - {}", clientId);
        }
        String messagesCacheKeyStr = ClientIdMessagesCacheKey.toStringKey(clientId, cachePrefix);
        String lastPacketIdKeyStr = ClientIdLastPacketIdCacheKey.toStringKey(clientId, cachePrefix);
        RedisFuture<String> removeFuture = connectionManager.evalShaAsync(REMOVE_MESSAGES_SCRIPT_SHA, ScriptOutputType.STATUS, messagesCacheKeyStr, lastPacketIdKeyStr);
        return removeFuture.exceptionallyCompose(throwable -> {
            if (throwable instanceof RedisNoScriptException) {
                CompletableFuture<Void> loadScriptFuture = processLoadScriptAsync(throwable, REMOVE_MESSAGES_SCRIPT_SHA, REMOVE_MESSAGES_SCRIPT);
                CompletableFuture<String> retryFuture = loadScriptFuture.thenCompose(__ ->
                        connectionManager.evalShaAsync(REMOVE_MESSAGES_SCRIPT_SHA, ScriptOutputType.STATUS, messagesCacheKeyStr, lastPacketIdKeyStr));
                return retryFuture.exceptionallyCompose(retryThrowable -> {
                    log.debug("Falling back to eval due to exception on retry sha evaluation: ", retryThrowable);
                    return connectionManager.evalAsync(REMOVE_MESSAGES_SCRIPT, ScriptOutputType.STATUS, messagesCacheKeyStr, lastPacketIdKeyStr);
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
        String messagesCacheKeyStr = ClientIdMessagesCacheKey.toStringKey(clientId, cachePrefix);
        String packetIdStr = String.valueOf(packetId);
        RedisFuture<String> removeFuture = connectionManager.evalShaAsync(REMOVE_MESSAGE_SCRIPT_SHA, ScriptOutputType.STATUS,
                new String[]{messagesCacheKeyStr}, packetIdStr);
        return removeFuture.exceptionallyCompose(throwable -> {
            if (throwable instanceof RedisNoScriptException) {
                CompletableFuture<Void> loadScriptFuture = processLoadScriptAsync(throwable, REMOVE_MESSAGE_SCRIPT_SHA, REMOVE_MESSAGE_SCRIPT);
                CompletableFuture<String> retryFuture = loadScriptFuture.thenCompose(__ ->
                        connectionManager.evalShaAsync(REMOVE_MESSAGE_SCRIPT_SHA, ScriptOutputType.STATUS,
                                new String[]{messagesCacheKeyStr}, packetIdStr));
                return retryFuture.exceptionallyCompose(retryThrowable -> {
                    log.debug("Falling back to eval due to exception on retry sha evaluation: ", retryThrowable);
                    return connectionManager.evalAsync(Objects.requireNonNull(REMOVE_MESSAGE_SCRIPT), ScriptOutputType.STATUS,
                            new String[]{messagesCacheKeyStr}, packetIdStr);
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
        String messagesCacheKeyStr = ClientIdMessagesCacheKey.toStringKey(clientId, cachePrefix);
        String packetIdStr = String.valueOf(packetId);
        RedisFuture<String> updateFuture = connectionManager.evalShaAsync(UPDATE_PACKET_TYPE_SCRIPT_SHA, ScriptOutputType.STATUS,
                new String[]{messagesCacheKeyStr}, packetIdStr);
        return updateFuture.exceptionallyCompose(throwable -> {
            if (throwable instanceof RedisNoScriptException) {
                CompletableFuture<Void> loadScriptFuture = processLoadScriptAsync(throwable, UPDATE_PACKET_TYPE_SCRIPT_SHA, UPDATE_PACKET_TYPE_SCRIPT);
                CompletableFuture<String> retryFuture = loadScriptFuture.thenCompose(__ ->
                        connectionManager.evalShaAsync(UPDATE_PACKET_TYPE_SCRIPT_SHA, ScriptOutputType.STATUS,
                                new String[]{messagesCacheKeyStr}, packetIdStr));
                return retryFuture.exceptionallyCompose(retryThrowable -> {
                    log.debug("Falling back to eval due to exception on retry sha evaluation: ", retryThrowable);
                    return connectionManager.evalAsync(UPDATE_PACKET_TYPE_SCRIPT, ScriptOutputType.STATUS,
                            new String[]{messagesCacheKeyStr}, packetIdStr);
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
        String lastPacketIdKeyStr = ClientIdLastPacketIdCacheKey.toStringKey(clientId, cachePrefix);
        RedisFuture<String> future = connectionManager.getAsync(lastPacketIdKeyStr);
        return future.thenApply(value -> value == null ? 0 : Integer.parseInt(value));
    }

    @Override
    public CompletionStage<Long> removeLastPacketId(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("Remove last packet id, clientId - {}", clientId);
        }
        String lastPacketIdKeyStr = ClientIdLastPacketIdCacheKey.toStringKey(clientId, cachePrefix);
        return connectionManager.delAsync(lastPacketIdKeyStr);
    }

    @Override
    public CompletionStage<String> saveLastPacketId(String clientId, int lastPacketId) {
        if (log.isTraceEnabled()) {
            log.trace("Save last packet id, clientId - {}", clientId);
        }
        String lastPacketIdKeyStr = ClientIdLastPacketIdCacheKey.toStringKey(clientId, cachePrefix);
        return connectionManager.setAsync(lastPacketIdKeyStr, String.valueOf(lastPacketId));
    }

    @Override
    public void importFromCsvFile(Path filePath) {
        if (!installProfileActive) {
            throw new RuntimeException("Import from CSV file can be executed during upgrade only!");
        }
        if (log.isTraceEnabled()) {
            log.trace("Import from csv file: {}", filePath);
        }
        migrateMessagesToRedis(filePath);
    }

    private void migrateMessagesToRedis(Path filePath) {
        var clientIdToMsgMap = new HashMap<String, List<DevicePublishMsg>>();
        consumeCsvRecords(filePath, record -> {
            String clientId = record.get("client_id");
            try {
                var messages = clientIdToMsgMap.computeIfAbsent(clientId, k -> new ArrayList<>());
                messages.add(DevicePublishMsgUtil.fromCsvRecord(record, defaultTtl));
                if (messages.size() >= IMPORT_TO_REDIS_BATCH_SIZE) {
                    writeBatchToRedis(clientId, messages);
                    messages.clear();
                }
            } catch (DecoderException e) {
                throw new RuntimeException("Failed to deserialize message for client: " + clientId, e);
            }
        });
        clientIdToMsgMap.forEach((clientId, messages) -> {
            if (!messages.isEmpty()) {
                writeBatchToRedis(clientId, messages);
            }
            log.info("All messages from client {} have been migrated!", clientId);
        });
    }

    private void writeBatchToRedis(String clientId, List<DevicePublishMsg> messages) {
        log.info("[{}] Adding {} messages to Redis ...", clientId, messages.size());
        String messagesCacheKeyStr = ClientIdMessagesCacheKey.toStringKey(clientId, cachePrefix);
        String lastPacketIdKeyStr = ClientIdLastPacketIdCacheKey.toStringKey(clientId, cachePrefix);
        String messagesStr = JacksonUtil.toString(messages);
        String defaultTtlStr = String.valueOf(defaultTtl);
        RedisFuture<String> migrateFuture = connectionManager.evalShaAsync(MIGRATE_FROM_POSTGRES_TO_REDIS_SCRIPT_SHA, ScriptOutputType.STATUS,
                new String[]{messagesCacheKeyStr, lastPacketIdKeyStr}, messagesStr, defaultTtlStr);
        migrateFuture.exceptionallyCompose(throwable -> {
            if (throwable instanceof RedisNoScriptException) {
                CompletableFuture<Void> loadScriptFuture = processLoadScriptAsync(throwable, MIGRATE_FROM_POSTGRES_TO_REDIS_SCRIPT_SHA, MIGRATE_FROM_POSTGRES_TO_REDIS_SCRIPT);
                CompletableFuture<String> retryFuture = loadScriptFuture.thenCompose(__ ->
                        connectionManager.evalShaAsync(MIGRATE_FROM_POSTGRES_TO_REDIS_SCRIPT_SHA, ScriptOutputType.STATUS,
                                new String[]{messagesCacheKeyStr, lastPacketIdKeyStr}, messagesStr, defaultTtlStr));
                return retryFuture.exceptionallyCompose(retryThrowable -> {
                    log.debug("Falling back to eval due to exception on retry sha evaluation: ", retryThrowable);
                    return connectionManager.evalAsync(MIGRATE_FROM_POSTGRES_TO_REDIS_SCRIPT, ScriptOutputType.STATUS,
                            new String[]{messagesCacheKeyStr, lastPacketIdKeyStr}, messagesStr, defaultTtlStr);
                });
            }
            throw new CompletionException(throwable);
        }).whenComplete((s, throwable) -> {
            if (throwable != null) {
                log.error("Failed to migrate {} messages for client {}", messages.size(), clientId, throwable);
                return;
            }
            log.info("[{}] Successfully added {} messages to Redis!", clientId, messages.size());
        });
    }

    private void consumeCsvRecords(Path filePath, Consumer<CSVRecord> csvRecordConsumer) {
        try (CSVParser csvParser = new CSVParser(Files.newBufferedReader(filePath), IMPORT_CSV_FORMAT)) {
            for (CSVRecord record : csvParser) {
                csvRecordConsumer.accept(record);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read CSV file: " + filePath, e);
        }
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

}
