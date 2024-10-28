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
  Validators
} from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { isDefinedAndNotNull, isEmptyStr } from '@core/utils';
import {
  ANY_CHARACTERS,
  AuthRulePatternsType,
  ScramCredentials,
  ClientCredentials,
  ShaType,
  shaTypeTranslationMap
} from '@shared/models/credentials.model';
import { MatChipEditedEvent, MatChipInputEvent } from '@angular/material/chips';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { clientUserNameRandom } from '@shared/models/ws-client.model';
import { ENTER, TAB } from "@angular/cdk/keycodes";

@Component({
  selector: 'tb-mqtt-credentials-scram',
  templateUrl: './scram.component.html',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => MqttCredentialsScramComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => MqttCredentialsScramComponent),
      multi: true,
    }],
  styleUrls: ['./scram.component.scss']
})
export class MqttCredentialsScramComponent implements ControlValueAccessor, Validator, OnDestroy, AfterViewInit {

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
  shaTypes = Object.values(ShaType);
  shaTypeTranslations = shaTypeTranslationMap;
  separatorKeysCodes = [ENTER, TAB];

  private maskedPassword = '********';
  private newPassword: string;
  private destroy$ = new Subject<void>();
  private propagateChange = (v: any) => {};

  constructor(public fb: FormBuilder,
              private cd: ChangeDetectorRef) {
    this.credentialsMqttFormGroup = this.fb.group({
      userName: [null, [Validators.required]],
      password: [null, [Validators.required]],
      algorithm: [ShaType.SHA_256, [Validators.required]],
      authRules: this.fb.group({
        pubAuthRulePatterns: [[ANY_CHARACTERS], []],
        subAuthRulePatterns: [[ANY_CHARACTERS], []]
      }),
      salt: [null, []],
      serverKey: [null, []],
      storedKey: [null, []]
    });
    this.credentialsMqttFormGroup.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe((value) => {
      this.updateView(value);
    });
    this.credentialsMqttFormGroup.get('password').valueChanges.subscribe(value => {
      if (value !== this.maskedPassword) {
        this.newPassword = value;
      }
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
      credentialsMqttScram: false
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
      valueJson.password = this.maskedPassword;
      this.credentialsMqttFormGroup.patchValue(valueJson, {emitEvent: false});
      this.newPassword = null;
    }
  }

  updateView(value: ScramCredentials) {
    if (this.newPassword) {
      value.password = this.newPassword;
    } else {
      delete value.password;
    }
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

  onClickTbCopyButton(value: string) {
    if (value === 'userName') {
      this.copyUsernameBtn.copy(this.credentialsMqttFormGroup.get(value)?.value);
    }
  }

  regenerate(type: string) {
    switch (type) {
      case 'userName':
        this.credentialsMqttFormGroup.patchValue({
          userName: clientUserNameRandom()
        });
        break;
    }
  }

  algorithmChanged() {
    if (this.entity?.id) {
      this.newPassword = null;
      this.credentialsMqttFormGroup.get('password').patchValue(null);
    }
  }

  private clearRuleSets() {
    this.pubRulesSet.clear();
    this.subRulesSet.clear();
  }

  private addValueToAuthRulesSet(type: string, value: string = ANY_CHARACTERS) {
    switch (type) {
      case 'subAuthRulePatterns':
        this.subRulesSet.add(value);
        break;
      case 'pubAuthRulePatterns':
        this.pubRulesSet.add(value);
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
}
