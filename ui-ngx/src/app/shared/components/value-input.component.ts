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

import { Component, forwardRef, Input, OnInit, ViewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, NgForm } from '@angular/forms';
import { ValueType, valueTypesMap } from '@shared/models/constants';
import { isValidObjectString } from '@core/utils';
import { MatDialog } from '@angular/material/dialog';
import {
  JsonObjectEditDialogComponent,
  JsonObjectEditDialogData
} from '@shared/components/dialog/json-object-edit-dialog.component';

@Component({
  selector: 'tb-value-input',
  templateUrl: './value-input.component.html',
  styleUrls: ['./value-input.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => ValueInputComponent),
      multi: true
    }
  ]
})
export class ValueInputComponent implements OnInit, ControlValueAccessor {

  @Input() disabled: boolean;

  @Input() required: boolean;

  @Input() flexCorrelation: number[] = [40, 60];

  @Input() requiredText: string;

  @ViewChild('inputForm', {static: true}) inputForm: NgForm;

  modelValue: any;

  valueType: ValueType;

  public valueTypeEnum = ValueType;

  valueTypeKeys = Object.keys(ValueType);

  valueTypes = valueTypesMap;

  private propagateChange = null;

  constructor(
    public dialog: MatDialog,
  ) {

  }

  ngOnInit(): void {
  }

  openEditJSONDialog($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<JsonObjectEditDialogComponent, JsonObjectEditDialogData, object>(JsonObjectEditDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        jsonValue: this.modelValue,
        required: true
      }
    }).afterClosed().subscribe(
      (res) => {
        if (res) {
          this.modelValue = res;
          this.inputForm.control.patchValue({value: this.modelValue});
          this.updateView();
        }
      }
    );
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  writeValue(value: any): void {
    if (isValidObjectString(value)) {
      this.modelValue = JSON.parse(value);
      this.valueType = ValueType.JSON;
    } else {
      this.modelValue = value;
      this.valueType = ValueType.STRING;
    }
  }

  updateView() {
    if (this.inputForm.valid || (this.valueType === ValueType.JSON && Array.isArray(this.modelValue))) {
      this.propagateChange(this.modelValue);
    } else {
      this.propagateChange(null);
    }
  }

  onValueTypeChanged() {
    if (this.valueType === ValueType.JSON) {
      this.modelValue = {};
      this.inputForm.form.get('value').patchValue({});
    } else {
      this.modelValue = null;
    }
    this.updateView();
  }

  onValueChanged() {
    this.updateView();
  }

}
