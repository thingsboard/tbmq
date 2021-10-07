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

import { Component, forwardRef, Input, OnInit } from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor, FormBuilder, FormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator, Validators
} from '@angular/forms';

@Component({
  selector: 'tb-connection',
  templateUrl: './connection.component.html',
  styleUrls: ['./connection.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => ConnectionComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => ConnectionComponent),
      multi: true
    }
  ]
})
export class ConnectionComponent implements OnInit, ControlValueAccessor, Validator {

  @Input()
  parentFormGroup: FormGroup;

  connectionForm: FormGroup;

  constructor(private fb: FormBuilder) { }

  ngOnInit(): void {
    this.connectionForm = this.fb.group({
      nodeId: [this.parentFormGroup ? this.parentFormGroup.value.connection?.nodeId : '', []],
      clientId: [this.parentFormGroup ? this.parentFormGroup.value.connection?.clientId : '', []],
      username: [this.parentFormGroup ? this.parentFormGroup.value.connection?.username : '', []],
      note: [this.parentFormGroup ? this.parentFormGroup.value.connection?.note : '', []],
      keepAliveSeconds: [this.parentFormGroup ? this.parentFormGroup.value.connection?.keepAliveSeconds : '', []],
      connectedAt: [this.parentFormGroup ? this.parentFormGroup.value.connection?.connectedAt : '', []],
      connectionState: [this.parentFormGroup ? this.parentFormGroup.value.connection?.connectionState : '', []]
    });
  }

  onTouched: () => void = () => {};

  registerOnChange(fn: any): void {
    console.log("on change");
    this.connectionForm.valueChanges.subscribe(fn);
  }

  registerOnTouched(fn: any): void {
    console.log("on blur");
    this.onTouched = fn;
  }

  writeValue(obj: any): void {
  }

  setDisabledState?(isDisabled: boolean): void {
    isDisabled ? this.connectionForm.disable() : this.connectionForm.enable();
  }

  validate(control: AbstractControl): ValidationErrors | null {
    console.log("connectionForm validation", control);
    return this.connectionForm.valid ? null : { invalidForm: { valid: false, message: "connectionForm fields are invalid" } };
  }

}
