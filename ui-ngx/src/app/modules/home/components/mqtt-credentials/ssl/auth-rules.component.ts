///
/// Copyright © 2016-2022 The Thingsboard Authors
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

import { Component, forwardRef, Input, OnDestroy } from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor, FormArray,
  FormBuilder,
  FormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR, ValidationErrors,
  Validator, Validators
} from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import { MatChipInputEvent } from "@angular/material/chips";

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
export class AuthRulesComponent implements ControlValueAccessor, Validator, OnDestroy {

  @Input()
  disabled: boolean;

  rulesMappingFormGroup: FormGroup;
  rulesMappings: FormArray;
  pubRulesArray: string[][] = [];
  subRulesArray: string[][] = [];

  private valueChangeSubscription: Subscription = null;
  private destroy$ = new Subject();
  private propagateChange = (v: any) => {};

  constructor(public fb: FormBuilder) {
    this.rulesMappingFormGroup = this.fb.group({});
    this.rulesMappingFormGroup.addControl('authRulesMapping',
      this.fb.array([]));
    this.rulesFormArray().valueChanges.subscribe((value) => {
      this.updateView(value);
    });
  }

  rulesFormArray(): FormArray {
    return this.rulesMappingFormGroup.get('authRulesMapping') as FormArray;
  }

  addRule(): void {
    this.rulesMappings = this.rulesFormArray() as FormArray;
    this.rulesMappings.push(this.fb.group({
      certificateMatcherRegex: [null, [Validators.required]],
      pubAuthRulePatterns: [null, []],
      subAuthRulePatterns: [null, []]
    }));
    this.subRulesArray.push([]);
    this.pubRulesArray.push([]);
  }

  removeRule(index: number) {
    this.rulesMappingFormGroup.markAsDirty();
    this.subRulesArray[index].splice(0);
    this.pubRulesArray[index].splice(0);
    (this.rulesFormArray() as FormArray).removeAt(index);
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
    if (!this.rulesFormArray().value?.length) {
      return { rulesMappingLength: true };
    }
    return this.rulesMappingFormGroup.valid ? null : { rulesMapping: true };
  }

  writeValue(authRulesMapping: any): void {
    if (this.valueChangeSubscription) {
      this.valueChangeSubscription.unsubscribe();
    }
    const rulesControls: Array<AbstractControl> = [];
    if (authRulesMapping) {
      let index = 0;
      for (const rule of Object.keys(authRulesMapping)) {
        const rulesControl = this.fb.group({
          certificateMatcherRegex: [rule, [Validators.required]],
          subAuthRulePatterns: [authRulesMapping[rule].subAuthRulePatterns, []],
          pubAuthRulePatterns: [authRulesMapping[rule].pubAuthRulePatterns, []]
        });
        this.subRulesArray[index] = authRulesMapping[rule].subAuthRulePatterns[0].split(',');
        this.pubRulesArray[index] = authRulesMapping[rule].pubAuthRulePatterns[0].split(',');
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
    value.authRulesMapping = this.formatValue(value.authRulesMapping);
    this.rulesMappingFormGroup.patchValue(value, { emitEvent: false });
    this.propagateChange(this.prepareValues(value.authRulesMapping));
  }

  private formatValue(authRulesMapping) {
    const newValue = [];
    authRulesMapping.forEach(
      rule => {
        newValue.push({
          certificateMatcherRegex: rule.certificateMatcherRegex,
          pubAuthRulePatterns: Array.isArray(rule.pubAuthRulePatterns) ? rule.pubAuthRulePatterns[0] : rule.pubAuthRulePatterns,
          subAuthRulePatterns: Array.isArray(rule.subAuthRulePatterns) ? rule.subAuthRulePatterns[0] : rule.subAuthRulePatterns
        })
      }
    );
    return newValue;
  }

  private prepareValues(authRulesMapping: any) {
    const newObj = {};
    authRulesMapping.map( obj => {
      const key = obj?.certificateMatcherRegex;
      if (key) {
        newObj[key] = {};
        if (obj?.pubAuthRulePatterns) newObj[key].pubAuthRulePatterns = [obj?.pubAuthRulePatterns];
        if (obj?.subAuthRulePatterns) newObj[key].subAuthRulePatterns = [obj?.subAuthRulePatterns];
      }
    });
    return newObj;
  }

  removePubTopicRule(rule: string, index: number) {
    const optIndex = this.pubRulesArray[index].indexOf(rule);
    if (optIndex >= 0) {
      this.pubRulesArray[index].splice(optIndex, 1);
    }
    this.setTopicRuleControl(index);
  }

  addPubTopicRule(event: MatChipInputEvent, index: number) {
    const input = event.input;
    const value = event.value;
    if ((value || '').trim()) {
      this.pubRulesArray[index].push(value);
    }
    if (input) {
      input.value = '';
    }
    this.setTopicRuleControl(index);
  }

  removeSubTopicRule(rule: string, index: number) {
    const optIndex = this.subRulesArray[index].indexOf(rule);
    if (optIndex >= 0) {
      this.subRulesArray[index].splice(optIndex, 1);
    }
    this.setTopicRuleControl(index);
  }

  addSubTopicRule(event: MatChipInputEvent, index: number) {
    const input = event.input;
    const value = event.value;
    if ((value || '').trim()) {
      this.subRulesArray[index].push(value);
    }
    if (input) {
      input.value = '';
    }
    this.setTopicRuleControl(index);
  }

  private setTopicRuleControl(index: number) {
    const pubValue = this.pubRulesArray[index].join(',');
    const subValue = this.subRulesArray[index].join(',');
    this.rulesFormArray().at(index).get('pubAuthRulePatterns').setValue(pubValue);
    this.rulesFormArray().at(index).get('subAuthRulePatterns').setValue(subValue);
  }
}

