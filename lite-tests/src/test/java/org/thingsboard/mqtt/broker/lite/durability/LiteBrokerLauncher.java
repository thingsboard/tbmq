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
package org.thingsboard.mqtt.broker.lite.durability;

import org.thingsboard.mqtt.broker.ThingsboardMqttBrokerApplication;

import java.io.IOException;
import java.io.InputStream;

/**
 * The main class of the broker that {@link LiteBrokerProcess} starts: runs {@link ThingsboardMqttBrokerApplication}
 * and halts the JVM once its standard input ends. The test JVM holds the other end of that pipe, so the broker does
 * not outlive the test JVM, even one that was killed and ran no shutdown hook.
 * <p>
 * The class is copied alone to a directory of its own on the broker's classpath: the test classes must not reach the
 * broker, which scans its whole package for beans.
 */
public final class LiteBrokerLauncher {

    private static final int TEST_JVM_GONE_EXIT_CODE = 1;

    private LiteBrokerLauncher() {
    }

    public static void main(String[] args) {
        Thread watchdog = new Thread(LiteBrokerLauncher::haltAtEndOfInput, "test-jvm-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        ThingsboardMqttBrokerApplication.main(args);
    }

    private static void haltAtEndOfInput() {
        InputStream in = System.in;
        try {
            while (in.read() != -1) {
                // the test JVM writes nothing: the pipe only tells that it is still there
            }
        } catch (IOException e) {
            // the pipe broke, which also means the test JVM is gone
        }
        System.err.println("The test JVM that started this broker is gone: halting");
        Runtime.getRuntime().halt(TEST_JVM_GONE_EXIT_CODE);
    }

}
