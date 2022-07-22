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

import { Component, forwardRef, Input, OnDestroy } from '@angular/core';
import {
  ControlValueAccessor,
  FormBuilder,
  FormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors, Validator,
  ValidatorFn,
  Validators
} from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { isDefinedAndNotNull, isEmptyStr } from '@core/utils';
import { MqttClientCredentials } from '@shared/models/mqtt-client-crenetials.model';
import { MatDialog } from '@angular/material/dialog';
import {
  ChangeMqttBasicPasswordDialogComponent,
  ChangeMqttBasicPasswordDialogData
} from '@home/pages/mqtt-client-credentials/change-mqtt-basic-password-dialog.component';
import { Observable } from 'rxjs/internal/Observable';

@Component({
  selector: 'tb-mqtt-credentials-basic',
  templateUrl: './basic.component.html',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => MqttCredentialsBasicComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => MqttCredentialsBasicComponent),
      multi: true,
    }],
  styleUrls: []
})
export class MqttCredentialsBasicComponent implements ControlValueAccessor, Validator, OnDestroy {

  @Input()
  disabled: boolean;

  @Input()
  entity: MqttClientCredentials;

  @Input()
  isEdit: boolean;

  credentialsMqttFormGroup: FormGroup;

  private destroy$ = new Subject();
  private propagateChange = (v: any) => {};

  constructor(public fb: FormBuilder,
              private dialog: MatDialog) {
    this.credentialsMqttFormGroup = this.fb.group({
      clientId: [null],
      userName: [null],
      password: [null]
    }, {validators: this.atLeastOne(Validators.required, ['clientId', 'userName'])});
    this.credentialsMqttFormGroup.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe((value) => {
      this.updateView(value);
    });
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
      this.credentialsMqttFormGroup.disable({emitEvent: false});
    } else {
      this.credentialsMqttFormGroup.enable({emitEvent: false});
    }
  }

  validate(): ValidationErrors | null {
    return this.credentialsMqttFormGroup.valid ? null : {
      deviceCredentialsMqttBasic: false
    };
  }

  writeValue(mqttBasic: string) {
    if (isDefinedAndNotNull(mqttBasic) && !isEmptyStr(mqttBasic)) {
      const value = JSON.parse(mqttBasic);
      this.credentialsMqttFormGroup.patchValue(value, {emitEvent: false});
    }
  }

  updateView(value: any) {
    const formValue = JSON.stringify(value);
    this.propagateChange(formValue);
  }

  passwordChanged() {
    const value = this.credentialsMqttFormGroup.get('password').value;
    if (value !== '') {
      this.credentialsMqttFormGroup.get('userName').setValidators([Validators.required]);
    } else {
      this.credentialsMqttFormGroup.get('userName').setValidators([]);
    }
    this.credentialsMqttFormGroup.get('userName').updateValueAndValidity({emitEvent: false});
  }

  changePassword(): Observable<boolean> {
    return this.dialog.open<ChangeMqttBasicPasswordDialogComponent, ChangeMqttBasicPasswordDialogData,
      boolean>(ChangeMqttBasicPasswordDialogComponent, {
        disableClose: true,
        panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
        data: {
          clientId: this.credentialsMqttFormGroup.get('clientId').value
        }
      }).afterClosed();
  }

  private atLeastOne(validator: ValidatorFn, controls: string[] = null) {
    return (group: FormGroup): ValidationErrors | null => {
      if (!controls) {
        controls = Object.keys(group.controls);
      }
      const hasAtLeastOne = group?.controls && controls.some(k => !validator(group.controls[k]));
      return hasAtLeastOne ? null : {atLeastOne: true};
    };
  }

}
