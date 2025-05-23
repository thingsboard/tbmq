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

import { AfterViewInit, ChangeDetectorRef, Component, forwardRef, OnDestroy, input, model } from '@angular/core';
import { AbstractControl, ControlValueAccessor, FormBuilder, FormControl, NG_VALIDATORS, NG_VALUE_ACCESSOR, UntypedFormArray, UntypedFormGroup, ValidationErrors, Validator, ValidatorFn, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import { MatChipEditedEvent, MatChipInputEvent, MatChipGrid, MatChipRow, MatChipRemove, MatChipInput } from '@angular/material/chips';
import {
  ANY_CHARACTERS,
  AuthRulePatternsType,
  AuthRulesMapping,
  ClientCredentials,
  SslAuthRulesMapping,
  SslCredentialsAuthRules
} from '@shared/models/credentials.model';
import { ENTER, TAB } from '@angular/cdk/keycodes';
import { MatExpansionPanel, MatExpansionPanelHeader, MatExpansionPanelTitle, MatExpansionPanelDescription } from '@angular/material/expansion';
import { TranslateModule } from '@ngx-translate/core';

import { MatError, MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIconButton, MatButton } from '@angular/material/button';

@Component({
    selector: 'tb-auth-rules',
    templateUrl: './auth-rules.component.html',
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => AuthRulesComponent),
            multi: true
        },
        {
            provide: NG_VALIDATORS,
            useExisting: forwardRef(() => AuthRulesComponent),
            multi: true,
        }
    ],
    styleUrls: ['./auth-rules.component.scss'],
    imports: [FormsModule, MatExpansionPanel, MatExpansionPanelHeader, MatExpansionPanelTitle, TranslateModule, MatExpansionPanelDescription, MatError, ReactiveFormsModule, MatFormField, MatLabel, MatInput, MatChipGrid, MatChipRow, MatChipRemove, MatIcon, MatChipInput, MatSuffix, MatTooltip, MatIconButton, MatButton]
})
export class AuthRulesComponent implements ControlValueAccessor, Validator, OnDestroy, AfterViewInit {

  disabled = model<boolean>();
  readonly entity = input<ClientCredentials>();

  authRulePatternsType = AuthRulePatternsType;
  rulesMappingFormGroup: UntypedFormGroup;
  pubRulesArray: string[][] = [];
  subRulesArray: string[][] = [];
  separatorKeysCodes = [ENTER, TAB];

  private valueChangeSubscription: Subscription = null;
  private destroy$ = new Subject<void>();
  private propagateChange = (v: any) => {};

  get rulesFormArray(): UntypedFormArray {
    return this.rulesMappingFormGroup.get('authRulesMapping') as UntypedFormArray;
  }

  constructor(public fb: FormBuilder,
              public cd: ChangeDetectorRef) {
    this.rulesMappingFormGroup = this.fb.group({
      authRulesMapping: this.fb.array([])
    });
    this.rulesFormArray.push(this.fb.group({
      certificateMatcherRegex: [ANY_CHARACTERS, [Validators.required, this.isUnique()]],
      pubAuthRulePatterns: [[ANY_CHARACTERS], []],
      subAuthRulePatterns: [[ANY_CHARACTERS], []]
    }));
    this.subRulesArray.push([ANY_CHARACTERS]);
    this.pubRulesArray.push([ANY_CHARACTERS]);
    this.rulesMappingFormGroup.valueChanges.subscribe(value =>  this.updateView(value));
  }

  ngAfterViewInit() {
    this.rulesFormArray.updateValueAndValidity();
  }

  addRule(): void {
    this.rulesFormArray.push(this.fb.group({
      certificateMatcherRegex: [ANY_CHARACTERS, [Validators.required, this.isUnique()]],
      pubAuthRulePatterns: [[ANY_CHARACTERS], []],
      subAuthRulePatterns: [[ANY_CHARACTERS], []]
    }));
    this.subRulesArray.push([ANY_CHARACTERS]);
    this.pubRulesArray.push([ANY_CHARACTERS]);
    this.cd.markForCheck();
  }

  removeRule(index: number) {
    this.rulesMappingFormGroup.markAsDirty();
    this.subRulesArray.splice(index, 1);
    this.pubRulesArray.splice(index, 1);
    this.rulesFormArray.removeAt(index);
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
      this.rulesMappingFormGroup.disable({emitEvent: false});
    } else {
      this.rulesMappingFormGroup.enable({emitEvent: false});
    }
  }

  validate(): ValidationErrors | null {
    if (!this.rulesFormArray.value?.length) {
      return { rulesMappingLength: true };
    }
    return this.rulesMappingFormGroup.valid ? null : { rulesMapping: true };
  }

  isUnique(): ValidatorFn {
    return (control: FormControl) => {
      let duplicateItem: string;
      const value = control.value;
      const certificateMatcherRegexList = this.rulesFormArray.value.map(function(item) {
        return item.certificateMatcherRegex;
      });
      const formArrayHasDuplicates = certificateMatcherRegexList.some(
        (item, idx) => {
          const hasDuplicates = certificateMatcherRegexList.indexOf(item) !== idx;
          if (hasDuplicates) {
            duplicateItem = item;
          }
          return hasDuplicates;
        }
      );
      if (formArrayHasDuplicates && duplicateItem === value) {
        return {notUnique: {valid: false}};
      }
      return null;
    };
  }

  writeValue(authRulesMapping: SslCredentialsAuthRules): void {
    if (this.valueChangeSubscription) {
      this.valueChangeSubscription.unsubscribe();
    }
    const rulesControls: Array<AbstractControl> = [];
    if (authRulesMapping) {
      let index = 0;
      for (const rule of Object.keys(authRulesMapping)) {
        const rulesControl = this.fb.group({
          certificateMatcherRegex: [rule, [Validators.required, this.isUnique()]],
          subAuthRulePatterns: [authRulesMapping[rule].subAuthRulePatterns ? authRulesMapping[rule].subAuthRulePatterns.join(',') : []],
          pubAuthRulePatterns: [authRulesMapping[rule].pubAuthRulePatterns ? authRulesMapping[rule].pubAuthRulePatterns.join(',') : []]
        });
        this.subRulesArray[index] = authRulesMapping[rule].subAuthRulePatterns ? authRulesMapping[rule].subAuthRulePatterns : [];
        this.pubRulesArray[index] = authRulesMapping[rule].pubAuthRulePatterns ? authRulesMapping[rule].pubAuthRulePatterns : [];
        if (this.disabled()) {
          rulesControl.disable();
        }
        rulesControls.push(rulesControl);
        index++;
      }
      this.rulesMappingFormGroup.setControl('authRulesMapping', this.fb.array(rulesControls), {emitEvent: false});
    }
  }

  addTopicRule(event: MatChipInputEvent, index: number, type: AuthRulePatternsType) {
    const input = event.input;
    const value = event.value;
    if ((value || '').trim()) {
      switch (type) {
        case AuthRulePatternsType.SUBSCRIBE:
          if (this.subRulesArray[index].indexOf(value) < 0) {
            this.subRulesArray[index].push(value);
          }
          break;
        case AuthRulePatternsType.PUBLISH:
          if (this.pubRulesArray[index].indexOf(value) < 0) {
            this.pubRulesArray[index].push(value);
          }
          break;
      }
    }
    if (input) {
      input.value = '';
    }
    this.updateTopicRuleControl(index, type);
  }

  removeTopicRule(rule: string, index: number, type: AuthRulePatternsType) {
    let optIndex;
    switch (type) {
      case AuthRulePatternsType.SUBSCRIBE:
        optIndex = this.subRulesArray[index].indexOf(rule);
        if (optIndex >= 0) {
          this.subRulesArray[index].splice(optIndex, 1);
        }
        break;
      case AuthRulePatternsType.PUBLISH:
        optIndex = this.pubRulesArray[index].indexOf(rule);
        if (optIndex >= 0) {
          this.pubRulesArray[index].splice(optIndex, 1);
        }
        break;
    }
    this.updateTopicRuleControl(index, type);
  }

  editTopicRule(event: MatChipEditedEvent, index: number, type: AuthRulePatternsType): void {
    let optIndex: number;
    const oldRule = event.chip.value;
    const newRule = event.value;
    switch (type) {
      case AuthRulePatternsType.SUBSCRIBE:
        optIndex = this.subRulesArray[index].indexOf(oldRule);
        if (optIndex !== -1) {
          this.subRulesArray[index].splice(optIndex, 1, newRule);
        }
        break;
      case AuthRulePatternsType.PUBLISH:
        optIndex = this.pubRulesArray[index].indexOf(oldRule);
        if (optIndex !== -1) {
          this.pubRulesArray[index].splice(optIndex, 1, newRule);
        }
        break;
    }
    this.updateTopicRuleControl(index, type);
  }

  private updateView(value: SslAuthRulesMapping) {
    this.rulesMappingFormGroup.patchValue(value, {emitEvent: false});
    this.propagateChange(this.prepareValues(value.authRulesMapping));
  }

  private prepareValues(authRulesMapping: AuthRulesMapping[]): SslCredentialsAuthRules {
    const result = {};
    authRulesMapping.map((obj, index) => {
      const key = obj?.certificateMatcherRegex;
      if (key) {
        result[key] = {};
        result[key].pubAuthRulePatterns = obj?.pubAuthRulePatterns ? this.pubRulesArray[index] : null;
        result[key].subAuthRulePatterns = obj?.subAuthRulePatterns ? this.subRulesArray[index] : null;
      }
    });
    return result;
  }

  private updateTopicRuleControl(index: number, type: AuthRulePatternsType) {
    switch (type) {
      case AuthRulePatternsType.SUBSCRIBE:
        this.rulesFormArray.at(index).get('subAuthRulePatterns').setValue(this.subRulesArray[index].join(','));
        break;
      case AuthRulePatternsType.PUBLISH:
        this.rulesFormArray.at(index).get('pubAuthRulePatterns').setValue(this.pubRulesArray[index].join(','));
        break;
    }
  }
}

