///
/// Copyright © 2016-2026 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { UntypedFormControl } from '@angular/forms';

const PRIVATE_NETWORK_REGEXP = /^((http|https|pulsar):\/\/)?(127\.|(10\.)|(172\.1[6-9]\.)|(172\.2[0-9]\.)|(172\.3[0-1]\.)|(192\.168\.)|localhost(:[0-9]+)?$)/;

export function privateNetworkAddressValidator(control: UntypedFormControl): { [key: string]: any } | null {
  if (control.value) {
    const host = control.value.trim();
    return !PRIVATE_NETWORK_REGEXP.test(host) ? null : {
      privateNetwork: {
        valid: false
      }
    };
  }
  return null;
}
