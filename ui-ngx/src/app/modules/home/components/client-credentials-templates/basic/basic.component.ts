///
/// Copyright © 2016-2023 The Thingsboard Authors
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

import { Component, EventEmitter, forwardRef, Input, OnDestroy, Output } from '@angular/core';
import {
  ControlValueAccessor,
  FormBuilder,
  FormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR, UntypedFormGroup,
  ValidationErrors,
  Validator,
  ValidatorFn,
  Validators
} from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { isDefinedAndNotNull, isEmptyStr, isString } from '@core/utils';
import { AuthRulePatternsType, AuthRules, BasicMqttCredentials, MqttClientCredentials } from '@shared/models/client-crenetials.model';
import { MatDialog } from '@angular/material/dialog';
import {
  ChangeMqttBasicPasswordDialogComponent,
  ChangeMqttBasicPasswordDialogData
} from '@home/pages/mqtt-client-credentials/change-mqtt-basic-password-dialog.component';
import { MatChipInputEvent } from '@angular/material/chips';

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
export class MqttCredentialsBasicComponent implements ControlValueAccessor, Validator, OnDestroy {

  @Input()
  disabled: boolean;

  @Input()
  entity: MqttClientCredentials;

  @Output()
  changePasswordCloseDialog = new EventEmitter<MqttClientCredentials>();

  authRulePatternsType = AuthRulePatternsType;
  credentialsMqttFormGroup: UntypedFormGroup;
  pubRulesSet: Set<string> = new Set();
  subRulesSet: Set<string> = new Set();

  private destroy$ = new Subject<void>();
  private propagateChange = (v: any) => {};

  constructor(public fb: FormBuilder,
              private dialog: MatDialog) {
    this.credentialsMqttFormGroup = this.fb.group({
      clientId: [null],
      userName: [null],
      password: [null],
      authRules: this.fb.group({
        pubAuthRulePatterns: [null],
        subAuthRulePatterns: [null]
      })
    }, {validators: this.atLeastOne(Validators.required, ['clientId', 'userName'])});
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
            const commaSeparatedRuleValues = valueJson.authRules[rule].join(',');
            valueJson.authRules[rule] = [commaSeparatedRuleValues];
          }
        }
      }
      this.credentialsMqttFormGroup.patchValue(valueJson, {emitEvent: false});
    } else {
      this.setDefaultAuthRuleValue();
    }
  }

  private clearRuleSets() {
    this.pubRulesSet.clear();
    this.subRulesSet.clear();
  }

  private setDefaultAuthRuleValue(defaultValue: string = '.*') {
    const authRulesControl = this.credentialsMqttFormGroup.get('authRules');
    for (const rule of Object.keys(authRulesControl.value)) {
      authRulesControl.patchValue({[rule]: defaultValue});
      this.addValueToAuthRulesSet(rule, defaultValue);
    }
  }

  updateView(value: BasicMqttCredentials) {
    value.authRules = this.formatAuthRules(value.authRules);
    const formValue = JSON.stringify(value);
    this.propagateChange(formValue);
  }

  private formatAuthRules(authRules: AuthRules): AuthRules {
    const result = {} as AuthRules;
    for (const rule of Object.keys(authRules)) {
      const value = authRules[rule];
      if (isString(value)) {
        result[rule] = value.length ? [authRules[rule]] : null;
      }
      if (Array.isArray(value)) {
        result[rule] = authRules[rule][0].length ? authRules[rule][0].split(',') : null;
      }
    }
    return result;
  }

  changePassword(): void {
    this.dialog.open<ChangeMqttBasicPasswordDialogComponent, ChangeMqttBasicPasswordDialogData,
      MqttClientCredentials>(ChangeMqttBasicPasswordDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        credentialsId: this.entity?.id
      }
    }).afterClosed().subscribe((res: MqttClientCredentials) => {
      if (res) {
        this.changePasswordCloseDialog.emit(res);
      }
    });
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

  addValueToAuthRulesSet(type: string, value: string) {
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
}
