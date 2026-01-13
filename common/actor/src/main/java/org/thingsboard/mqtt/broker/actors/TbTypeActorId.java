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
package org.thingsboard.mqtt.broker.actors;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.thingsboard.mqtt.broker.common.data.id.ActorType;

import java.util.Objects;

@Getter
@RequiredArgsConstructor
public class TbTypeActorId implements TbActorId {

    private final ActorType type;
    private final String id;

    @Override
    public String toString() {
        return type + "|" + id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TbTypeActorId that = (TbTypeActorId) o;
        return type == that.type &&
                id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id);
    }
}
