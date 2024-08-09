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
import org.springframework.beans.factory.annotation.Value;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DeviceMsgServiceImpl implements DeviceMsgService {

    private static final JedisPool MOCK_POOL = new JedisPool(); //non-null pool required for JedisConnection to trigger closing jedis connection

    private static final RedisSerializer<String> stringSerializer = StringRedisSerializer.UTF_8;

    private static final byte[] ADD_MESSAGES_SCRIPT_SHA = stringSerializer.serialize("cea4fbc467e6f4749bb3170f45b4853e89956a31");
    private static final byte[] GET_MESSAGES_SCRIPT_SHA = stringSerializer.serialize("e083e5645a5f268448aca2ec1d3150ee6de510ef");
    private static final byte[] REMOVE_MESSAGES_SCRIPT_SHA = stringSerializer.serialize("a619f42eb693ea732763d878dd59dff513a295c7");
    private static final byte[] REMOVE_MESSAGE_SCRIPT_SHA = stringSerializer.serialize("038e09c6e313eab0d5be4f31361250f4179bc38c");
    private static final byte[] UPDATE_PACKET_TYPE_SCRIPT_SHA = stringSerializer.serialize("958139aa4015911c82ddd423ff408b6638805081");

    private static final byte[] ADD_MESSAGES_SCRIPT = stringSerializer.serialize("""
            local messagesKey = KEYS[1]
            local lastPacketIdKey = KEYS[2]
            local maxMessagesSize = tonumber(ARGV[1])
            local messages = cjson.decode(ARGV[2])
            -- Fetch the last packetId from the key-value store
            local lastPacketId = tonumber(redis.call('GET', lastPacketIdKey)) or 0
            -- Initialize the score with the last packet ID value
            local score = lastPacketId
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
    public static final byte[] GET_MESSAGES_SCRIPT = stringSerializer.serialize("""
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

    @Value("${mqtt.persistent-session.device.persisted-messages.ttl}")
    private int defaultTtl;

    @Value("${mqtt.persistent-session.device.persisted-messages.limit}")
    private int messagesLimit;

    @Value("${cache.cache-prefix:}")
    protected String cachePrefix;

    private byte[] messagesLimitBytes;

    private final JedisConnectionFactory connectionFactory;

    public DeviceMsgServiceImpl(RedisConnectionFactory redisConnectionFactory) {
        this.connectionFactory = (JedisConnectionFactory) redisConnectionFactory;
    }

    @PostConstruct
    public void init() {
        if (messagesLimit > 0xffff) {
            throw new IllegalArgumentException("Persisted messages limit can't be greater than 65535!");
        }
        messagesLimitBytes = intToBytes(messagesLimit);
        try (var connection = getNonClusterAwareConnection()) {
            loadScript(connection, ADD_MESSAGES_SCRIPT_SHA, ADD_MESSAGES_SCRIPT);
            loadScript(connection, GET_MESSAGES_SCRIPT_SHA, GET_MESSAGES_SCRIPT);
            loadScript(connection, REMOVE_MESSAGES_SCRIPT_SHA, REMOVE_MESSAGES_SCRIPT);
            loadScript(connection, REMOVE_MESSAGE_SCRIPT_SHA, REMOVE_MESSAGE_SCRIPT);
            loadScript(connection, UPDATE_PACKET_TYPE_SCRIPT_SHA, UPDATE_PACKET_TYPE_SCRIPT);
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
                log.debug("Slowly executing eval instead of fast evalSha [{}] due to exception throwing on sha evaluation: ", connection, e);
                messagesBytes = connection.scriptingCommands().eval(
                        Objects.requireNonNull(GET_MESSAGES_SCRIPT),
                        ReturnType.MULTI,
                        1,
                        rawMessagesKey,
                        messagesLimitBytes
                );
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
                log.debug("Slowly executing eval instead of fast evalSha [{}] due to exception throwing on sha evaluation: ", connection, e);
                connection.scriptingCommands().eval(
                        Objects.requireNonNull(REMOVE_MESSAGES_SCRIPT),
                        ReturnType.VALUE,
                        2,
                        rawMessagesKey,
                        rawLastPacketIdKey
                );
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
                log.debug("Slowly executing eval instead of fast evalSha [{}] due to exception throwing on sha evaluation: ", connection, e);
                connection.scriptingCommands().eval(
                        Objects.requireNonNull(REMOVE_MESSAGE_SCRIPT),
                        ReturnType.VALUE,
                        1,
                        rawMessagesKey,
                        packetIdBytes
                );
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
                connection.scriptingCommands().eval(
                        Objects.requireNonNull(UPDATE_PACKET_TYPE_SCRIPT),
                        ReturnType.VALUE,
                        1,
                        rawMessagesKey,
                        packetIdBytes
                );
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

    private RedisConnection getNonClusterAwareConnection() {
        return connectionFactory.getConnection();
    }

    private void loadScript(RedisConnection connection, byte[] scriptSha, byte[] script) {
        String scriptShaStr = new String(Objects.requireNonNull(scriptSha));
        log.debug("Loading LUA with expected SHA [{}], connection [{}]", scriptShaStr, connection.getNativeConnection());
        String actualScriptSha = connection.scriptingCommands().scriptLoad(Objects.requireNonNull(script));
        validateSha(scriptSha, scriptShaStr, actualScriptSha, connection);
    }

    private void validateSha(byte[] expectedSha, String expectedShaStr, String actualAddMessagesScriptSha, RedisConnection connection) {
        if (!Arrays.equals(expectedSha, stringSerializer.serialize(actualAddMessagesScriptSha))) {
            log.error("SHA for script is wrong! Expected [{}] actual [{}] connection [{}]", expectedShaStr, actualAddMessagesScriptSha, connection.getNativeConnection());
        }
    }

}
