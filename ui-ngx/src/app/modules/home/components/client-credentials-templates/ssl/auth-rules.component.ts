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

import { AfterViewInit, ChangeDetectorRef, Component, forwardRef, OnDestroy, input, model } from '@angular/core';
import { AbstractControl, ControlValueAccessor, FormBuilder, FormControl, NG_VALIDATORS, NG_VALUE_ACCESSOR, UntypedFormArray, UntypedFormGroup, ValidationErrors, Validator, ValidatorFn, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import {
  ANY_CHARACTERS,
  AuthRulesMapping,
  ClientCredentials,
  SslAuthRulesMapping,
  SslCredentialsAuthRules
} from '@shared/models/credentials.model';
import { MatExpansionPanel, MatExpansionPanelHeader, MatExpansionPanelTitle, MatExpansionPanelDescription } from '@angular/material/expansion';
import { TranslateModule } from '@ngx-translate/core';

import { MatError, MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIconButton, MatButton } from '@angular/material/button';
import { TopicRulesChipListComponent } from '@shared/components/topic-rules-chip-list.component';

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
    imports: [FormsModule, MatExpansionPanel, MatExpansionPanelHeader, MatExpansionPanelTitle, TranslateModule, MatExpansionPanelDescription, MatError, ReactiveFormsModule, MatFormField, MatLabel, MatInput, MatIcon, MatSuffix, MatTooltip, MatIconButton, MatButton, TopicRulesChipListComponent]
})
export class AuthRulesComponent implements ControlValueAccessor, Validator, OnDestroy, AfterViewInit {

  disabled = model<boolean>();
  readonly entity = input<ClientCredentials>();

  rulesMappingFormGroup: UntypedFormGroup;

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
    this.cd.markForCheck();
  }

  removeRule(index: number) {
    this.rulesMappingFormGroup.markAsDirty();
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
      for (const rule of Object.keys(authRulesMapping)) {
        const rulesControl = this.fb.group({
          certificateMatcherRegex: [rule, [Validators.required, this.isUnique()]],
          subAuthRulePatterns: [authRulesMapping[rule].subAuthRulePatterns || []],
          pubAuthRulePatterns: [authRulesMapping[rule].pubAuthRulePatterns || []]
        });
        if (this.disabled()) {
          rulesControl.disable();
        }
        rulesControls.push(rulesControl);
      }
      this.rulesMappingFormGroup.setControl('authRulesMapping', this.fb.array(rulesControls), {emitEvent: false});
    }
  }

  private updateView(value: SslAuthRulesMapping) {
    this.rulesMappingFormGroup.patchValue(value, {emitEvent: false});
    this.propagateChange(this.prepareValues(value.authRulesMapping));
  }

  private prepareValues(authRulesMapping: AuthRulesMapping[]): SslCredentialsAuthRules {
    const result = {};
    authRulesMapping.map((obj) => {
      const key = obj?.certificateMatcherRegex;
      if (key) {
        result[key] = {};
        result[key].pubAuthRulePatterns = obj?.pubAuthRulePatterns || null;
        result[key].subAuthRulePatterns = obj?.subAuthRulePatterns || null;
      }
    });
    return result;
  }
}

