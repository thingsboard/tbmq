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

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.connection.jedis.JedisClusterConnection;
import org.springframework.data.redis.connection.jedis.JedisConnection;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.util.JedisClusterCRC16;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DeviceMsgServiceImpl implements DeviceMsgService {

    private static final int IMPORT_TO_REDIS_BATCH_SIZE = 1000;

    private static final JedisPool MOCK_POOL = new JedisPool(); //non-null pool required for JedisConnection to trigger closing jedis connection

    private static final RedisSerializer<String> stringSerializer = StringRedisSerializer.UTF_8;

    private static final CSVFormat IMPORT_CSV_FORMAT = CSVFormat.Builder.create()
            .setHeader().setSkipHeaderRecord(true).build();

    private static final byte[] ADD_MESSAGES_SCRIPT_SHA = stringSerializer.serialize("1a36112b30eda656ff34629a18d4890499a79256");
    private static final byte[] GET_MESSAGES_SCRIPT_SHA = stringSerializer.serialize("e083e5645a5f268448aca2ec1d3150ee6de510ef");
    private static final byte[] REMOVE_MESSAGES_SCRIPT_SHA = stringSerializer.serialize("a619f42eb693ea732763d878dd59dff513a295c7");
    private static final byte[] REMOVE_MESSAGE_SCRIPT_SHA = stringSerializer.serialize("038e09c6e313eab0d5be4f31361250f4179bc38c");
    private static final byte[] UPDATE_PACKET_TYPE_SCRIPT_SHA = stringSerializer.serialize("958139aa4015911c82ddd423ff408b6638805081");
    private static final byte[] MIGRATE_FROM_POSTGRES_TO_REDIS_SCRIPT_SHA = stringSerializer.serialize("c67a939d71d127455883af90edbc578f0ce22b48");

    private static final byte[] ADD_MESSAGES_SCRIPT = stringSerializer.serialize("""
            local messagesKey = KEYS[1]
            local lastPacketIdKey = KEYS[2]
            local maxMessagesSize = tonumber(ARGV[1])
            local messages = cjson.decode(ARGV[2])
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
                -- Store the message as a separate key with TTL
                redis.call('SET', msgKey, msgJson, 'EX', msg.msgExpiryInterval)
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
            """);
    private static final byte[] GET_MESSAGES_SCRIPT = stringSerializer.serialize("""
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
            """);
    private static final byte[] REMOVE_MESSAGES_SCRIPT = stringSerializer.serialize("""
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
            return nil
            """);
    private static final byte[] REMOVE_MESSAGE_SCRIPT = stringSerializer.serialize("""
            local messagesKey = KEYS[1]
            local packetId = ARGV[1]
            -- Construct the message key
            local msgKey = messagesKey .. "_" .. packetId
            -- Remove the message from the sorted set
            redis.call('ZREM', messagesKey, msgKey)
            -- Delete the message key
            redis.call('DEL', msgKey)
            return nil
            """);
    private static final byte[] UPDATE_PACKET_TYPE_SCRIPT = stringSerializer.serialize("""
            local messagesKey = KEYS[1]
            local packetId = ARGV[1]
            -- Construct the message key
            local msgKey = messagesKey .. "_" .. packetId
            -- Fetch the message from the key-value store
            local msgJson = redis.call('GET', msgKey)
            if not msgJson then
                return nil -- Message not found
            end
            -- Decode the JSON message
            local msg = cjson.decode(msgJson)
            -- Update the packet type
            msg.packetType = "PUBREL"
            -- Encode the updated message back to JSON
            local updatedMsgJson = cjson.encode(msg)
            -- Save the updated message back to the key-value store
            redis.call('SET', msgKey, updatedMsgJson)
            return nil
            """);
    private static final byte[] MIGRATE_FROM_POSTGRES_TO_REDIS_SCRIPT = stringSerializer.serialize("""
            local messagesKey = KEYS[1]
            local lastPacketIdKey = KEYS[2]
            local messages = cjson.decode(ARGV[1])
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
                -- Store the message as a separate key with TTL
                redis.call('SET', msgKey, msgJson, 'EX', msg.msgExpiryInterval)
                -- increase the score
                score = score + 1
                redis.call('ZADD', messagesKey, score, msgKey)
                -- Update lastPacketId with the current packetId
                lastPacketId = msg.packetId
            end

            -- Update the last packetId in the key-value store
            redis.call('SET', lastPacketIdKey, lastPacketId)
            
            return nil
            """);

    @Value("${mqtt.persistent-session.device.persisted-messages.ttl}")
    private int defaultTtl;

    @Value("${mqtt.persistent-session.device.persisted-messages.limit}")
    private int messagesLimit;

    @Value("${cache.cache-prefix:}")
    protected String cachePrefix;

    private byte[] messagesLimitBytes;

    private final JedisConnectionFactory connectionFactory;
    private final boolean installProfileActive;

    public DeviceMsgServiceImpl(RedisConnectionFactory redisConnectionFactory, Environment environment) {
        this.connectionFactory = (JedisConnectionFactory) redisConnectionFactory;
        this.installProfileActive = Arrays.asList(environment.getActiveProfiles()).contains("install");
    }

    @PostConstruct
    public void init() {
        if (messagesLimit > 0xffff) {
            throw new IllegalArgumentException("Persisted messages limit can't be greater than 65535!");
        }
        messagesLimitBytes = intToBytes(messagesLimit);
        try (var connection = connectionFactory.getConnection()) {
            loadScript(connection, ADD_MESSAGES_SCRIPT_SHA, ADD_MESSAGES_SCRIPT);
            loadScript(connection, GET_MESSAGES_SCRIPT_SHA, GET_MESSAGES_SCRIPT);
            loadScript(connection, REMOVE_MESSAGES_SCRIPT_SHA, REMOVE_MESSAGES_SCRIPT);
            loadScript(connection, REMOVE_MESSAGE_SCRIPT_SHA, REMOVE_MESSAGE_SCRIPT);
            loadScript(connection, UPDATE_PACKET_TYPE_SCRIPT_SHA, UPDATE_PACKET_TYPE_SCRIPT);
            if (!installProfileActive) {
                return;
            }
            loadScript(connection, MIGRATE_FROM_POSTGRES_TO_REDIS_SCRIPT_SHA,
                    MIGRATE_FROM_POSTGRES_TO_REDIS_SCRIPT);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to init persisted device messages cache service!", t);
        }
    }

    @Override
    public int saveAndReturnPreviousPacketId(String clientId, List<DevicePublishMsg> devicePublishMessages, boolean failOnConflict) {
        if (log.isTraceEnabled()) {
            log.trace("Save persisted messages, clientId - {}, devicePublishMessages size - {}", clientId, devicePublishMessages.size());
        }
        var messages = devicePublishMessages.stream()
                .map(devicePublishMsg -> new DevicePublishMsgEntity(devicePublishMsg, defaultTtl))
                .collect(Collectors.toCollection(ArrayList::new));
        byte[] rawLastPacketIdKey = toLastPacketIdKey(clientId);
        byte[] rawMessagesKey = toMessagesCacheKey(clientId);
        byte[] messagesBytes = JacksonUtil.writeValueAsBytes(messages);
        try (var connection = getConnection(rawMessagesKey)) {
            Long prevPacketId;
            try {
                prevPacketId = connection.scriptingCommands().evalSha(
                        Objects.requireNonNull(ADD_MESSAGES_SCRIPT_SHA),
                        ReturnType.INTEGER,
                        2,
                        rawMessagesKey,
                        rawLastPacketIdKey,
                        messagesLimitBytes,
                        messagesBytes
                );
            } catch (InvalidDataAccessApiUsageException e) {
                loadScript(connection, ADD_MESSAGES_SCRIPT_SHA, ADD_MESSAGES_SCRIPT);
                try {
                    prevPacketId = connection.scriptingCommands().evalSha(
                            Objects.requireNonNull(ADD_MESSAGES_SCRIPT_SHA),
                            ReturnType.INTEGER,
                            2,
                            rawMessagesKey,
                            rawLastPacketIdKey,
                            messagesLimitBytes,
                            messagesBytes
                    );
                } catch (InvalidDataAccessApiUsageException ignored) {
                    log.debug("Slowly executing eval instead of fast evalSha [{}] due to exception throwing on sha evaluation: ", connection, e);
                    prevPacketId = connection.scriptingCommands().eval(
                            Objects.requireNonNull(ADD_MESSAGES_SCRIPT),
                            ReturnType.INTEGER,
                            2,
                            rawMessagesKey,
                            rawLastPacketIdKey,
                            messagesLimitBytes,
                            messagesBytes
                    );
                }
            }
            return prevPacketId != null ? prevPacketId.intValue() : 0;
        }
    }

    @Override
    public List<DevicePublishMsg> findPersistedMessages(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("Find persisted messages, clientId - {}", clientId);
        }
        byte[] rawMessagesKey = toMessagesCacheKey(clientId);
        try (var connection = getConnection(rawMessagesKey)) {
            List<byte[]> messagesBytes;
            try {
                messagesBytes = connection.scriptingCommands().evalSha(
                        Objects.requireNonNull(GET_MESSAGES_SCRIPT_SHA),
                        ReturnType.MULTI,
                        1,
                        rawMessagesKey,
                        messagesLimitBytes
                );
            } catch (InvalidDataAccessApiUsageException e) {
                loadScript(connection, GET_MESSAGES_SCRIPT_SHA, GET_MESSAGES_SCRIPT);
                try {
                    messagesBytes = connection.scriptingCommands().evalSha(
                            Objects.requireNonNull(GET_MESSAGES_SCRIPT_SHA),
                            ReturnType.MULTI,
                            1,
                            rawMessagesKey,
                            messagesLimitBytes
                    );
                } catch (InvalidDataAccessApiUsageException ignored) {
                    log.debug("Slowly executing eval instead of fast evalSha [{}] due to exception throwing on sha evaluation: ", connection, e);
                    messagesBytes = connection.scriptingCommands().eval(
                            Objects.requireNonNull(GET_MESSAGES_SCRIPT),
                            ReturnType.MULTI,
                            1,
                            rawMessagesKey,
                            messagesLimitBytes
                    );
                }
            }
            return Objects.requireNonNull(messagesBytes)
                    .stream().map(DevicePublishMsgEntity::fromBytes)
                    .toList();
        }
    }

    @Override
    public void removePersistedMessages(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("Removing persisted messages, clientId - {}", clientId);
        }
        byte[] rawMessagesKey = toMessagesCacheKey(clientId);
        byte[] rawLastPacketIdKey = toLastPacketIdKey(clientId);
        try (var connection = getConnection(rawMessagesKey)) {
            try {
                connection.scriptingCommands().evalSha(
                        Objects.requireNonNull(REMOVE_MESSAGES_SCRIPT_SHA),
                        ReturnType.VALUE,
                        2,
                        rawMessagesKey,
                        rawLastPacketIdKey
                );
            } catch (InvalidDataAccessApiUsageException e) {
                loadScript(connection, REMOVE_MESSAGES_SCRIPT_SHA, REMOVE_MESSAGES_SCRIPT);
                try {
                    connection.scriptingCommands().evalSha(
                            Objects.requireNonNull(REMOVE_MESSAGES_SCRIPT_SHA),
                            ReturnType.VALUE,
                            2,
                            rawMessagesKey,
                            rawLastPacketIdKey
                    );
                } catch (InvalidDataAccessApiUsageException ignored) {
                    log.debug("Slowly executing eval instead of fast evalSha [{}] due to exception throwing on sha evaluation: ", connection, e);
                    connection.scriptingCommands().eval(
                            Objects.requireNonNull(REMOVE_MESSAGES_SCRIPT),
                            ReturnType.VALUE,
                            2,
                            rawMessagesKey,
                            rawLastPacketIdKey
                    );
                }
            }
        } catch (Exception e) {
            log.error("Failed to remove persisted messages, clientId - {}", clientId, e);
        }
    }

    @Override
    public void removePersistedMessage(String clientId, int packetId) {
        if (log.isTraceEnabled()) {
            log.trace("Removing persisted message, clientId - {}, packetId - {}", clientId, packetId);
        }
        byte[] rawMessagesKey = toMessagesCacheKey(clientId);
        byte[] packetIdBytes = intToBytes(packetId);
        try (var connection = getConnection(rawMessagesKey)) {
            try {
                connection.scriptingCommands().evalSha(
                        Objects.requireNonNull(REMOVE_MESSAGE_SCRIPT_SHA),
                        ReturnType.VALUE,
                        1,
                        rawMessagesKey,
                        packetIdBytes
                );
            } catch (InvalidDataAccessApiUsageException e) {
                loadScript(connection, REMOVE_MESSAGE_SCRIPT_SHA, REMOVE_MESSAGE_SCRIPT);
                try {
                    connection.scriptingCommands().evalSha(
                            Objects.requireNonNull(REMOVE_MESSAGE_SCRIPT_SHA),
                            ReturnType.VALUE,
                            1,
                            rawMessagesKey,
                            packetIdBytes
                    );
                } catch (InvalidDataAccessApiUsageException ignored) {
                    log.debug("Slowly executing eval instead of fast evalSha [{}] due to exception throwing on sha evaluation: ", connection, e);
                    connection.scriptingCommands().eval(
                            Objects.requireNonNull(REMOVE_MESSAGE_SCRIPT),
                            ReturnType.VALUE,
                            1,
                            rawMessagesKey,
                            packetIdBytes
                    );
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove persisted message, clientId - " + clientId + " packetId - " + packetId, e);
        }
    }

    @Override
    public void updatePacketReceived(String clientId, int packetId) {
        if (log.isTraceEnabled()) {
            log.trace("Update packet received, clientId - {}, packetId - {}", clientId, packetId);
        }
        byte[] rawMessagesKey = toMessagesCacheKey(clientId);
        byte[] packetIdBytes = intToBytes(packetId);
        try (var connection = getConnection(rawMessagesKey)) {
            try {
                connection.scriptingCommands().evalSha(
                        Objects.requireNonNull(UPDATE_PACKET_TYPE_SCRIPT_SHA),
                        ReturnType.VALUE,
                        1,
                        rawMessagesKey,
                        packetIdBytes
                );
            } catch (InvalidDataAccessApiUsageException e) {
                loadScript(connection, UPDATE_PACKET_TYPE_SCRIPT_SHA, UPDATE_PACKET_TYPE_SCRIPT);
                try {
                    connection.scriptingCommands().evalSha(
                            Objects.requireNonNull(UPDATE_PACKET_TYPE_SCRIPT_SHA),
                            ReturnType.VALUE,
                            1,
                            rawMessagesKey,
                            packetIdBytes
                    );
                } catch (InvalidDataAccessApiUsageException ingored) {
                    connection.scriptingCommands().eval(
                            Objects.requireNonNull(UPDATE_PACKET_TYPE_SCRIPT),
                            ReturnType.VALUE,
                            1,
                            rawMessagesKey,
                            packetIdBytes
                    );
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update packet type, clientId - " + clientId + " packetId - " + packetId, e);
        }
    }

    @Override
    public int getLastPacketId(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("Get last packet id, clientId - {}", clientId);
        }
        byte[] rawLastPacketIdKey = toLastPacketIdKey(clientId);
        try (var connection = getConnection(rawLastPacketIdKey)) {
            byte[] rawValue = connection.stringCommands().get(rawLastPacketIdKey);
            if (rawValue == null) {
                return 0;
            }
            return Integer.parseInt(Objects.requireNonNull(stringSerializer.deserialize(rawValue)));
        }
    }

    @Override
    public void removeLastPacketId(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("Remove last packet id, clientId - {}", clientId);
        }
        byte[] rawLastPacketIdKey = toLastPacketIdKey(clientId);
        try (var connection = getConnection(rawLastPacketIdKey)) {
            connection.keyCommands().del(rawLastPacketIdKey);
        }
    }

    @Override
    public void saveLastPacketId(String clientId, int lastPacketId) {
        if (log.isTraceEnabled()) {
            log.trace("Save last packet id, clientId - {}", clientId);
        }
        byte[] rawLastPacketIdKey = toLastPacketIdKey(clientId);
        byte[] rawLastPacketIdValue = intToBytes(lastPacketId);
        try (var connection = getConnection(rawLastPacketIdKey)) {
            connection.stringCommands().set(rawLastPacketIdKey, rawLastPacketIdValue);
        }
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
        var clientIdToMsgMap = new HashMap<String, List<DevicePublishMsgEntity>>();
        consumeCsvRecords(filePath, record -> {
            String clientId = record.get("client_id");
            try {
                var messages = clientIdToMsgMap.computeIfAbsent(clientId, k -> new ArrayList<>());
                messages.add(DevicePublishMsgEntity.fromCsvRecord(record, defaultTtl));
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

    private void writeBatchToRedis(String clientId, List<DevicePublishMsgEntity> messages) {
        log.info("[{}] Adding {} messages to Redis ...", clientId, messages.size());
        byte[] rawMessagesKey = toMessagesCacheKey(clientId);
        byte[] rawLastPacketIdKey = toLastPacketIdKey(clientId);
        byte[] messagesBytes = JacksonUtil.writeValueAsBytes(messages);
        try (var connection = getConnection(rawMessagesKey)) {
            try {
                connection.scriptingCommands().evalSha(
                        Objects.requireNonNull(MIGRATE_FROM_POSTGRES_TO_REDIS_SCRIPT_SHA),
                        ReturnType.VALUE,
                        2,
                        rawMessagesKey,
                        rawLastPacketIdKey,
                        messagesBytes
                );
            } catch (InvalidDataAccessApiUsageException e) {
                loadScript(connection, MIGRATE_FROM_POSTGRES_TO_REDIS_SCRIPT_SHA, MIGRATE_FROM_POSTGRES_TO_REDIS_SCRIPT);
                try {
                    connection.scriptingCommands().evalSha(
                            Objects.requireNonNull(MIGRATE_FROM_POSTGRES_TO_REDIS_SCRIPT_SHA),
                            ReturnType.VALUE,
                            2,
                            rawMessagesKey,
                            rawLastPacketIdKey,
                            messagesBytes
                    );
                } catch (InvalidDataAccessApiUsageException ignored) {
                    log.debug("Slowly executing eval instead of fast evalSha [{}] due to exception throwing on sha evaluation: ", connection, e);
                    connection.scriptingCommands().eval(
                            Objects.requireNonNull(MIGRATE_FROM_POSTGRES_TO_REDIS_SCRIPT),
                            ReturnType.VALUE,
                            2,
                            rawMessagesKey,
                            rawLastPacketIdKey,
                            messagesBytes
                    );
                }
            }
            log.info("[{}] Successfully added {} messages to Redis!", clientId, messages.size());
        } catch (Exception e) {
            throw new RuntimeException("Failed to migrate messages to Redis for client: " + clientId, e);
        }
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

    private byte[] toMessagesCacheKey(String clientId) {
        ClientIdMessagesCacheKey clientIdMessagesCacheKey = new ClientIdMessagesCacheKey(clientId, cachePrefix);
        String stringValue = clientIdMessagesCacheKey.toString();
        byte[] rawKey;
        try {
            rawKey = stringSerializer.serialize(stringValue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (rawKey == null) {
            throw new IllegalArgumentException("Failed to serialize the messages cache key, clientId - " + clientId);
        }
        return rawKey;
    }

    private byte[] toLastPacketIdKey(String clientId) {
        ClientIdLastPacketIdCacheKey clientIdLastPacketIdCacheKey = new ClientIdLastPacketIdCacheKey(clientId, cachePrefix);
        String stringValue = clientIdLastPacketIdCacheKey.toString();
        byte[] rawKey;
        try {
            rawKey = stringSerializer.serialize(stringValue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (rawKey == null) {
            throw new IllegalArgumentException("Failed to serialize the last packet id cache key, clientId - " + clientId);
        }
        return rawKey;
    }

    private byte[] intToBytes(int value) {
        return stringSerializer.serialize(Integer.toString(value));
    }

    private RedisConnection getConnection(byte[] rawKey) {
        if (!connectionFactory.isRedisClusterAware()) {
            return connectionFactory.getConnection();
        }
        RedisConnection connection = connectionFactory.getClusterConnection();

        int slotNum = JedisClusterCRC16.getSlot(rawKey);
        Jedis jedis = new Jedis((((JedisClusterConnection) connection).getNativeConnection().getConnectionFromSlot(slotNum)));

        JedisConnection jedisConnection = new JedisConnection(jedis, MOCK_POOL, jedis.getDB());
        jedisConnection.setConvertPipelineAndTxResults(connectionFactory.getConvertPipelineAndTxResults());

        return jedisConnection;
    }

    private void loadScript(RedisConnection connection, byte[] scriptSha, byte[] script) {
        String scriptShaStr = new String(Objects.requireNonNull(scriptSha));
        log.debug("Loading LUA with expected SHA [{}], connection [{}]", scriptShaStr, connection.getNativeConnection());
        String actualScriptSha = connection.scriptingCommands().scriptLoad(Objects.requireNonNull(script));
        validateSha(scriptSha, scriptShaStr, actualScriptSha, connection);
    }

    private void validateSha(byte[] expectedSha, String expectedShaStr, String actualScriptSha, RedisConnection connection) {
        if (!Arrays.equals(expectedSha, stringSerializer.serialize(actualScriptSha))) {
            log.error("SHA for script is wrong! Expected [{}] actual [{}] connection [{}]", expectedShaStr, actualScriptSha, connection.getNativeConnection());
        }
    }

}
