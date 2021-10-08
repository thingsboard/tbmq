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

import { Component, forwardRef, Input } from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor,
  FormBuilder,
  FormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
  Validators
} from '@angular/forms';
import { isDefinedAndNotNull, isEmptyStr } from '@core/utils';
import { BasicMqttCredentials } from '@shared/models/mqtt-client-crenetials.model';

@Component({
  selector: 'tb-mqtt-credentials-mqtt-ssl',
  templateUrl: './mqtt-credentials-mqtt-ssl.component.html',
  styleUrls: ['./mqtt-credentials-mqtt-ssl.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => MqttCredentialsMqttSslComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => MqttCredentialsMqttSslComponent),
      multi: true
    }
  ],
})
export class MqttCredentialsMqttSslComponent implements ControlValueAccessor, Validator {

  @Input()
  disabled: boolean;

  credentialsMqttSslFormGroup: FormGroup;

  private propagateChange = null;
  private onTouched: () => void = () => {};

  constructor(public fb: FormBuilder) {
    this.credentialsMqttSslFormGroup = this.fb.group({
      parentCertCommonName: ['', [Validators.required]],
      authorizationRulesMapping: ['']
    });
  }

  writeValue(credentialsValue: string): void {
    if (isDefinedAndNotNull(credentialsValue) && !isEmptyStr(credentialsValue)) {
      const value = JSON.parse(credentialsValue);
      this.credentialsMqttSslFormGroup.patchValue(value, { emitEvent: false });
    }
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
    this.credentialsMqttSslFormGroup.valueChanges.subscribe(fn);
  }

  registerOnTouched(fn: any): void {
    this.onTouched = fn;
  }

  updateView(value: BasicMqttCredentials) {
    this.credentialsMqttSslFormGroup.patchValue(value, { emitEvent: false });
    const formValue = JSON.stringify(value);
    this.propagateChange(formValue);
  }

  validate(control: AbstractControl): ValidationErrors | null {
    return this.credentialsMqttSslFormGroup.valid ? null : {
      credentialsMqttSsl: {valid: false}
    };
  }

  setDisabledState(isDisabled: boolean) {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.credentialsMqttSslFormGroup.disable({ emitEvent: false });
    } else {
      this.credentialsMqttSslFormGroup.enable({ emitEvent: false });
    }
  }

}
