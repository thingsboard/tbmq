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
package org.thingsboard.mqtt.broker.common.data.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.security.http.HttpAuthCallback;

import java.util.function.Consumer;

public class CallbackUtil {

    public static final BasicCallback EMPTY = new BasicCallback() {
        @Override
        public void onSuccess() {
        }

        @Override
        public void onFailure(Throwable t) {
        }
    };

    public static BasicCallback createCallback(Runnable onSuccess, Consumer<Throwable> onFailure) {
        return new BasicCallback() {
            @Override
            public void onSuccess() {
                if (onSuccess != null) {
                    onSuccess.run();
                }
            }

            @Override
            public void onFailure(Throwable t) {
                if (onFailure != null) {
                    onFailure.accept(t);
                }
            }
        };
    }

    public static HttpAuthCallback createHttpCallback(Consumer<ResponseEntity<JsonNode>> onSuccess, Consumer<Throwable> onFailure) {
        return new HttpAuthCallback() {
            @Override
            public void onSuccess(ResponseEntity<JsonNode> result) {
                if (onSuccess != null) {
                    onSuccess.accept(result);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                if (onFailure != null) {
                    onFailure.accept(t);
                }
            }
        };
    }
}
