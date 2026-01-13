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

import { Component, forwardRef, OnDestroy, input, model, booleanAttribute } from '@angular/core';
import {
  ControlValueAccessor, FormsModule,
  NG_VALUE_ACCESSOR, ReactiveFormsModule,
  UntypedFormBuilder,
  UntypedFormControl,
  Validators
} from '@angular/forms';
import {
  MatFormField,
  MatFormFieldAppearance,
  MatLabel,
  SubscriptSizing
} from '@angular/material/form-field';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DEFAULT_QOS, QoS, QosTranslation, QosTypes } from '../models/session.model';
import { isDefinedAndNotNull } from '@core/utils';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MatOption, MatSelect } from '@angular/material/select';

@Component({
    selector: 'tb-qos-select',
    templateUrl: './qos-select.component.html',
    styleUrl: './qos-select.component.scss',
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => QosSelectComponent),
            multi: true
        }
    ],
    imports: [TranslateModule, MatLabel, FormsModule, ReactiveFormsModule, MatFormField, MatSelect, MatOption]
})
export class QosSelectComponent implements ControlValueAccessor, OnDestroy  {

  disabled = model<boolean>();
  readonly required = input(false, {transform: booleanAttribute});
  readonly asString = input(false, {transform: booleanAttribute});
  readonly displayLabel = input(true, {transform: booleanAttribute});
  readonly label = input<string>(this.translate.instant('mqtt-client-session.qos'));
  readonly appearance = input<MatFormFieldAppearance>('outline');
  readonly subscriptSizing = input<SubscriptSizing>('fixed');
  readonly hideRequiredMarker = input(true);

  qosFormControl: UntypedFormControl;
  qosTypes = QosTypes;
  qosTranslation = QosTranslation;

  private destroy$ = new Subject<void>();
  private propagateChange = (_val: any) => {};

  constructor(readonly fb: UntypedFormBuilder,
              readonly translate: TranslateService) {
    this.qosFormControl = this.fb.control(null, this.required() ? [Validators.required] : []);
    this.qosFormControl.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(value => this.updateModel(value));
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  writeValue(value: QoS) {
    if (isDefinedAndNotNull(value)) {
      this.qosFormControl.patchValue(value, {emitEvent: false});
    } else {
      this.qosFormControl.patchValue(DEFAULT_QOS, {emitEvent: false});
    }
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
    if (this.disabled()) {
      this.qosFormControl.disable({emitEvent: false});
    } else {
      this.qosFormControl.enable({emitEvent: false});
    }
  }

  registerOnChange(fn: any) {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any) {}

  updateModel(value: QoS) {
    this.propagateChange(value);
  }
}
