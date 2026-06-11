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

import { Component, forwardRef, input, OnDestroy } from '@angular/core';
import {
  ControlValueAccessor,
  FormsModule,
  NG_VALUE_ACCESSOR,
  ReactiveFormsModule,
  UntypedFormBuilder,
  UntypedFormControl
} from '@angular/forms';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatOption, MatSelect } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { ClientLifecycleEventType } from '@shared/models/integration.models';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

@Component({
  selector: 'tb-integration-lifecycle-events',
  templateUrl: './integration-lifecycle-events.component.html',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule, MatFormField, MatLabel, MatSelect, MatOption, TranslateModule],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => IntegrationLifecycleEventsComponent),
      multi: true
    }
  ]
})
export class IntegrationLifecycleEventsComponent implements ControlValueAccessor, OnDestroy {

  showNoSelectionHint = input(false);

  readonly eventTypes = [
    ClientLifecycleEventType.CLIENT_CONNECTED,
    ClientLifecycleEventType.CLIENT_DISCONNECTED,
    ClientLifecycleEventType.CLIENT_SUBSCRIBED,
    ClientLifecycleEventType.CLIENT_UNSUBSCRIBED,
  ];

  readonly eventTypeTranslations: Record<ClientLifecycleEventType, string> = {
    [ClientLifecycleEventType.CLIENT_CONNECTED]:    'integration.client-connected',
    [ClientLifecycleEventType.CLIENT_DISCONNECTED]: 'integration.client-disconnected',
    [ClientLifecycleEventType.CLIENT_SUBSCRIBED]:   'integration.client-subscribed',
    [ClientLifecycleEventType.CLIENT_UNSUBSCRIBED]: 'integration.client-unsubscribed',
  };

  lifecycleEventsFormControl: UntypedFormControl;

  private destroy$ = new Subject<void>();
  private propagateChange = (_value: ClientLifecycleEventType[]) => {};

  constructor(private fb: UntypedFormBuilder) {
    this.lifecycleEventsFormControl = this.fb.control([]);
    this.lifecycleEventsFormControl.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe((value: ClientLifecycleEventType[]) => this.propagateChange(value ?? []));
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get hasSelection(): boolean {
    return (this.lifecycleEventsFormControl.value ?? []).length > 0;
  }

  writeValue(value: ClientLifecycleEventType[] | null): void {
    this.lifecycleEventsFormControl.patchValue(value ?? [], {emitEvent: false});
  }

  registerOnChange(fn: (value: ClientLifecycleEventType[]) => void): void {
    this.propagateChange = fn;
  }

  registerOnTouched(): void {}

  setDisabledState(isDisabled: boolean): void {
    if (isDisabled) {
      this.lifecycleEventsFormControl.disable({emitEvent: false});
    } else {
      this.lifecycleEventsFormControl.enable({emitEvent: false});
    }
  }
}
