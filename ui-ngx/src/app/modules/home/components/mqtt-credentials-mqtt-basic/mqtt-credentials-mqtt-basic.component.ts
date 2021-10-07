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

import { Component, forwardRef, Input, OnDestroy } from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor,
  FormBuilder,
  FormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
  ValidatorFn,
  Validators
} from '@angular/forms';
import { DeviceCredentialMQTTBasic } from '@shared/models/device.models';
import { isDefinedAndNotNull, isEmptyStr } from '@core/utils';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

@Component({
  selector: 'tb-mqtt-credentials-mqtt-basic',
  templateUrl: './mqtt-credentials-mqtt-basic.component.html',
  styleUrls: ['./mqtt-credentials-mqtt-basic.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => MqttCredentialsMqttBasicComponent),
      multi: true
    }
  ],
})
export class MqttCredentialsMqttBasicComponent implements ControlValueAccessor, OnDestroy {

  @Input()
  disabled: boolean;

  credentialsMqttBasicFormGroup: FormGroup;

  private destroy$ = new Subject();
  private propagateChange = (v: any) => {};

  constructor(public fb: FormBuilder) {
    this.credentialsMqttBasicFormGroup = this.fb.group({
        clientId: [null],
        userName: [null],
        password: [null]
      },
      {
        validators: this.atLeastOne(Validators.required, ['clientId', 'userName'])
      }
    );
    this.credentialsMqttBasicFormGroup.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe((value) => {
      this.updateView(value);
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  writeValue(mqttBasic: any): void {
    if (isDefinedAndNotNull(mqttBasic) && !isEmptyStr(mqttBasic)) {
      const value = JSON.parse(mqttBasic);
      this.credentialsMqttBasicFormGroup.patchValue(value, { emitEvent: false });
    }
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean) {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.credentialsMqttBasicFormGroup.disable({ emitEvent: false });
    } else {
      this.credentialsMqttBasicFormGroup.enable({ emitEvent: false });
    }
  }

  updateView(value: DeviceCredentialMQTTBasic) {
    this.credentialsMqttBasicFormGroup.patchValue(value, { emitEvent: false });
    const formValue = JSON.stringify(value);
    this.propagateChange(formValue);
  }

  passwordChanged() {
    const value = this.credentialsMqttBasicFormGroup.get('password').value;
    if (value !== '') {
      this.credentialsMqttBasicFormGroup.get('userName').setValidators([Validators.required]);
    } else {
      this.credentialsMqttBasicFormGroup.get('userName').setValidators([]);
    }
    this.credentialsMqttBasicFormGroup.get('userName').updateValueAndValidity({emitEvent: false});
  }

  private atLeastOne(validator: ValidatorFn, controls: string[] = null) {
    return (group: FormGroup): ValidationErrors | null => {
      if (!controls) {
        controls = Object.keys(group.controls);
      }
      const hasAtLeastOne = group?.controls && controls.some(k => !validator(group.controls[k]));

      return hasAtLeastOne ? null : {atLeastOne: true};
    };
  }

}
