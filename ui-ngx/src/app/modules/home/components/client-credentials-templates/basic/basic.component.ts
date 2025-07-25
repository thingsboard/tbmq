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

import {
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  forwardRef,
  OnDestroy,
  input,
  model
} from '@angular/core';
import { ControlValueAccessor, FormBuilder, NG_VALIDATORS, NG_VALUE_ACCESSOR, UntypedFormGroup, ValidationErrors, Validator, ValidatorFn, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { isDefinedAndNotNull, isEmptyStr } from '@core/utils';
import { ANY_CHARACTERS, AuthRulePatternsType, BasicCredentials, ClientCredentials } from '@shared/models/credentials.model';
import { MatChipEditedEvent, MatChipInputEvent, MatChipGrid, MatChipRow, MatChipRemove, MatChipInput } from '@angular/material/chips';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { clientIdRandom, clientUserNameRandom } from '@shared/models/ws-client.model';
import { ENTER, TAB } from '@angular/cdk/keycodes';
import { TranslateModule } from '@ngx-translate/core';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatIconButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';

import { TogglePasswordComponent } from '@shared/components/button/toggle-password.component';
import { MatExpansionPanel, MatExpansionPanelHeader, MatExpansionPanelTitle } from '@angular/material/expansion';

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
        }
    ],
    styleUrls: ['./basic.component.scss'],
    imports: [FormsModule, ReactiveFormsModule, TranslateModule, MatFormField, MatLabel, MatInput, CopyButtonComponent, MatSuffix, MatIconButton, MatTooltip, MatIcon, TogglePasswordComponent, MatExpansionPanel, MatExpansionPanelHeader, MatExpansionPanelTitle, MatChipGrid, MatChipRow, MatChipRemove, MatChipInput]
})
export class MqttCredentialsBasicComponent implements ControlValueAccessor, Validator, OnDestroy, AfterViewInit {

  disabled = model<boolean>();
  readonly entity = input<ClientCredentials>();

  authRulePatternsType = AuthRulePatternsType;
  credentialsMqttFormGroup: UntypedFormGroup;
  pubRulesSet = new Set<string>();
  subRulesSet = new Set<string>();
  separatorKeysCodes = [ENTER, TAB];

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
    this.disabled.set(isDisabled);
    if (this.disabled()) {
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
