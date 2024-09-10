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

import { AfterViewInit, Component, forwardRef, Input, OnDestroy, ViewChild } from '@angular/core';
import {
  ControlValueAccessor,
  FormBuilder,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR, UntypedFormGroup,
  ValidationErrors,
  Validator, Validators
} from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { isDefinedAndNotNull, isEmptyStr } from '@core/utils';
import { ClientCredentials, SslMqttCredentials } from '@shared/models/credentials.model';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';

@Component({
  selector: 'tb-mqtt-credentials-ssl',
  templateUrl: './ssl.component.html',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => MqttCredentialsSslComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => MqttCredentialsSslComponent),
      multi: true,
    }],
  styleUrls: []
})
export class MqttCredentialsSslComponent implements AfterViewInit, ControlValueAccessor, Validator, OnDestroy {

  @ViewChild('copyBtn')
  copyBtn: CopyButtonComponent;

  @Input()
  disabled: boolean;

  @Input()
  entity: ClientCredentials;

  credentialsMqttFormGroup: UntypedFormGroup;
  certificateCnHint = 'mqtt-client-credentials.hint-ssl-cert-common-name';
  certificateCnLabel = 'mqtt-client-credentials.certificate-common-name';

  private destroy$ = new Subject<void>();
  private propagateChange = (v: any) => {};

  constructor(public fb: FormBuilder) {
    this.credentialsMqttFormGroup = this.fb.group({
      certCnPattern: [null, [Validators.required]],
      certCnIsRegex: [false],
      authRulesMapping: [null]
    });
  }

  ngAfterViewInit(): void {
    this.credentialsMqttFormGroup.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe((value) => {
      this.updateView(value);
    });
    this.credentialsMqttFormGroup.get('certCnIsRegex').valueChanges.subscribe(value => this.updateCertificateCnView(value));
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {}

  setDisabledState(isDisabled: boolean) {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.credentialsMqttFormGroup.disable({emitEvent: false});
    } else {
      this.credentialsMqttFormGroup.enable({emitEvent: false});
    }
  }

  validate(): ValidationErrors | null {
    return this.credentialsMqttFormGroup.valid ? null : {
      mqttCredentials: {
        valid: false
      }
    };
  }

  writeValue(mqttSsl: string) {
    if (isDefinedAndNotNull(mqttSsl) && !isEmptyStr(mqttSsl)) {
      const value = JSON.parse(mqttSsl);
      this.credentialsMqttFormGroup.patchValue(value, {emitEvent: false});
    }
  }

  updateView(value: SslMqttCredentials) {
    const formValue = JSON.stringify(value);
    this.updateCertificateCnView(value?.certCnIsRegex);
    this.propagateChange(formValue);
  }

  onClickTbCopyButton(value: string) {
    this.copyBtn.copy(value);
  }

  private updateCertificateCnView(value: boolean = false) {
    if (value) {
      this.certificateCnLabel = 'mqtt-client-credentials.certificate-common-name-regex-label';
      this.certificateCnHint = 'mqtt-client-credentials.hint-ssl-cert-common-name-regex';
    } else {
      this.certificateCnLabel = 'mqtt-client-credentials.certificate-common-name';
      this.certificateCnHint = 'mqtt-client-credentials.hint-ssl-cert-common-name';
    }
  }
}
