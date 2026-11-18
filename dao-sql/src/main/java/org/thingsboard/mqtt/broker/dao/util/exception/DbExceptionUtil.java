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
package org.thingsboard.mqtt.broker.dao.util.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.thingsboard.mqtt.broker.dao.exception.EntityConstraintViolationException;

public class DbExceptionUtil {

    /**
     * Translates a Hibernate constraint violation (found anywhere in the cause chain of {@code e}) to the
     * persistence-neutral {@link EntityConstraintViolationException}. Returns {@code e} itself otherwise.
     */
    public static RuntimeException translate(RuntimeException e) {
        for (Throwable current = e; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return new EntityConstraintViolationException(violation.getConstraintName(), violation);
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return e;
    }

}
