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
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { baseUrl, isDefinedAndNotNull, notOnlyWhitespaceValidator } from '@core/utils';
import { takeUntil } from 'rxjs/operators';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import {
  MqttAuthenticationProviderForm
} from '@home/components/authentication/configuration/mqtt-authentication-provider-form';
import {
  MqttAuthProvider,
  BasicMqttAuthProviderConfiguration,
  HttpMqttAuthProviderConfiguration
} from '@shared/models/mqtt-auth-provider.model';
import { MatFormField, MatInput, MatLabel, MatSuffix } from '@angular/material/input';
import { MatOption } from '@angular/material/core';
import { MatSelect } from '@angular/material/select';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { HttpRequestType } from '@shared/models/integration.models';
import {
  IntegrationCredentialsComponent
} from '@home/components/integration/integration-credentials/integration-credentials.component';
import { KeyValMapComponent } from '@shared/components/key-val-map.component';
import { IntegrationCredentialType } from '@shared/models/integration.models';
import {
  MatExpansionPanel,
  MatExpansionPanelContent,
  MatExpansionPanelDescription,
  MatExpansionPanelHeader
} from '@angular/material/expansion';
import { ClientType } from '@shared/models/client.model';
import { clientTypeTranslationMap } from '@shared/models/client.model';
import { ENTER, TAB } from '@angular/cdk/keycodes';
import {
  MatChipEditedEvent,
  MatChipGrid,
  MatChipInput,
  MatChipInputEvent,
  MatChipRemove,
  MatChipRow
} from '@angular/material/chips';
import { AuthRulePatternsType } from '@shared/models/credentials.model';
import { JsonObjectEditComponent } from '@shared/components/json-object-edit.component';

@Component({
  selector: 'tb-http-provider-config-form',
  templateUrl: './http-provider-form.component.html',
  styleUrls: ['./http-provider-form.component.scss'],
  imports: [
    ReactiveFormsModule,
    TranslateModule,
    MatFormField,
    MatLabel,
    MatOption,
    MatSelect,
    CopyButtonComponent,
    MatIcon,
    MatInput,
    MatSuffix,
    MatTooltip,
    IntegrationCredentialsComponent,
    KeyValMapComponent,
    MatExpansionPanel,
    MatExpansionPanelContent,
    MatExpansionPanelDescription,
    MatExpansionPanelHeader,
    MatChipInput,
    MatChipRow,
    MatChipGrid,
    MatChipRemove,
    JsonObjectEditComponent,
  ],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => HttpProviderFormComponent),
    multi: true
  },
  {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => HttpProviderFormComponent),
    multi: true,
  }]
})
export class HttpProviderFormComponent extends MqttAuthenticationProviderForm implements ControlValueAccessor, Validator, OnInit {

  readonly provider = input<MqttAuthProvider>();
  readonly isEdit = input<boolean>();

  readonly httpRequestTypes = Object.keys(HttpRequestType).filter(key => [HttpRequestType.POST, HttpRequestType.GET].includes(key as HttpRequestType));
  readonly IntegrationCredentialType = IntegrationCredentialType;
  readonly MemoryBufferSizeInKbLimit = 25000;
  readonly ClientType = ClientType;
  readonly clientTypeTranslationMap = clientTypeTranslationMap;
  readonly clientTypes = Object.values(ClientType);
  readonly authRulePatternsType = AuthRulePatternsType;
  readonly separatorKeysCodes = [ENTER, TAB];

  pubRulesSet = new Set<string>();
  subRulesSet = new Set<string>();

  readonly httpProviderConfigForm = this.fb.group({
    restEndpointUrl: [baseUrl(), [Validators.required, notOnlyWhitespaceValidator]],
    requestMethod: [HttpRequestType.POST],
    headers: [{'Content-Type': 'application/json'}, Validators.required],
    requestBody: [null, []],
    credentials: [{ type: IntegrationCredentialType.Anonymous }],
    defaultClientType: [null, [Validators.required]],
    authRules: this.fb.group({
      pubAuthRulePatterns: [null, []],
      subAuthRulePatterns: [null, []]
    }),
    readTimeoutMs: [0, []],
    maxParallelRequestsCount: [0, []],
    maxInMemoryBufferSizeInKb: [256, [Validators.required, Validators.min(1), Validators.max(this.MemoryBufferSizeInKbLimit)]],
  });

  private propagateChangePending = false;
  private propagateChange = (v: any) => { };

  constructor(protected fb: UntypedFormBuilder,
              protected store: Store<AppState>,
              protected translate: TranslateService) {
    super();
  }

  ngOnInit() {
    this.httpProviderConfigForm.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.updateModels(this.httpProviderConfigForm.getRawValue()));
  }

  writeValue(value: HttpMqttAuthProviderConfiguration) {
    if (isDefinedAndNotNull(value)) {
      this.clearRuleSets();
      if (value.authRules) {
        for (const rule of Object.keys(value.authRules)) {
          if (value.authRules[rule]?.length) {
            value.authRules[rule].map(el => this.addValueToAuthRulesSet(rule, el));
          }
        }
      }
      if (value.requestBody && typeof value.requestBody === 'string') {
        try {
          value.requestBody = JSON.parse(value.requestBody);
        } catch (e) {
          console.error('Failed to parse requestBody as JSON', e);
        }
      }
      this.httpProviderConfigForm.reset(value, {emitEvent: false});
    } else {
      this.propagateChangePending = true;
    }
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
    if (this.propagateChangePending) {
      this.propagateChangePending = false;
      setTimeout(() => {
        this.updateModels(this.httpProviderConfigForm.getRawValue());
      }, 0);
    }
  }

  registerOnTouched(fn: any) { }

  setDisabledState(isDisabled: boolean) {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.httpProviderConfigForm.disable({emitEvent: false});
    } else {
      this.httpProviderConfigForm.enable({emitEvent: false});
    }
  }

  validate(): ValidationErrors | null {
    return this.httpProviderConfigForm.valid ? null : {
      basicProviderConfigForm: {valid: false}
    };
  }

  private updateModels(value: HttpMqttAuthProviderConfiguration) {
    if (value.requestBody && typeof value.requestBody === 'object') {
      value.requestBody = JSON.stringify(value.requestBody);
    }
    this.propagateChange(value);
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
        this.httpProviderConfigForm.get('authRules').get('pubAuthRulePatterns').setValue(rulesArray);
        break;
      case AuthRulePatternsType.SUBSCRIBE:
        this.httpProviderConfigForm.get('authRules').get('subAuthRulePatterns').setValue(rulesArray);
        break;
    }
  }

  private clearRuleSets() {
    this.pubRulesSet.clear();
    this.subRulesSet.clear();
  }

  private addValueToAuthRulesSet(type: string, value: string) {
    switch (type) {
      case 'subAuthRulePatterns':
        this.subRulesSet.add(value);
        break;
      case 'pubAuthRulePatterns':
        this.pubRulesSet.add(value);
        break;
    }
  }
}
