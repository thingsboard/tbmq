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
  FormsModule,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ReactiveFormsModule,
  UntypedFormBuilder,
  UntypedFormGroup,
  ValidationErrors,
  Validators,
  Validator
} from '@angular/forms';
import { isDefinedAndNotNull } from '@core/utils';
import { takeUntil } from 'rxjs/operators';
import { TranslateModule } from '@ngx-translate/core';
import {
  JwtAlgorithmType,
  JwtAlgorithmTypeTranslation,
  JwtMqttAuthProviderConfiguration,
  JwtVerifierType,
  MqttAuthProvider,
  MqttAuthProviderType
} from '@shared/models/mqtt-auth-provider.model';
import {
  MqttAuthenticationProviderForm
} from '@home/components/authentication/configuration/mqtt-authentication-provider-form';
import { MatSlideToggle } from "@angular/material/slide-toggle";
import { MatFormField, MatLabel, MatSuffix } from "@angular/material/form-field";
import { MatOption } from "@angular/material/core";
import { MatSelect } from "@angular/material/select";
import { ClientType, clientTypeTranslationMap } from "@shared/models/client.model";
import { KeyValMapComponent } from "@shared/components/key-val-map.component";
import { ToggleOption } from "@shared/components/toggle-header.component";
import { ToggleSelectComponent } from "@shared/components/toggle-select.component";
import { NgTemplateOutlet } from "@angular/common";
import { IntegrationCredentialType } from "@shared/models/integration.models";
import { MatInput } from "@angular/material/input";
import { FileInputComponent } from "@shared/components/file-input.component";
import {
  IntegrationCredentialsComponent
} from "@home/components/integration/integration-credentials/integration-credentials.component";
import { MatIcon } from "@angular/material/icon";
import { MatTooltip } from "@angular/material/tooltip";
import { TogglePasswordComponent } from "@shared/components/button/toggle-password.component";
import { HintTooltipIconComponent } from "@shared/components/hint-tooltip-icon.component";

@Component({
  selector: 'tb-jwt-provider-config-form',
  templateUrl: './jwt-provider-form.component.html',
  styleUrls: ['./jwt-provider-form.component.scss'],
  imports: [
    ReactiveFormsModule,
    TranslateModule,
    MatFormField,
    MatLabel,
    MatOption,
    MatSelect,
    KeyValMapComponent,
    ToggleOption,
    ToggleSelectComponent,
    NgTemplateOutlet,
    FormsModule,
    MatInput,
    FileInputComponent,
    IntegrationCredentialsComponent,
    MatSlideToggle,
    MatIcon,
    MatSuffix,
    MatTooltip,
    TogglePasswordComponent,
    HintTooltipIconComponent,
  ],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => JwtProviderFormComponent),
    multi: true
  },
  {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => JwtProviderFormComponent),
    multi: true,
  }]
})
export class JwtProviderFormComponent extends MqttAuthenticationProviderForm implements ControlValueAccessor, Validator, OnInit {

  provider = input<MqttAuthProvider>();
  isEdit = input<boolean>();

  jwtConfigForm: UntypedFormGroup;

  clientTypes = Object.values(ClientType);
  clientTypeTranslationMap = clientTypeTranslationMap;
  ClientType = ClientType;

  JwtVerifierType = JwtVerifierType;
  JwtAlgorithmType = JwtAlgorithmType;
  jwtAlgorithmTypes = Object.values(JwtAlgorithmType);
  jwtAlgorithmTypeTranslation = JwtAlgorithmTypeTranslation;

  IntegrationCredentialType = IntegrationCredentialType;

  private propagateChangePending = false;
  private propagateChange = (v: any) => { };

  constructor(private fb: UntypedFormBuilder) {
    super();
  }

  ngOnInit() {
    this.jwtConfigForm = this.fb.group({
      configuration: this.fb.group({
        clientType: [null, []],
        claims: [null, []],
        clientTypeClaims: [null, []],
        jwtVerifierType: [null, []],
        algorithmType: [null, []],
        secret: [null, []],
        publicKey: [null, []],
        endpoint: [null, []],
        refreshInterval: [null, []],
        ssl: [null, []],
        credentials: [null, []],
        headers: [null, []],
      })
    });
    this.jwtConfigForm.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.updateModels(this.jwtConfigForm.getRawValue()));
    this.jwtConfigForm.get('configuration.jwtVerifierType').valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe((type) => this.updateJwtValidators(type));
  }

  writeValue(value: MqttAuthProvider) {
    if (isDefinedAndNotNull(value)) {
      this.jwtConfigForm.reset(value, {emitEvent: false});
      if (value.type === MqttAuthProviderType.JWT) {
        const jwtConfig = value.configuration as JwtMqttAuthProviderConfiguration;
        this.updateJwtValidators(jwtConfig.jwtVerifierType);
      }
    } else {
      this.propagateChangePending = true;
    }
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
    if (this.propagateChangePending) {
      this.propagateChangePending = false;
      setTimeout(() => {
        this.updateModels(this.jwtConfigForm.getRawValue());
      }, 0);
    }
  }

  registerOnTouched(fn: any) { }

  setDisabledState(isDisabled: boolean) {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.jwtConfigForm.disable({emitEvent: false});
    } else {
      this.jwtConfigForm.enable({emitEvent: false});
    }
  }

  private updateModels(value: MqttAuthProvider) {
    this.propagateChange(value);
  }

  validate(): ValidationErrors | null {
    return this.jwtConfigForm.valid ? null : {
      jwtProviderConfigForm: {valid: false}
    };
  }

  updateJwtValidators(type: JwtVerifierType) {
    if (type === JwtVerifierType.JWKS) {
      this.jwtConfigForm.get('configuration.algorithmType').disable({emitEvent: false});
      this.jwtConfigForm.get('configuration.secret').disable({emitEvent: false});
      this.jwtConfigForm.get('configuration.publicKey').disable({emitEvent: false});

      this.jwtConfigForm.get('configuration.endpoint').enable({emitEvent: false});
      this.jwtConfigForm.get('configuration.endpoint').setValidators([Validators.required]);

      this.jwtConfigForm.get('configuration.refreshInterval').enable({emitEvent: false});
      this.jwtConfigForm.get('configuration.ssl').enable({emitEvent: false});
      this.jwtConfigForm.get('configuration.credentials').enable({emitEvent: false});
      this.jwtConfigForm.get('configuration.headers').enable({emitEvent: false});
    } else {
      this.jwtConfigForm.get('configuration.algorithmType').enable({emitEvent: false});
      this.jwtConfigForm.get('configuration.publicKey').enable({emitEvent: false});

      this.jwtConfigForm.get('configuration.secret').enable({emitEvent: false});
      this.jwtConfigForm.get('configuration.secret').setValidators([Validators.required]);

      this.jwtConfigForm.get('configuration.endpoint').disable({emitEvent: false});
      this.jwtConfigForm.get('configuration.refreshInterval').disable({emitEvent: false});
      this.jwtConfigForm.get('configuration.ssl').disable({emitEvent: false});
      this.jwtConfigForm.get('configuration.credentials').disable({emitEvent: false});
      this.jwtConfigForm.get('configuration.headers').disable({emitEvent: false});
    }
    this.jwtConfigForm.updateValueAndValidity();
  }

  displayEnableSsl() {
    return this.jwtConfigForm.get('configuration.credentials').value?.type !== 'cert.PEM';
  }
}
