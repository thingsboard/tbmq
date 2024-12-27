///
/// Copyright © 2016-2024 The Thingsboard Authors
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

import { Component, forwardRef, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { ControlValueAccessor, FormBuilder, NG_VALIDATORS, NG_VALUE_ACCESSOR, UntypedFormGroup, ValidationErrors, Validator, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import { WsMqttQoSType, WsQoSTranslationMap, WsQoSTypes } from '@shared/models/session.model';
import { TimeUnitTypeTranslationMap, WebSocketConnection, WebSocketTimeUnit } from '@shared/models/ws-client.model';
import { FlexModule } from '@angular/flex-layout/flex';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { TranslateModule } from '@ngx-translate/core';
import { MatInput } from '@angular/material/input';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { ExtendedModule } from '@angular/flex-layout/extended';
import { MatSelect } from '@angular/material/select';
import { NgFor } from '@angular/common';
import { MatOption } from '@angular/material/core';
import { ValueInputComponent } from '@shared/components/value-input.component';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';

export interface LastWill {
  topic: string;
  qos: number;
}

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
    standalone: true,
    imports: [FormsModule, ReactiveFormsModule, FlexModule, MatFormField, MatLabel, TranslateModule, MatInput, CopyButtonComponent, MatSuffix, ExtendedModule, MatSelect, NgFor, MatOption, ValueInputComponent, MatSlideToggle, MatIcon, MatTooltip]
})
export class LastWillComponent implements OnInit, ControlValueAccessor, Validator, OnDestroy, OnChanges {

  @Input()
  disabled: boolean;

  @Input()
  mqttVersion: number;

  @Input()
  entity: WebSocketConnection;

  formGroup: UntypedFormGroup;
  qoSTypes = WsQoSTypes;
  qoSTranslationMap = WsQoSTranslationMap;
  timeUnitTypes = Object.keys(WebSocketTimeUnit);
  timeUnitTypeTranslationMap = TimeUnitTypeTranslationMap;

  private valueChangeSubscription: Subscription = null;
  private destroy$ = new Subject<void>();
  private propagateChange = (v: any) => {
  };

  constructor(public fb: FormBuilder) {
  }

  ngOnInit() {
    this.initForm(this.entity);
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (propName === 'mqttVersion' && change.currentValue) {
          this.mqttVersion = change.currentValue;
          this.disableMqtt5Features();
        }
      }
    }
  }

  private initForm(entity: WebSocketConnection) {
    const lastWillMsg = entity?.configuration?.lastWillMsg;
    this.formGroup = this.fb.group({
      topic: [lastWillMsg ? lastWillMsg.topic : null, []],
      payload: [lastWillMsg ? lastWillMsg.payload : null, []],
      qos: [lastWillMsg ? lastWillMsg.qos : WsMqttQoSType.AT_LEAST_ONCE, []],
      retain: [lastWillMsg ? lastWillMsg.retain : false, []],
      willDelayInterval: [{value: lastWillMsg ? lastWillMsg.willDelayInterval : 0, disabled: this.mqttVersion !== 5}, []],
      willDelayIntervalUnit: [{
        value: lastWillMsg ? lastWillMsg.willDelayIntervalUnit : WebSocketTimeUnit.SECONDS,
        disabled: this.mqttVersion !== 5
      }, []],
      payloadFormatIndicator: [{value: lastWillMsg ? lastWillMsg.payloadFormatIndicator : false, disabled: this.mqttVersion !== 5}, []],
      msgExpiryInterval: [{value: lastWillMsg ? lastWillMsg.msgExpiryInterval : 0, disabled: this.mqttVersion !== 5}, []],
      msgExpiryIntervalUnit: [{
        value: lastWillMsg ? lastWillMsg.msgExpiryIntervalUnit : WebSocketTimeUnit.SECONDS,
        disabled: this.mqttVersion !== 5
      }, []],
      contentType: [{value: lastWillMsg ? lastWillMsg.contentType : null, disabled: this.mqttVersion !== 5}, []],
      responseTopic: [{value: lastWillMsg ? lastWillMsg.responseTopic : null, disabled: this.mqttVersion !== 5}, []],
      correlationData: [{value: lastWillMsg ? lastWillMsg.correlationData : null, disabled: this.mqttVersion !== 5}, []]
    });
    this.disableMqtt5Features();
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

  setDisabledState(isDisabled: boolean) {
    /*    this.disabled = isDisabled;
        if (this.disabled) {
          this.formGroup.disable({emitEvent: false});
        } else {
          this.formGroup.enable({emitEvent: false});
        }*/
  }

  validate(): ValidationErrors | null {
    return this.formGroup.valid ? null : {lastWill: true};
  }

  writeValue(value: LastWill): void {
    this.formGroup.valueChanges.subscribe((value) => {
      this.updateView(value);
    });
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

  private updateView(value: LastWill) {
    this.propagateChange(value);
  }

  private disableMqtt5Features() {
    if (this.mqttVersion !== 5) {
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
}

