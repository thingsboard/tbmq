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

import { AfterViewInit, Component, forwardRef, Input, OnInit } from '@angular/core';
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
import { QoSTranslationMap, QosType } from '../models/session.model';
import { isDefinedAndNotNull } from '@core/utils';

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
export class QosSelectComponent implements ControlValueAccessor, OnInit, AfterViewInit {

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
  defaultQosType: string = QosType.AT_LEAST_ONCE;

  @Input()
  subscriptSizing: SubscriptSizing = 'fixed';

  @Input()
  hideRequiredMarker = true;

  qosFormControl: UntypedFormControl;
  qosType = Object.values(QosType);
  qosTranslationMap = QoSTranslationMap;

  private propagateChange = (_val: any) => {};

  constructor(private fb: UntypedFormBuilder,
              private translate: TranslateService) {
  }

  ngOnInit() {
    this.qosFormControl = this.fb.control(this.defaultQosType, this.required ? [Validators.required] : []);
    this.qosFormControl.valueChanges.subscribe(value => this.updateView(value));
  }

  ngAfterViewInit() {
    this.updateView(this.qosFormControl.value);
  }

  writeValue(value: number) {
    if (isDefinedAndNotNull(value)) {
      const qosValue = this.qos(value);
      this.qosFormControl.setValue(qosValue);
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

  updateView(value: QosType) {
    const qosValue = this.qosToNumber(value);
    this.propagateChange(qosValue);
  }

  private qosToNumber(value: QosType): number {
    return this.qosType.indexOf(value);
  }

  private qos(value: any): QosType {
    if (this.isQosType(value)) {
      return value;
    }
    return this.qosType.at(value);
  }

  private isQosType(value: any): boolean {
    return Object.values(QosType).includes(value);
  }
}
