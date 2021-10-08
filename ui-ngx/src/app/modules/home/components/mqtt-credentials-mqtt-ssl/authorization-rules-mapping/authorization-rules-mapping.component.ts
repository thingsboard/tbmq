///
/// Copyright © 2016-2020 The Thingsboard Authors
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

import { Component, forwardRef, Injector, Input, OnInit } from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor,
  FormArray,
  FormBuilder,
  FormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors, Validator,
  Validators
} from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Subscription } from 'rxjs';
import { SslMqttCredentials } from '@shared/models/mqtt-client-crenetials.model';

export interface AuthorizationRulesMap {
  certificateMatcherRegex: string;
  topicRule: string;
}

@Component({
  selector: 'tb-authorization-rules-mapping',
  templateUrl: './authorization-rules-mapping.component.html',
  styleUrls: ['./authorization-rules-mapping.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => AuthorizationRulesMappingComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => AuthorizationRulesMappingComponent),
      multi: true
    }
  ]
})
export class AuthorizationRulesMappingComponent implements ControlValueAccessor, OnInit, Validator {

  @Input() disabled: boolean;

  rulesMappingFormGroup: FormGroup;

  rulesMappings: FormArray;

  private propagateChange = null;
  private valueChangeSubscription: Subscription = null;

  get authorizationRulesMapping() {
    return this.rulesMappingFormGroup.get('authorizationRulesMapping');
  }

  constructor(protected store: Store<AppState>,
              private injector: Injector,
              private fb: FormBuilder) {
  }

  ngOnInit(): void {
    this.rulesMappingFormGroup = this.fb.group({});
    this.rulesMappingFormGroup.addControl('authorizationRulesMapping',
      this.fb.array([]));
    this.rulesMappingFormGroup.get('authorizationRulesMapping').valueChanges.subscribe((value) => {
      this.updateView(value);
    });
  }

  rulesFormArray(): FormArray {
    return this.rulesMappingFormGroup.get('authorizationRulesMapping') as FormArray;
  }

  addRule(): void {
    this.rulesMappings = this.rulesMappingFormGroup.get('authorizationRulesMapping') as FormArray;
    this.rulesMappings.push(this.fb.group({
      certificateMatcherRegex: ['', [Validators.required]],
      topicRule: ['', [Validators.required]]
    }));
  }

  removeRule(index: number) {
    (this.rulesMappingFormGroup.get('authorizationRulesMapping') as FormArray).removeAt(index);
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  writeValue(authorizationRules: any): void {
    if (this.valueChangeSubscription) {
      this.valueChangeSubscription.unsubscribe();
    }
    const rulesControls: Array<AbstractControl> = [];
    if (authorizationRules) {
      for (const rule of Object.keys(authorizationRules)) {
        const rulesControl = this.fb.group({
          certificateMatcherRegex: [rule, [Validators.required]],
          topicRule: [authorizationRules[rule], [Validators.required]]
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

  updateView(value: SslMqttCredentials) {
    this.rulesMappingFormGroup.patchValue(value, { emitEvent: false });
    this.propagateChange(this.prepareValues(value.authorizationRulesMapping));
  }

  private prepareValues(authorizationRulesMapping: any) {
    const newObj = {};
    authorizationRulesMapping.forEach( (obj: AuthorizationRulesMap) => {
      const key = obj.certificateMatcherRegex;
      newObj[key] = obj.topicRule;
    });
    return newObj;
  }

  validate(control: AbstractControl): ValidationErrors | null {
    if (!this.rulesMappingFormGroup.get('authorizationRulesMapping').value?.length) {
      return { rulesMappingLength: true };
    }
    return this.rulesMappingFormGroup.valid ? null : { rulesMapping: true };
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.rulesMappingFormGroup.disable();
    } else {
      this.rulesMappingFormGroup.enable();
    }
  }

}
