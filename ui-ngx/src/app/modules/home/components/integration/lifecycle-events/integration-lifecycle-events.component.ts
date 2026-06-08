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

import { ChangeDetectionStrategy, Component, forwardRef } from '@angular/core';
import {
  ControlValueAccessor,
  FormsModule,
  NG_VALUE_ACCESSOR,
  ReactiveFormsModule
} from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { TranslateModule } from '@ngx-translate/core';
import { ClientLifecycleEventType } from '@shared/models/integration.models';

@Component({
  selector: 'tb-integration-lifecycle-events',
  templateUrl: './integration-lifecycle-events.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, MatCheckboxModule, TranslateModule],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => IntegrationLifecycleEventsComponent),
      multi: true
    }
  ]
})
export class IntegrationLifecycleEventsComponent implements ControlValueAccessor {

  readonly eventTypes = [
    ClientLifecycleEventType.CLIENT_CONNECTED,
    ClientLifecycleEventType.CLIENT_DISCONNECTED,
    ClientLifecycleEventType.CLIENT_SUBSCRIBED,
  ];

  readonly eventTypeTranslations: Record<ClientLifecycleEventType, string> = {
    [ClientLifecycleEventType.CLIENT_CONNECTED]:    'integration.client-connected',
    [ClientLifecycleEventType.CLIENT_DISCONNECTED]: 'integration.client-disconnected',
    [ClientLifecycleEventType.CLIENT_SUBSCRIBED]:   'integration.client-subscribed',
  };

  selected = new Set<ClientLifecycleEventType>();
  disabled = false;

  private onChange: (value: ClientLifecycleEventType[]) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: ClientLifecycleEventType[] | null): void {
    this.selected = new Set(value ?? []);
  }

  registerOnChange(fn: (value: ClientLifecycleEventType[]) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  toggle(eventType: ClientLifecycleEventType, checked: boolean): void {
    if (checked) {
      this.selected.add(eventType);
    } else {
      this.selected.delete(eventType);
    }
    this.onChange(Array.from(this.selected));
    this.onTouched();
  }

  isChecked(eventType: ClientLifecycleEventType): boolean {
    return this.selected.has(eventType);
  }
}
