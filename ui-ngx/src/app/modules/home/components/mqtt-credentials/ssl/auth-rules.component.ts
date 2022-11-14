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

  private valueChangeSubscription: Subscription = null;
  private destroy$ = new Subject();
  private propagateChange = (v: any) => {};

  constructor(public fb: FormBuilder) {
    this.rulesMappingFormGroup = this.fb.group({});
    this.rulesMappingFormGroup.addControl('authorizationRulesMapping',
      this.fb.array([]));
    this.rulesFormArray().valueChanges.subscribe((value) => {
      this.updateView(value);
    });
  }

  rulesFormArray(): FormArray {
    return this.rulesMappingFormGroup.get('authorizationRulesMapping') as FormArray;
  }

  addRule(): void {
    this.rulesMappings = this.rulesFormArray() as FormArray;
    this.rulesMappings.push(this.fb.group({
      certificateMatcherRegex: ['', [Validators.required]],
      topicRule: ['', [Validators.required]]
    }));
  }

  removeRule(index: number) {
    this.rulesMappingFormGroup.markAsDirty();
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

  writeValue(authorizationRulesMapping: any): void {
    if (this.valueChangeSubscription) {
      this.valueChangeSubscription.unsubscribe();
    }
    const rulesControls: Array<AbstractControl> = [];
    if (authorizationRulesMapping) {
      for (const rule of Object.keys(authorizationRulesMapping)) {
        const rulesControl = this.fb.group({
          certificateMatcherRegex: [rule, [Validators.required]],
          topicRule: [authorizationRulesMapping[rule], [Validators.required]]
        });
        if (this.disabled) {
          rulesControl.disable();
        }
        rulesControls.push(rulesControl);
      }
    }
    this.rulesMappingFormGroup.setControl('authorizationRulesMapping', this.fb.array(rulesControls));
    this.valueChangeSubscription = this.rulesMappingFormGroup.valueChanges.subscribe((value) => {
      this.updateView(value);
    });
  }

  updateView(value: any) {
    value.authorizationRulesMapping = this.formatValue(value.authorizationRulesMapping);
    this.rulesMappingFormGroup.patchValue(value, { emitEvent: false });
    this.propagateChange(this.prepareValues(value.authorizationRulesMapping));
  }

  private formatValue(authorizationRulesMapping) {
    const newValue = [];
    authorizationRulesMapping.forEach(
      rule => {
        newValue.push({
          certificateMatcherRegex: rule.certificateMatcherRegex,
          topicRule: Array.isArray(rule.topicRule) ? rule.topicRule[0] : rule.topicRule
        })
      }
    );
    return newValue;
  }

  private prepareValues(authorizationRulesMapping: any) {
    const newObj = {};
    authorizationRulesMapping.forEach( (obj: any) => {
      const key = obj.certificateMatcherRegex;
      newObj[key] = [obj.topicRule];
    });
    return newObj;
  }

}

