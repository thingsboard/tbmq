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

import { Directive, Input, OnDestroy } from '@angular/core';
import { Subject } from 'rxjs';

@Directive()
export abstract class MqttAuthenticationProviderForm implements OnDestroy {

  @Input()
  disabled: boolean;

  protected destroy$ = new Subject<void>();

  // Set by a subclass when the stored value holds a client type the edition does not allow (e.g. APPLICATION in Lite).
  protected clientTypeResetPending = false;

  // Resets the stored client type once the form is editable, so that view mode never marks the parent form dirty.
  protected scheduleClientTypeReset() {
    if (this.clientTypeResetPending) {
      setTimeout(() => {
        if (this.clientTypeResetPending && !this.disabled) {
          this.clientTypeResetPending = false;
          this.resetClientType();
        }
      }, 0);
    }
  }

  // Overridden by the forms that carry a client type.
  protected resetClientType(): void { }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
