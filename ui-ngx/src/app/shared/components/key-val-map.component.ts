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

import { Component, forwardRef, Input, OnDestroy, OnInit, input } from '@angular/core';
import { AbstractControl, ControlValueAccessor, UntypedFormArray, UntypedFormBuilder, UntypedFormGroup, NG_VALIDATORS, NG_VALUE_ACCESSOR, Validator, Validators, ValidationErrors, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { SubscriptSizing, MatFormField } from '@angular/material/form-field';
import { isDefinedAndNotNull, isEqual } from '@core/utils';
import { TranslateModule } from '@ngx-translate/core';
import { AsyncPipe } from '@angular/common';
import { MatInput } from '@angular/material/input';
import { MatIconButton, MatButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { MatDivider } from '@angular/material/divider';

@Component({
    selector: 'tb-key-val-map',
    templateUrl: './key-val-map.component.html',
    styleUrls: ['./key-val-map.component.scss'],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => KeyValMapComponent),
            multi: true
        },
        {
            provide: NG_VALIDATORS,
            useExisting: forwardRef(() => KeyValMapComponent),
            multi: true,
        }
    ],
    imports: [FormsModule, ReactiveFormsModule, TranslateModule, MatFormField, MatInput, MatIconButton, MatTooltip, MatIcon, MatButton, AsyncPipe, MatDivider]
})
export class KeyValMapComponent extends PageComponent implements ControlValueAccessor, OnInit, OnDestroy, Validator {

  @Input() disabled: boolean;

  readonly isValueRequired = input(true);
  readonly titleText = input<string>();
  readonly keyPlaceholderText = input<string>();
  readonly valuePlaceholderText = input<string>();
  readonly noDataText = input<string>();
  readonly singleMode = input<boolean>(false);
  readonly singlePredefinedKey = input<string>();
  readonly singlePredefinedValue = input<string>();
  readonly addText = input<string>('action.add');
  readonly keyLabel = input<string>('key-val.key');
  readonly valueLabel = input<string>('key-val.value');
  readonly isStrokedButton = input(true);
  readonly subscriptSizing = input<SubscriptSizing>('dynamic');

  kvListFormGroup: UntypedFormGroup;
  private destroy$ = new Subject<void>();
  private propagateChange = null;

  constructor(protected store: Store<AppState>,
              private fb: UntypedFormBuilder) {
    super(store);
  }

  ngOnInit(): void {
    this.kvListFormGroup = this.fb.group({
      keyVals: this.fb.array([])
    });

    this.kvListFormGroup.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(() => this.updateModel());
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  keyValsFormArray(): UntypedFormArray {
    return this.kvListFormGroup.get('keyVals') as UntypedFormArray;
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState?(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.kvListFormGroup.disable({emitEvent: false});
    } else {
      this.kvListFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(keyValMap: {[key: string]: string}): void {
    const keyValsControls: Array<AbstractControl> = [];
    if (keyValMap && !isEqual(keyValMap, {})) {
      for (const property of Object.keys(keyValMap)) {
        if (Object.prototype.hasOwnProperty.call(keyValMap, property)) {
          keyValsControls.push(this.fb.group({
            key: [property, [Validators.required]],
            value: [keyValMap[property], this.isValueRequired() ? [Validators.required] : []]
          }));
        }
      }
    }
    this.kvListFormGroup.setControl('keyVals', this.fb.array(keyValsControls), {emitEvent: false});
    if (this.isSingleMode && this.isSinglePredefinedKey && !keyValsControls.length) {
      setTimeout(() => this.addKeyVal(), 0);
    }
    if (this.disabled) {
      this.kvListFormGroup.disable({emitEvent: false});
    } else {
      this.kvListFormGroup.enable({emitEvent: false});
    }
  }

  public removeKeyVal(index: number) {
    (this.kvListFormGroup.get('keyVals') as UntypedFormArray).removeAt(index);
  }

  public addKeyVal() {
    const keyValsFormArray = this.keyValsFormArray();
    const isFirstKey = keyValsFormArray.length === 0;
    keyValsFormArray.push(this.fb.group({
      key: [this.isSinglePredefinedKey && isFirstKey ? this.singlePredefinedKey() : null,
        this.isValueRequired() ? [Validators.required] : []],
      value: [this.isSinglePredefinedValue && isFirstKey ? this.singlePredefinedValue() : null,
        this.isValueRequired() ? [Validators.required] : []]
    }));
  }

  public validate(): ValidationErrors | null {
    return this.kvListFormGroup.valid ? null : { keyVals: { valid: false } };
  }

  get isSingleMode(): boolean {
    return this.singleMode();
  }

  get isSinglePredefinedKey(): boolean {
    return isDefinedAndNotNull(this.singlePredefinedKey());
  }

  get isSinglePredefinedValue(): boolean {
    return isDefinedAndNotNull(this.singlePredefinedValue());
  }

  private updateModel() {
    const kvList: {key: string; value: string}[] = this.kvListFormGroup.get('keyVals').value;
    const keyValMap: {[key: string]: string} = {};
    kvList.forEach((entry) => {
      keyValMap[entry.key] = entry.value;
    });
    this.propagateChange(keyValMap);
  }
}
