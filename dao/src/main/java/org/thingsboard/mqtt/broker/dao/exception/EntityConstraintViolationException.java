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
package org.thingsboard.mqtt.broker.dao.exception;

import org.springframework.dao.DataIntegrityViolationException;

import java.io.Serial;
import java.util.Optional;

/**
 * Persistence-neutral report of a violated unique/foreign-key constraint. Stays a
 * {@link DataIntegrityViolationException} (and so a {@code DataAccessException}), so callers that
 * handle data access failures generically keep working when no service names the constraint.
 */
public class EntityConstraintViolationException extends DataIntegrityViolationException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String constraintName;

    public EntityConstraintViolationException(String constraintName, Throwable cause) {
        super(cause != null && cause.getMessage() != null ? cause.getMessage() : "Unique constraint violated: " + constraintName, cause);
        this.constraintName = constraintName;
    }

    public String getConstraintName() {
        return constraintName;
    }

    public static Optional<EntityConstraintViolationException> find(Throwable t) {
        for (Throwable current = t; current != null; current = current.getCause()) {
            if (current instanceof EntityConstraintViolationException violation) {
                return Optional.of(violation);
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return Optional.empty();
    }

    public static boolean isViolationOf(Throwable t, String constraintName) {
        return find(t)
                .map(EntityConstraintViolationException::getConstraintName)
                .filter(name -> name.equalsIgnoreCase(constraintName))
                .isPresent();
    }

}
