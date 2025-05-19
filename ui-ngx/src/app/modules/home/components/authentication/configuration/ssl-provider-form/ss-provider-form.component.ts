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

import { Component, forwardRef, input, OnInit } from '@angular/core';
import {
  ControlValueAccessor,
  UntypedFormBuilder,
  UntypedFormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
  ReactiveFormsModule
} from '@angular/forms';
import { isDefinedAndNotNull } from '@core/utils';
import { takeUntil } from 'rxjs/operators';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MatSlideToggle } from "@angular/material/slide-toggle";
import {
  MqttAuthenticationProviderForm
} from '@home/components/authentication/configuration/mqtt-authentication-provider-form';
import { MqttAuthProvider } from '@shared/models/mqtt-auth-provider.model';

@Component({
  selector: 'tb-ssl-provider-config-form',
  templateUrl: './ssl-provider-form.component.html',
  styleUrls: ['./ssl-provider-form.component.scss'],
  imports: [
    ReactiveFormsModule,
    TranslateModule,
    MatSlideToggle,
  ],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => SslProviderFormComponent),
    multi: true
  },
  {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => SslProviderFormComponent),
    multi: true,
  }]
})
export class SslProviderFormComponent extends MqttAuthenticationProviderForm implements ControlValueAccessor, Validator, OnInit {

  provider = input<MqttAuthProvider>();
  isEdit = input<boolean>();
  sslProviderConfigForm: UntypedFormGroup;

  private propagateChangePending = false;
  private propagateChange = (v: any) => { };

  constructor(protected fb: UntypedFormBuilder,
              protected store: Store<AppState>,
              protected translate: TranslateService) {
    super();
  }

  ngOnInit() {
    this.sslProviderConfigForm = this.fb.group({
      configuration: this.fb.group({
        skipValidityCheckForClientCert: [false, []],
      })
    });
    this.sslProviderConfigForm.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.updateModels(this.sslProviderConfigForm.getRawValue()));
  }

  writeValue(value: MqttAuthProvider) {
    if (isDefinedAndNotNull(value)) {
      this.sslProviderConfigForm.reset(value, {emitEvent: false});
    } else {
      this.propagateChangePending = true;
    }
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
    if (this.propagateChangePending) {
      this.propagateChangePending = false;
      setTimeout(() => {
        this.updateModels(this.sslProviderConfigForm.getRawValue());
      }, 0);
    }
  }

  registerOnTouched(fn: any) { }

  setDisabledState(isDisabled: boolean) {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.sslProviderConfigForm.disable({emitEvent: false});
    } else {
      this.sslProviderConfigForm.enable({emitEvent: false});
    }
  }

  private updateModels(value) {
    this.propagateChange(value);
  }

  validate(): ValidationErrors | null {
    return this.sslProviderConfigForm.valid ? null : {
      sslProviderConfigForm: {valid: false}
    };
  }
}
