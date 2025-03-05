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

import { AfterViewInit, Component, forwardRef, OnDestroy, input, model, viewChild } from '@angular/core';
import { ControlValueAccessor, FormBuilder, NG_VALIDATORS, NG_VALUE_ACCESSOR, UntypedFormGroup, ValidationErrors, Validator, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { isDefinedAndNotNull, isEmptyStr } from '@core/utils';
import { ClientCredentials, SslMqttCredentials } from '@shared/models/credentials.model';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { TranslateModule } from '@ngx-translate/core';
import { MatFormField, MatLabel, MatSuffix, MatError } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';

import { MatSlideToggle } from '@angular/material/slide-toggle';
import { AuthRulesComponent } from './auth-rules.component';

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
        }
    ],
    imports: [FormsModule, ReactiveFormsModule, TranslateModule, MatFormField, MatLabel, MatInput, CopyButtonComponent, MatSuffix, MatError, MatSlideToggle, AuthRulesComponent]
})
export class MqttCredentialsSslComponent implements AfterViewInit, ControlValueAccessor, Validator, OnDestroy {

  readonly copyBtn = viewChild<CopyButtonComponent>('copyBtn');

  disabled = model<boolean>();
  readonly entity = input<ClientCredentials>();

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
    this.disabled.set(isDisabled);
    if (this.disabled()) {
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
      this.updateCertificateCnView(value?.certCnIsRegex);
      this.credentialsMqttFormGroup.patchValue(value, {emitEvent: false});
    }
  }

  updateView(value: SslMqttCredentials) {
    const formValue = JSON.stringify(value);
    this.updateCertificateCnView(value?.certCnIsRegex);
    this.propagateChange(formValue);
  }

  onClickTbCopyButton(value: string) {
    this.copyBtn().copy(value);
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
