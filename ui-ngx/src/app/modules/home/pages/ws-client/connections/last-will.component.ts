///
/// Copyright © 2016-2025 The Thingsboard Authors
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

import { Component, forwardRef, OnChanges, OnDestroy, OnInit, SimpleChanges, input, model } from '@angular/core';
import { ControlValueAccessor, FormBuilder, NG_VALIDATORS, NG_VALUE_ACCESSOR, UntypedFormGroup, ValidationErrors, Validator, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import {
  LastWillMsg,
  TimeUnitTypeTranslationMap,
  WebSocketConnection,
  WebSocketTimeUnit
} from '@shared/models/ws-client.model';
import { FlexModule } from '@angular/flex-layout/flex';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { TranslateModule } from '@ngx-translate/core';
import { MatInput } from '@angular/material/input';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { ExtendedModule } from '@angular/flex-layout/extended';
import { MatSelect } from '@angular/material/select';

import { MatOption } from '@angular/material/core';
import { ValueInputComponent } from '@shared/components/value-input.component';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { DEFAULT_QOS } from '@shared/models/session.model';
import { takeUntil } from 'rxjs/operators';
import { isDefinedAndNotNull } from '@core/utils';
import { QosSelectComponent } from '@shared/components/qos-select.component';

@Component({
    selector: 'tb-last-will',
    templateUrl: './last-will.component.html',
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => LastWillComponent),
            multi: true
        },
        {
            provide: NG_VALIDATORS,
            useExisting: forwardRef(() => LastWillComponent),
            multi: true,
        }
    ],
    styleUrls: ['./last-will.component.scss'],
    imports: [FormsModule, ReactiveFormsModule, FlexModule, MatFormField, MatLabel, TranslateModule, MatInput, CopyButtonComponent, MatSuffix, ExtendedModule, MatSelect, MatOption, ValueInputComponent, MatSlideToggle, MatIcon, MatTooltip, QosSelectComponent]
})
export class LastWillComponent implements OnInit, ControlValueAccessor, Validator, OnDestroy, OnChanges {

  disabled = model<boolean>();
  mqttVersion = model<number>();
  readonly entity = input<WebSocketConnection>();

  formGroup: UntypedFormGroup;
  timeUnitTypes = Object.keys(WebSocketTimeUnit);
  timeUnitTypeTranslationMap = TimeUnitTypeTranslationMap;

  private destroy$ = new Subject<void>();
  private propagateChange = (v: any) => {};

  constructor(public fb: FormBuilder) {
  }

  ngOnInit() {
    this.initForm();
    this.formGroup.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(value => this.updateModel(value));
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (propName === 'mqttVersion' && change.currentValue) {
          this.mqttVersion.set(change.currentValue);
          this.disableMqtt5Features();
        }
      }
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  validate(): ValidationErrors | null {
    return this.formGroup.valid ? null : {lastWill: true};
  }

  writeValue(value: LastWillMsg): void {
    if (isDefinedAndNotNull(value)) {
      this.formGroup.patchValue(value);
    }
  }

  calcMax(unitControl: string) {
    const messageExpiryInterval = this.formGroup.get(unitControl)?.value;
    switch (messageExpiryInterval) {
      case WebSocketTimeUnit.MILLISECONDS:
        return 4294967295000;
      case WebSocketTimeUnit.SECONDS:
        return 4294967295;
      case WebSocketTimeUnit.MINUTES:
        return 71582788;
      case WebSocketTimeUnit.HOURS:
        return 1193046;
    }
  }

  private initForm() {
    const disabled = this.isNotMqttVersionV5();
    this.formGroup = this.fb.group({
      topic: [null, []],
      payload: [null, []],
      qos: [DEFAULT_QOS, []],
      retain: [false, []],
      willDelayInterval: [{value: 0, disabled}, []],
      willDelayIntervalUnit: [{value: WebSocketTimeUnit.SECONDS, disabled}, []],
      payloadFormatIndicator: [{value: false, disabled}, []],
      msgExpiryInterval: [{value: 0, disabled}, []],
      msgExpiryIntervalUnit: [{value: WebSocketTimeUnit.SECONDS, disabled}, []],
      contentType: [{value: null, disabled}, []],
      responseTopic: [{value: null, disabled}, []],
      correlationData: [{value: null, disabled}, []]
    });
    this.disableMqtt5Features();
  }

  private updateModel(value: LastWillMsg) {
    this.propagateChange(value);
  }

  private disableMqtt5Features() {
    if (this.isNotMqttVersionV5()) {
      this.formGroup.get('willDelayInterval').disable();
      this.formGroup.get('willDelayIntervalUnit').disable();
      this.formGroup.get('payloadFormatIndicator').disable();
      this.formGroup.get('msgExpiryInterval').disable();
      this.formGroup.get('msgExpiryIntervalUnit').disable();
      this.formGroup.get('contentType').disable();
      this.formGroup.get('responseTopic').disable();
      this.formGroup.get('correlationData').disable();
    } else {
      this.formGroup.get('willDelayInterval').enable();
      this.formGroup.get('willDelayIntervalUnit').enable();
      this.formGroup.get('payloadFormatIndicator').enable();
      this.formGroup.get('msgExpiryInterval').enable();
      this.formGroup.get('msgExpiryIntervalUnit').enable();
      this.formGroup.get('contentType').enable();
      this.formGroup.get('responseTopic').enable();
      this.formGroup.get('correlationData').enable();
    }
    this.formGroup.updateValueAndValidity();
  }

  private isNotMqttVersionV5() {
    return this.mqttVersion() !== 5;
  }
}

