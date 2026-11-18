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
package org.thingsboard.mqtt.broker.dao.contract;

import org.junit.runner.RunWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.common.data.id.HasId;

import java.util.Comparator;

/**
 * Base of the contract classes: the checks every storage backend of the dao interfaces must pass. A concrete subclass
 * per backend supplies the Spring context ({@code @ContextConfiguration}) and its test properties, and adds a case only
 * when the behaviour holds on that backend alone, with the reason in a comment.
 */
@RunWith(SpringRunner.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractContractTest {

    public static class IdComparator<D extends HasId> implements Comparator<D> {
        @Override
        public int compare(D o1, D o2) {
            return o1.getId().compareTo(o2.getId());
        }
    }

}
