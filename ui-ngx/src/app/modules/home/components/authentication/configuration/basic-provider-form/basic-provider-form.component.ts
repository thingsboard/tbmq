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

import { Component, forwardRef, input, OnInit } from '@angular/core';
import {
  ControlValueAccessor,
  UntypedFormBuilder,
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
import {
  MqttAuthenticationProviderForm
} from '@home/components/authentication/configuration/mqtt-authentication-provider-form';
import {
  MqttBasicAuthenticationStrategy,
  MqttBasicAuthenticationStrategyHintTranslationMap,
  MqttBasicAuthenticationStrategyTranslationMap,
  MqttAuthProvider,
  BasicMqttAuthProviderConfiguration
} from '@shared/models/mqtt-auth-provider.model';
import { MatFormField, MatHint, MatLabel } from '@angular/material/input';
import { MatOption } from '@angular/material/core';
import { MatSelect } from '@angular/material/select';

@Component({
  selector: 'tb-basic-provider-config-form',
  templateUrl: './basic-provider-form.component.html',
  styleUrls: ['./basic-provider-form.component.scss'],
  imports: [
    ReactiveFormsModule,
    TranslateModule,
    MatFormField,
    MatHint,
    MatLabel,
    MatOption,
    MatSelect
  ],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => BasicProviderFormComponent),
    multi: true
  },
  {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => BasicProviderFormComponent),
    multi: true,
  }]
})
export class BasicProviderFormComponent extends MqttAuthenticationProviderForm implements ControlValueAccessor, Validator, OnInit {

  readonly provider = input<MqttAuthProvider>();
  readonly isEdit = input<boolean>();

  authStrategies = Object.values(MqttBasicAuthenticationStrategy);
  authStrategyTranslations = MqttBasicAuthenticationStrategyTranslationMap;
  authStrategyHintTranslations = MqttBasicAuthenticationStrategyHintTranslationMap;

  readonly basicProviderConfigForm = this.fb.group({
    authStrategy: [null, []]
  });

  private propagateChangePending = false;
  private propagateChange = (v: any) => { };

  constructor(protected fb: UntypedFormBuilder,
              protected store: Store<AppState>,
              protected translate: TranslateService) {
    super();
  }

  ngOnInit() {
    this.basicProviderConfigForm.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.updateModels(this.basicProviderConfigForm.getRawValue()));
  }

  writeValue(value: MqttAuthProvider) {
    if (isDefinedAndNotNull(value)) {
      this.basicProviderConfigForm.reset(value, {emitEvent: false});
    } else {
      this.propagateChangePending = true;
    }
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
    if (this.propagateChangePending) {
      this.propagateChangePending = false;
      setTimeout(() => {
        this.updateModels(this.basicProviderConfigForm.getRawValue());
      }, 0);
    }
  }

  registerOnTouched(fn: any) { }

  setDisabledState(isDisabled: boolean) {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.basicProviderConfigForm.disable({emitEvent: false});
    } else {
      this.basicProviderConfigForm.enable({emitEvent: false});
    }
  }

  validate(): ValidationErrors | null {
    return this.basicProviderConfigForm.valid ? null : {
      basicProviderConfigForm: {valid: false}
    };
  }

  authStrategyHint() {
    const authStrategy = this.basicProviderConfigForm.get('authStrategy')?.value;
    return this.authStrategyHintTranslations.get(authStrategy);
  }

  private updateModels(value: BasicMqttAuthProviderConfiguration) {
    this.propagateChange(value);
  }
}
