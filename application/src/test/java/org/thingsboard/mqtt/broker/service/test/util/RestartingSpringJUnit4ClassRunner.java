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
package org.thingsboard.mqtt.broker.service.test.util;

import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

public class RestartingSpringJUnit4ClassRunner extends SpringJUnit4ClassRunner {
    /**
     * Construct a new {@code SpringJUnit4ClassRunner} and initialize a
     * {@link TestContextManager} to provide Spring testing functionality to
     * standard JUnit tests.
     *
     * @param clazz the test class to be run
     * @see #createTestContextManager(Class)
     */
    public RestartingSpringJUnit4ClassRunner(Class<?> clazz) throws InitializationError {
        super(clazz);
    }

    /**
     * Binds {@link SpringRestarter} to this class's context when the class runs. The runners of all classes are created
     * before the first one runs, so binding at creation would leave the restarter on the last class created.
     */
    @Override
    public void run(RunNotifier notifier) {
        SpringRestarter.getInstance().init(getTestContextManager());
        super.run(notifier);
    }
}
