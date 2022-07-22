///
/// Copyright © 2016-2020 The Thingsboard Authors
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

import { Component, forwardRef, Input, OnDestroy, OnInit } from '@angular/core';
import {
  ControlValueAccessor,
  FormBuilder, FormControl,
  FormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  Validator, Validators
} from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { isDefinedAndNotNull } from '@core/utils';
import {
  credentialsTypeNames,
  MqttClientCredentials,
  MqttCredentialsType,
  MqttCredentialsTypes
} from '@shared/models/mqtt-client-crenetials.model';

@Component({
  selector: 'tb-mqtt-credentials',
  templateUrl: './mqtt-credentials.component.html',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => MqttCredentialsComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => MqttCredentialsComponent),
      multi: true,
    }],
  styleUrls: []
})
export class MqttCredentialsComponent implements ControlValueAccessor, OnInit, Validator, OnDestroy {

  @Input()
  disabled: boolean;

  @Input()
  mqttCredentials: MqttClientCredentials;

  @Input()
  isEdit: boolean;

  get credentialsType(): MqttCredentialsType {
    return this.mqttCredentials.credentialsType;
  }

  mqttCredentialsType = MqttCredentialsType;
  mqttCredentialsTypes = MqttCredentialsTypes;

  private destroy$ = new Subject();

  credentialsFormGroup: FormGroup;

  credentialTypeNames = credentialsTypeNames;

  private propagateChange = (v: any) => {};

  constructor(public fb: FormBuilder) {
    this.credentialsFormGroup = this.fb.group({
      name: [null],
      credentialsType: [null],
      credentialsValue: [null]
    });
    this.credentialsFormGroup.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.updateView();
    });
    this.credentialsFormGroup.get('credentialsType').valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.credentialsTypeChanged();
    });
  }

  ngOnInit(): void {
    if (this.disabled) {
      this.credentialsFormGroup.disable({emitEvent: false});
    }
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  writeValue(value: MqttClientCredentials | null): void {
    if (isDefinedAndNotNull(value)) {
      this.credentialsFormGroup.patchValue({
        name: value.name,
        credentialsType: value.credentialsType,
        credentialsValue: value.credentialsValue
      }, {emitEvent: false});
      this.updateValidators();
    }
  }

  updateView() {
    this.propagateChange(this.credentialsFormGroup.value);
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {}

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.credentialsFormGroup.disable({emitEvent: false});
    } else {
      this.credentialsFormGroup.enable({emitEvent: false});
    }
  }

  public validate(c: FormControl) {
    return this.credentialsFormGroup.valid ? null : {
      mqttCredentials: {
        valid: false
      }
    };
  }

  credentialsTypeChanged(): void {
    this.credentialsFormGroup.patchValue({
      credentialsValue: null
    });
    this.updateValidators();
  }

  updateValidators(): void {
    const credentialsType = this.credentialsFormGroup.get('credentialsType').value as MqttCredentialsType;
    switch (credentialsType) {
      case MqttCredentialsType.SSL:
        this.credentialsFormGroup.get('credentialsValue').setValidators([]);
        this.credentialsFormGroup.get('credentialsValue').updateValueAndValidity({emitEvent: false});
        break;
      case MqttCredentialsType.MQTT_BASIC:
        this.credentialsFormGroup.get('credentialsValue').setValidators([Validators.required]);
        this.credentialsFormGroup.get('credentialsValue').updateValueAndValidity({emitEvent: false});
        break;
    }
  }

}
