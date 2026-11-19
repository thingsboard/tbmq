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

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.dao.client.GenericClientSessionCtxService;
import org.thingsboard.mqtt.broker.dao.client.generic.GenericClientSessionCtxDao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public abstract class AbstractGenericClientSessionCtxContractTest extends AbstractContractTest {

    @Autowired
    protected GenericClientSessionCtxService genericClientSessionCtxService;

    @Autowired
    protected GenericClientSessionCtxDao genericClientSessionCtxDao;

    @Test
    public void givenNoContext_whenRemoveInDao_thenDoesNotThrow() {
        assertThatCode(() -> genericClientSessionCtxDao.remove("missing")).doesNotThrowAnyException();
    }

    @Test
    public void givenNoContext_whenDelete_thenNothingHappens() {
        genericClientSessionCtxService.deleteGenericClientSessionCtx("missing");

        assertThat(genericClientSessionCtxService.findGenericClientSessionCtx("missing")).isEmpty();
    }

}
