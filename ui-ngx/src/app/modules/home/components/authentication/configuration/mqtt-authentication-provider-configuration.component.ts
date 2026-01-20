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

import { Component, forwardRef, input, model, OnDestroy, ViewEncapsulation } from '@angular/core';
import {
  ControlValueAccessor,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ReactiveFormsModule,
  UntypedFormBuilder,
  UntypedFormGroup,
  ValidationErrors,
  Validator,
  Validators
} from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MqttAuthProvider, MqttAuthProviderType } from '@shared/models/mqtt-auth-provider.model';
import {
  SslProviderFormComponent
} from '@home/components/authentication/configuration/ssl-provider-form/ss-provider-form.component';
import {
  JwtProviderFormComponent
} from '@home/components/authentication/configuration/jwt-provider-form/jwt-provider-form.component';
import {
  BasicProviderFormComponent
} from '@home/components/authentication/configuration/basic-provider-form/basic-provider-form.component';
import {
  HttpProviderFormComponent
} from '@home/components/authentication/configuration/http-provider-form/http-provider-form.component';
import { MatButton } from '@angular/material/button';
import { TranslateModule } from '@ngx-translate/core';
import { MqttAuthProviderService } from '@core/http/mqtt-auth-provider.service';
import { NgTemplateOutlet } from '@angular/common';

@Component({
  selector: 'tb-mqtt-authentication-provider-configuration',
  templateUrl: './mqtt-authentication-provider-configuration.component.html',
  styleUrls: ['./mqtt-authentication-provider-configuration.component.scss'],
  encapsulation: ViewEncapsulation.None,
  imports: [
    ReactiveFormsModule,
    SslProviderFormComponent,
    JwtProviderFormComponent,
    BasicProviderFormComponent,
    HttpProviderFormComponent,
    MatButton,
    TranslateModule,
    NgTemplateOutlet,
  ],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => MqttAuthenticationProviderConfigurationComponent),
    multi: true
  },
  {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => MqttAuthenticationProviderConfigurationComponent),
    multi: true,
  }]
})
export class MqttAuthenticationProviderConfigurationComponent implements ControlValueAccessor, Validator, OnDestroy {

  providerForm: UntypedFormGroup;
  MqttAuthProviderType = MqttAuthProviderType;

  readonly providerType = input<MqttAuthProviderType>();
  readonly provider = input<MqttAuthProvider>();
  readonly isEdit = input<boolean>();
  disabled = model<boolean>();

  private destroy$ = new Subject<void>();
  private propagateChange = (v: any) => { };

  constructor(
    private fb: UntypedFormBuilder,
    private mqttAuthProviderService: MqttAuthProviderService,
  ) {
    this.providerForm = this.fb.group({
      configuration: [null, Validators.required]
    });
    this.providerForm.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(value => this.updateModel(value.configuration));
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  registerOnChange(fn: any) {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any) { }

  setDisabledState(isDisabled: boolean) {
    this.disabled.set(isDisabled);
    if (isDisabled) {
      this.providerForm.disable({emitEvent: false});
    } else {
      this.providerForm.enable({emitEvent: false});
    }
  }

  writeValue(value: any) {
    this.providerForm.get('configuration').reset(value, {emitEvent: false});
  }

  private updateModel(value: any) {
    this.propagateChange(value);
  }

  validate(): ValidationErrors | null {
    return this.providerForm.valid ? null : {
      providerConfiguration: {valid: false}
    };
  }

  onConnectionCheck() {
    this.provider().configuration = {
      ...this.provider().configuration,
      ...this.providerForm.getRawValue().configuration
    };
    this.mqttAuthProviderService.checkAuthProviderConnection(this.provider()).subscribe();
  }
}
