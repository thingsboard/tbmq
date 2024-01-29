/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.service;

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.BaseData;
import org.thingsboard.mqtt.broker.common.data.StringUtils;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public abstract class DataValidator<D extends BaseData> {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    public void validate(D data) {
        try {
            if (data == null) {
                throw new DataValidationException("Data object can't be null!");
            }

            ConstraintValidator.validateFields(data);

            validateDataImpl(data);
            if (data.getId() == null) {
                validateCreate(data);
            } else {
                validateUpdate(data);
            }
        } catch (DataValidationException e) {
            log.error("Data object is invalid: [{}]", e.getMessage());
            throw e;
        }
    }

    protected void validateDataImpl(D data) {
    }

    protected void validateCreate(D data) {
    }

    protected void validateUpdate(D data) {
    }

    public void validateString(String exceptionPrefix, String name) {
        if (StringUtils.isEmpty(name) || name.trim().isEmpty()) {
            throw new DataValidationException(exceptionPrefix + " should be specified!");
        }
        if (StringUtils.contains0x00(name)) {
            throw new DataValidationException(exceptionPrefix + " should not contain 0x00 symbol!");
        }
    }

    protected boolean isSameData(D existentData, D actualData) {
        return actualData.getId() != null && existentData.getId().equals(actualData.getId());
    }

    public static void validateEmail(String email) {
        if (!isValidEmail(email)) {
            throw new DataValidationException("Invalid email address format '" + email + "'!");
        }
    }

    private static boolean isValidEmail(String email) {
        if (email == null) {
            return false;
        }

        Matcher emailMatcher = EMAIL_PATTERN.matcher(email);
        return emailMatcher.matches();
    }
}
