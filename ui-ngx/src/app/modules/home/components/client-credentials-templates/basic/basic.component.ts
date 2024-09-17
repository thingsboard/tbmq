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

import { AfterViewInit, ChangeDetectorRef, Component, forwardRef, Input, OnDestroy, ViewChild } from '@angular/core';
import {
  ControlValueAccessor,
  FormBuilder,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  UntypedFormGroup,
  ValidationErrors,
  Validator,
  ValidatorFn,
  Validators
} from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { isDefinedAndNotNull, isEmptyStr } from '@core/utils';
import { ANY_CHARACTERS, AuthRulePatternsType, BasicCredentials, ClientCredentials } from '@shared/models/credentials.model';
import { MatChipInputEvent } from '@angular/material/chips';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { clientIdRandom, clientUserNameRandom } from '@shared/models/ws-client.model';

@Component({
  selector: 'tb-mqtt-credentials-basic',
  templateUrl: './basic.component.html',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => MqttCredentialsBasicComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => MqttCredentialsBasicComponent),
      multi: true,
    }],
  styleUrls: ['./basic.component.scss']
})
export class MqttCredentialsBasicComponent implements ControlValueAccessor, Validator, OnDestroy, AfterViewInit {

  @ViewChild('copyClientIdBtn')
  copyClientIdBtn: CopyButtonComponent;

  @ViewChild('copyUsernameBtn')
  copyUsernameBtn: CopyButtonComponent;

  @Input()
  disabled: boolean;

  @Input()
  entity: ClientCredentials;

  authRulePatternsType = AuthRulePatternsType;
  credentialsMqttFormGroup: UntypedFormGroup;
  pubRulesSet: Set<string> = new Set();
  subRulesSet: Set<string> = new Set();

  private destroy$ = new Subject<void>();
  private propagateChange = (v: any) => {};

  constructor(public fb: FormBuilder,
              private cd: ChangeDetectorRef) {
    this.credentialsMqttFormGroup = this.fb.group({
      clientId: [null],
      userName: [null],
      password: [null],
      authRules: this.fb.group({
        pubAuthRulePatterns: [[ANY_CHARACTERS], []],
        subAuthRulePatterns: [[ANY_CHARACTERS], []]
      })
    }, {validators: this.atLeastOne(Validators.required, ['clientId', 'userName'])});
    this.credentialsMqttFormGroup.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe((value) => {
      this.updateView(value);
    });
  }

  ngAfterViewInit() {
    this.addValueToAuthRulesSet('pubAuthRulePatterns');
    this.addValueToAuthRulesSet('subAuthRulePatterns');
    this.cd.detectChanges();
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
      deviceCredentialsMqttBasic: false
    };
  }

  writeValue(value: string) {
    if (isDefinedAndNotNull(value) && !isEmptyStr(value)) {
      this.clearRuleSets();
      const valueJson = JSON.parse(value);
      if (valueJson.authRules) {
        for (const rule of Object.keys(valueJson.authRules)) {
          if (valueJson.authRules[rule]?.length) {
            valueJson.authRules[rule].map(el => this.addValueToAuthRulesSet(rule, el));
          }
        }
      }
      this.credentialsMqttFormGroup.patchValue(valueJson, {emitEvent: false});
    }
  }

  private clearRuleSets() {
    this.pubRulesSet.clear();
    this.subRulesSet.clear();
  }

  updateView(value: BasicCredentials) {
    const formValue = JSON.stringify(value);
    this.propagateChange(formValue);
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

  addValueToAuthRulesSet(type: string, value: string = ANY_CHARACTERS) {
    switch (type) {
      case 'subAuthRulePatterns':
        this.subRulesSet.add(value);
        break;
      case 'pubAuthRulePatterns':
        this.pubRulesSet.add(value);
        break;
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

  private setAuthRulePatternsControl(set: Set<string>, type: AuthRulePatternsType) {
    const rulesArray = [Array.from(set).join(',')];
    switch (type) {
      case AuthRulePatternsType.PUBLISH:
        this.credentialsMqttFormGroup.get('authRules').get('pubAuthRulePatterns').setValue(rulesArray);
        break;
      case AuthRulePatternsType.SUBSCRIBE:
        this.credentialsMqttFormGroup.get('authRules').get('subAuthRulePatterns').setValue(rulesArray);
        break;
    }
  }

  private atLeastOne(validator: ValidatorFn, controls: string[] = null) {
    return (group: UntypedFormGroup): ValidationErrors | null => {
      if (!controls) {
        controls = Object.keys(group.controls);
      }
      const hasAtLeastOne = group?.controls && controls.some(k => !validator(group.controls[k]));
      return hasAtLeastOne ? null : {atLeastOne: true};
    };
  }

  onClickTbCopyButton(value: string) {
    if (value === 'clientId') {
      this.copyClientIdBtn.copy(this.credentialsMqttFormGroup.get(value)?.value);
    } else if (value === 'userName') {
      this.copyUsernameBtn.copy(this.credentialsMqttFormGroup.get(value)?.value);
    }
  }

  regenerate(type: string) {
    switch (type) {
      case 'clientId':
        this.credentialsMqttFormGroup.patchValue({
          clientId: clientIdRandom()
        });
        break;
      case 'userName':
        this.credentialsMqttFormGroup.patchValue({
          userName: clientUserNameRandom()
        });
        break;
    }
  }
}
