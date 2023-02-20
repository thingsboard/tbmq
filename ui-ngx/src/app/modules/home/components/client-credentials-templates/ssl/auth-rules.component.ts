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

import { AfterViewInit, ChangeDetectorRef, Component, forwardRef, Input, OnDestroy } from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor,
  FormArray,
  FormBuilder, FormControl,
  FormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator, ValidatorFn,
  Validators
} from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import { MatChipInputEvent } from "@angular/material/chips";
import {
  AuthRulePatternsType,
  AuthRulesMapping, MqttClientCredentials,
  SslMqttCredentialsAuthRules
} from "@shared/models/client-crenetials.model";

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
    }],
  styleUrls: ['./auth-rules.component.scss']
})
export class AuthRulesComponent implements ControlValueAccessor, Validator, OnDestroy, AfterViewInit {

  @Input()
  disabled: boolean;

  @Input()
  entity: MqttClientCredentials;

  authRulePatternsType = AuthRulePatternsType;
  rulesMappingFormGroup: FormGroup;
  authRulesMappings: FormArray;
  pubRulesArray: string[][] = [];
  subRulesArray: string[][] = [];

  private valueChangeSubscription: Subscription = null;
  private destroy$ = new Subject();
  private propagateChange = (v: any) => {};

  get rulesFormArray(): FormArray {
    return this.rulesMappingFormGroup.get('authRulesMapping') as FormArray;
  }

  constructor(public fb: FormBuilder,
              public cd: ChangeDetectorRef) {
    this.rulesMappingFormGroup = this.fb.group({
      authRulesMapping: this.fb.array([])
    });
  }

  ngAfterViewInit() {
    if (!this.entity?.credentialsId) {
      this.addRule();
    }
  }

  addRule(): void {
    this.authRulesMappings = this.rulesFormArray;
    this.authRulesMappings.push(this.fb.group({
      certificateMatcherRegex: ['.*', [Validators.required, this.isUnique()]],
      pubAuthRulePatterns: [['.*'], []],
      subAuthRulePatterns: [['.*'], []]
    }));
    this.subRulesArray.push(['.*']);
    this.pubRulesArray.push(['.*']);
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
    this.disabled = isDisabled;
    if (this.disabled) {
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
      const certificateMatcherRegexList = this.rulesFormArray.value.map(function(item){ return item.certificateMatcherRegex });
      const formArrayHasDuplicates = certificateMatcherRegexList.some(
        (item, idx) => {
          const hasDuplicates = certificateMatcherRegexList.indexOf(item) !== idx;
          if (hasDuplicates) duplicateItem = item;
          return hasDuplicates;
        }
      );
      if (formArrayHasDuplicates && duplicateItem === value) return { notUnique: {valid: false} };
      return null;
    };
  }

  writeValue(authRulesMapping: SslMqttCredentialsAuthRules): void {
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
        if (this.disabled) {
          rulesControl.disable();
        }
        rulesControls.push(rulesControl);
        index++;
      }
    }
    this.rulesMappingFormGroup.setControl('authRulesMapping', this.fb.array(rulesControls));
    this.valueChangeSubscription = this.rulesMappingFormGroup.valueChanges.subscribe((value) => {
      this.updateView(value);
    });
  }

  updateView(value: any) {
    value.authRulesMapping = this.formatAuthRules(value.authRulesMapping);
    this.rulesMappingFormGroup.patchValue(value, { emitEvent: false });
    this.propagateChange(this.prepareValues(value.authRulesMapping));
  }

  private formatAuthRules(authRulesMapping: any) {
    const newValue = [];
    authRulesMapping.forEach(
      rule => {
        newValue.push({
          certificateMatcherRegex: rule.certificateMatcherRegex,
          pubAuthRulePatterns: Array.isArray(rule.pubAuthRulePatterns) ? rule.pubAuthRulePatterns[0] : rule.pubAuthRulePatterns,
          subAuthRulePatterns: Array.isArray(rule.subAuthRulePatterns) ? rule.subAuthRulePatterns[0] : rule.subAuthRulePatterns
        });
      }
    );
    return newValue;
  }

  private prepareValues(authRulesMapping: Array<AuthRulesMapping>) {
    const result = {};
    authRulesMapping.map( obj => {
      const key = obj?.certificateMatcherRegex;
      if (key) {
        result[key] = {};
        result[key].pubAuthRulePatterns = obj?.pubAuthRulePatterns ? obj?.pubAuthRulePatterns.split(',') : null;
        result[key].subAuthRulePatterns = obj?.subAuthRulePatterns ? obj?.subAuthRulePatterns.split(',') : null;
      }
    });
    return result;
  }

  addTopicRule(event: MatChipInputEvent, index: number, type: AuthRulePatternsType) {
    const input = event.input;
    const value = event.value;
    if ((value || '').trim()) {
      switch (type) {
        case AuthRulePatternsType.SUBSCRIBE:
          if (this.subRulesArray[index].indexOf(value) < 0) this.subRulesArray[index].push(value);
          break;
        case AuthRulePatternsType.PUBLISH:
          if (this.pubRulesArray[index].indexOf(value) < 0) this.pubRulesArray[index].push(value);
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
        if (optIndex >= 0) this.subRulesArray[index].splice(optIndex, 1);
        break;
      case AuthRulePatternsType.PUBLISH:
        optIndex = this.pubRulesArray[index].indexOf(rule);
        if (optIndex >= 0) this.pubRulesArray[index].splice(optIndex, 1);
        break;
    }
    this.updateTopicRuleControl(index, type);
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

