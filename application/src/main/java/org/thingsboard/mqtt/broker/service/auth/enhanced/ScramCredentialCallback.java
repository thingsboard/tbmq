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
package org.thingsboard.mqtt.broker.service.auth.enhanced;

import javax.security.auth.callback.Callback;

/**
 * Callback through which a {@link javax.security.auth.callback.CallbackHandler} hands the stored credential of the
 * user to {@link TbScramSaslServer}.
 */
public class ScramCredentialCallback implements Callback {

    private ScramCredential scramCredential;

    public ScramCredential getScramCredential() {
        return scramCredential;
    }

    public void setScramCredential(ScramCredential scramCredential) {
        this.scramCredential = scramCredential;
    }

}
