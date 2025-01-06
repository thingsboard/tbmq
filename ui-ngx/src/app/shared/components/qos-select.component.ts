///
/// Copyright © 2016-2024 The Thingsboard Authors
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
  ControlValueAccessor,
  NG_VALUE_ACCESSOR,
  UntypedFormBuilder,
  UntypedFormControl,
  Validators
} from '@angular/forms';
import { MatFormFieldAppearance, SubscriptSizing } from '@angular/material/form-field';
import { coerceBoolean } from '@shared/decorators/coercion';
import { TranslateService } from '@ngx-translate/core';
import { DEFAULT_QOS, QoS, QosTranslation } from '../models/session.model';
import { isDefinedAndNotNull, isNumber } from '@core/utils';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

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
  ]
})
export class QosSelectComponent implements ControlValueAccessor, OnDestroy  {

  @Input()
  disabled: boolean;

  @Input()
  @coerceBoolean()
  required = false;

  @Input()
  @coerceBoolean()
  asString = false;

  @Input()
  @coerceBoolean()
  displayLabel = true;

  @Input()
  label: string = this.translate.instant('mqtt-client-session.qos');

  @Input()
  appearance: MatFormFieldAppearance = 'fill';

  @Input()
  subscriptSizing: SubscriptSizing = 'fixed';

  @Input()
  hideRequiredMarker = true;

  qosFormControl: UntypedFormControl;
  qosTypes = Object.values(QoS).filter(v => isNumber(v));
  qosTranslation = QosTranslation;

  private destroy$ = new Subject<void>();
  private propagateChange = (_val: any) => {};

  constructor(readonly fb: UntypedFormBuilder,
              readonly translate: TranslateService) {
    this.qosFormControl = this.fb.control(null, this.required ? [Validators.required] : []);
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
      this.qosFormControl.patchValue(value);
    } else {
      this.qosFormControl.patchValue(DEFAULT_QOS);
    }
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
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
