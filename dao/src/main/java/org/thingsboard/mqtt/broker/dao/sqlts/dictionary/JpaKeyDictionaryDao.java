/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.sqlts.dictionary;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.dao.JpaAbstractDaoListeningExecutorService;
import org.thingsboard.mqtt.broker.dao.dictionary.KeyDictionaryDao;
import org.thingsboard.mqtt.broker.dao.model.sqlts.dictionary.TsKvDictionary;
import org.thingsboard.mqtt.broker.dao.model.sqlts.dictionary.TsKvDictionaryCompositeKey;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Slf4j
@RequiredArgsConstructor
public class JpaKeyDictionaryDao extends JpaAbstractDaoListeningExecutorService implements KeyDictionaryDao {

    private static final ReentrantLock creationLock = new ReentrantLock();
    private final ConcurrentMap<String, Integer> keyDictionaryMap = new ConcurrentHashMap<>();
    private final TsKvDictionaryRepository keyDictionaryRepository;

    @Override
    public Integer getOrSaveKeyId(String strKey) {
        Integer keyId = keyDictionaryMap.get(strKey);
        if (keyId == null) {
            Optional<TsKvDictionary> tsKvDictionaryOptional = keyDictionaryRepository.findById(new TsKvDictionaryCompositeKey(strKey));
            if (tsKvDictionaryOptional.isEmpty()) {
                creationLock.lock();
                try {
                    keyId = keyDictionaryMap.get(strKey);
                    if (keyId != null) {
                        return keyId;
                    }
                    tsKvDictionaryOptional = keyDictionaryRepository.findById(new TsKvDictionaryCompositeKey(strKey));
                    if (tsKvDictionaryOptional.isEmpty()) {
                        TsKvDictionary keyDictionaryEntry = new TsKvDictionary();
                        keyDictionaryEntry.setKey(strKey);
                        try {
                            TsKvDictionary saved = keyDictionaryRepository.save(keyDictionaryEntry);
                            keyDictionaryMap.put(saved.getKey(), saved.getKeyId());
                            keyId = saved.getKeyId();
                        } catch (DataIntegrityViolationException | ConstraintViolationException e) {
                            tsKvDictionaryOptional = keyDictionaryRepository.findById(new TsKvDictionaryCompositeKey(strKey));
                            TsKvDictionary dictionary = tsKvDictionaryOptional.orElseThrow(() -> new RuntimeException("Failed to get TsKvDictionary entity from DB!"));
                            keyDictionaryMap.put(dictionary.getKey(), dictionary.getKeyId());
                            keyId = dictionary.getKeyId();
                        }
                    } else {
                        keyId = tsKvDictionaryOptional.get().getKeyId();
                    }
                } finally {
                    creationLock.unlock();
                }
            } else {
                keyId = tsKvDictionaryOptional.get().getKeyId();
                keyDictionaryMap.put(strKey, keyId);
            }
        }
        return keyId;
    }

    @Override
    public String getKey(Integer keyId) {
        Optional<TsKvDictionary> byKeyId = keyDictionaryRepository.findByKeyId(keyId);
        return byKeyId.map(TsKvDictionary::getKey).orElse(null);
    }

    @Override
    public Integer getKeyId(String key) {
        return keyDictionaryMap.get(key);
    }
}
