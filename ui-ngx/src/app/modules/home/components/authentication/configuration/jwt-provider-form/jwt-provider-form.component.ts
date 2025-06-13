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
  Validator,
  Validators
} from '@angular/forms';
import { isDefinedAndNotNull } from '@core/utils';
import { takeUntil } from 'rxjs/operators';
import { TranslateModule } from '@ngx-translate/core';
import {
  JwtAlgorithmType,
  JwtAlgorithmTypeTranslation,
  JwtMqttAuthProviderConfiguration,
  JwtVerifierType,
  MqttAuthProvider
} from '@shared/models/mqtt-auth-provider.model';
import {
  MqttAuthenticationProviderForm
} from '@home/components/authentication/configuration/mqtt-authentication-provider-form';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatOption } from '@angular/material/core';
import { MatSelect } from '@angular/material/select';
import { ClientType, clientTypeTranslationMap } from '@shared/models/client.model';
import { KeyValMapComponent } from '@shared/components/key-val-map.component';
import { ToggleOption } from '@shared/components/toggle-header.component';
import { ToggleSelectComponent } from '@shared/components/toggle-select.component';
import { JsonPipe, NgTemplateOutlet } from '@angular/common';
import { IntegrationCredentialType } from '@shared/models/integration.models';
import { MatInput } from '@angular/material/input';
import { FileInputComponent } from '@shared/components/file-input.component';
import {
  IntegrationCredentialsComponent
} from '@home/components/integration/integration-credentials/integration-credentials.component';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { TogglePasswordComponent } from '@shared/components/button/toggle-password.component';
import { HintTooltipIconComponent } from '@shared/components/hint-tooltip-icon.component';
import {
  MatChipEditedEvent,
  MatChipGrid,
  MatChipInput,
  MatChipInputEvent,
  MatChipRemove,
  MatChipRow
} from '@angular/material/chips';
import { AuthRulePatternsType } from '@shared/models/credentials.model';
import { ENTER, TAB } from '@angular/cdk/keycodes';

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
    MatIcon,
    MatSuffix,
    MatTooltip,
    TogglePasswordComponent,
    HintTooltipIconComponent,
    MatChipGrid,
    MatChipInput,
    MatChipRemove,
    MatChipRow,
    JsonPipe
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
  pubRulesSet = new Set<string>();
  subRulesSet = new Set<string>();
  separatorKeysCodes = [ENTER, TAB];
  authRulePatternsType = AuthRulePatternsType;

  private propagateChangePending = false;
  private propagateChange = (v: any) => { };

  constructor(private fb: UntypedFormBuilder) {
    super();
  }

mockData = {
  type: "JWT",
  jwtVerifierType: "ALGORITHM_BASED",
  defaultClientType: "APPLICATION",
  jwtVerifierConfiguration: {
    jwtVerifierType: "ALGORITHM_BASED",
    algorithm: "HMAC_BASED",
    jwtSignAlgorithmConfiguration: {
      algorithm: "HMAC_BASED",
      secret: "please-change-this-32-char-jwt-secret"
    }
  },
  authClaims: {},
  clientTypeClaims: {},
  authRules: {
    pubAuthRulePatterns: null,
    subAuthRulePatterns: null
  }
}

  ngOnInit() {
    this.jwtConfigForm = this.fb.group({
      defaultClientType: [null, []],
      authClaims: [null, []],
      authRules: this.fb.group({
        pubAuthRulePatterns: [null, []],
        subAuthRulePatterns: [null, []]
      }),
      clientTypeClaims: [null, []],
      jwtVerifierType: [null, []],
      jwtVerifierConfiguration: this.fb.group({
        jwtSignAlgorithmConfiguration: this.fb.group({
          secret: [null, [Validators.required]],
        }),
        algorithm: [null, []],
        publicKey: [null, [Validators.required]],
        endpoint: [null, [Validators.required]],
        refreshInterval: [null, []],
        credentials: [null, []],
        headers: [null, []],
      }),
    });
    this.jwtConfigForm.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.updateModels(this.jwtConfigForm.getRawValue()));
    this.jwtConfigForm.get('jwtVerifierType').valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe((type) => this.updateJwtValidators(type));
  }

  writeValue(value: JwtMqttAuthProviderConfiguration) {
    if (isDefinedAndNotNull(value)) {
      this.jwtConfigForm.reset(value, {emitEvent: false});
      this.updateJwtValidators(value.jwtVerifierType);
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
    const emitEvent = false;
    if (type === JwtVerifierType.JWKS) {
      this.jwtConfigForm.get('jwtVerifierConfiguration.endpoint').enable({emitEvent});
      this.jwtConfigForm.get('jwtVerifierConfiguration.endpoint').setValidators([Validators.required]);
      this.jwtConfigForm.get('jwtVerifierConfiguration.refreshInterval').enable({emitEvent});
      this.jwtConfigForm.get('jwtVerifierConfiguration.credentials').enable({emitEvent});
      this.jwtConfigForm.get('jwtVerifierConfiguration.headers').enable({emitEvent});

      this.jwtConfigForm.get('jwtVerifierConfiguration.algorithm').disable({emitEvent});
      this.jwtConfigForm.get('jwtVerifierConfiguration.jwtSignAlgorithmConfiguration.secret').disable({emitEvent});
      this.jwtConfigForm.get('jwtVerifierConfiguration.publicKey').disable({emitEvent});
    } else if (type === JwtVerifierType.ALGORITHM_BASED) {
      this.jwtConfigForm.get('jwtVerifierConfiguration.algorithm').enable({emitEvent});

      this.jwtConfigForm.get('jwtVerifierConfiguration.endpoint').disable({emitEvent});
      this.jwtConfigForm.get('jwtVerifierConfiguration.refreshInterval').disable({emitEvent});
      this.jwtConfigForm.get('jwtVerifierConfiguration.credentials').disable({emitEvent});
      this.jwtConfigForm.get('jwtVerifierConfiguration.headers').disable({emitEvent});

      if (this.jwtConfigForm.get('jwtVerifierConfiguration.algorithm').value === JwtAlgorithmType.HMAC_BASED) {
        this.jwtConfigForm.get('jwtVerifierConfiguration.jwtSignAlgorithmConfiguration.secret').enable({emitEvent});
        this.jwtConfigForm.get('jwtVerifierConfiguration.jwtSignAlgorithmConfiguration.secret').setValidators([Validators.required]);

        // this.jwtConfigForm.get('jwtVerifierConfiguration.publicKey').disable({emitEvent});
      }  else if (this.jwtConfigForm.get('jwtVerifierConfiguration.algorithm').value === JwtAlgorithmType.PUBLIC_KEY) {
        this.jwtConfigForm.get('jwtVerifierConfiguration.publicKey').enable({emitEvent});
        this.jwtConfigForm.get('jwtVerifierConfiguration.publicKey').setValidators([Validators.required]);

        this.jwtConfigForm.get('jwtVerifierConfiguration.jwtSignAlgorithmConfiguration.secret').disable({emitEvent});
      }
    }
    this.jwtConfigForm.updateValueAndValidity({emitEvent});
  }

  addTopicRule(event: MatChipInputEvent, type: AuthRulePatternsType) {
    const input = event.input;
    const value = event.value;
    if ((value || '').trim()) {
      switch (type) {
        case AuthRulePatternsType.PUBLISH:
          this.pubRulesSet.add(value);
          this.setAuthRulePatternsControl(this.pubRulesSet, type);
          break;
        case AuthRulePatternsType.SUBSCRIBE:
          this.subRulesSet.add(value);
          this.setAuthRulePatternsControl(this.subRulesSet, type);
          break;
      }
    }
    if (input) {
      input.value = '';
    }
  }

  removeTopicRule(rule: string, type: AuthRulePatternsType) {
    switch (type) {
      case AuthRulePatternsType.PUBLISH:
        this.pubRulesSet.delete(rule);
        this.setAuthRulePatternsControl(this.pubRulesSet, type);
        break;
      case AuthRulePatternsType.SUBSCRIBE:
        this.subRulesSet.delete(rule);
        this.setAuthRulePatternsControl(this.subRulesSet, type);
        break;
    }
  }

  editTopicRule(event: MatChipEditedEvent, type: AuthRulePatternsType): void {
    let index: number;
    let array: string[];
    const oldRule = event.chip.value;
    const newRule = event.value;
    switch (type) {
      case AuthRulePatternsType.SUBSCRIBE:
        array = Array.from(this.subRulesSet);
        index = array.indexOf(oldRule);
        if (index !== -1) {
          array[index] = newRule;
        }
        this.subRulesSet = new Set(array);
        this.setAuthRulePatternsControl(this.subRulesSet, type);
        break;
      case AuthRulePatternsType.PUBLISH:
        array = Array.from(this.pubRulesSet);
        index = array.indexOf(oldRule);
        if (index !== -1) {
          array[index] = newRule;
        }
        this.pubRulesSet = new Set(array);
        this.setAuthRulePatternsControl(this.pubRulesSet, type);
        break;
    }
  }

  private setAuthRulePatternsControl(set: Set<string>, type: AuthRulePatternsType) {
    const rulesArray = [Array.from(set).join(',')];
    switch (type) {
      case AuthRulePatternsType.PUBLISH:
        this.jwtConfigForm.get('authRules').get('pubAuthRulePatterns').setValue(rulesArray);
        break;
      case AuthRulePatternsType.SUBSCRIBE:
        this.jwtConfigForm.get('authRules').get('subAuthRulePatterns').setValue(rulesArray);
        break;
    }
  }
}
