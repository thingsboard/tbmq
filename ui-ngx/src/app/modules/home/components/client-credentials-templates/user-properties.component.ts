///
/// Copyright © 2016-2023 The Thingsboard Authors
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

import { ChangeDetectorRef, Component, forwardRef, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import {
  ControlValueAccessor,
  FormBuilder,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  UntypedFormArray,
  UntypedFormGroup,
  ValidationErrors,
  Validator
} from '@angular/forms';
import { Subject } from 'rxjs';
import { isDefinedAndNotNull } from '@core/utils';
import { WebSocketConnection, WebSocketUserProperties } from '@shared/models/ws-client.model';

export interface UserProperties {
  userProperties: UserPropertiesObject[];
}

export interface UserPropertiesObject {
  key: string;
  value: string;
}

@Component({
  selector: 'tb-user-properties',
  templateUrl: './user-properties.component.html',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => UserPropertiesComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => UserPropertiesComponent),
      multi: true,
    }],
  styleUrls: ['./user-properties.component.scss']
})
export class UserPropertiesComponent implements ControlValueAccessor, Validator, OnDestroy, OnInit {

  @Input()
  disabled: boolean;

  @Input()
  mqttVersion: number;

  @Input()
  entity: WebSocketConnection;

  userPropertiesFormGroup: UntypedFormGroup;

  private destroy$ = new Subject<void>();
  private propagateChange = (v: any) => {
  };

  get userPropertiesFormArray(): UntypedFormArray {
    return this.userPropertiesFormGroup.get('userProperties') as UntypedFormArray;
  }

  constructor(public fb: FormBuilder,
              public cd: ChangeDetectorRef) {
  }

  ngOnInit() {
    this.userPropertiesFormGroup = this.fb.group({
      userProperties: this.fb.array([])
    });
    if (isDefinedAndNotNull(this.entity?.configuration?.userProperties?.props?.length)) {
      for (const prop in this.entity.configuration.userProperties.props) {
        this.userPropertiesFormArray.push(this.fb.group({
          key: [this.entity.configuration.userProperties.props[prop].k, []],
          value: [this.entity.configuration.userProperties.props[prop].v, []]
        }));
      }
    } else {
      this.userPropertiesFormArray.push(this.fb.group({
        key: [null, []],
        value: [null, []]
      }));
    }
  }

  addRule(): void {
    this.userPropertiesFormArray.push(this.fb.group({
      key: [null, []],
      value: [null, []]
    }));
    this.cd.markForCheck();
  }

  removeRule(index: number) {
    this.userPropertiesFormArray.removeAt(index);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean) {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.userPropertiesFormGroup.disable({emitEvent: false});
    } else {
      this.userPropertiesFormGroup.enable({emitEvent: false});
    }
  }

  validate(): ValidationErrors | null {
    return this.userPropertiesFormGroup.valid ? null : {userProperties: true};
  }

  writeValue(value: UserProperties): void {
    this.userPropertiesFormGroup.valueChanges.subscribe((value) => {
      this.updateView(value);
    });
  }

  private updateView(value: UserProperties) {
    if (isDefinedAndNotNull(value)) {
      this.propagateChange(this.prepareValues(value));
    }
  }

  private prepareValues(value: UserProperties): WebSocketUserProperties {
    const userProperties = {
      props: []
    };
    value.userProperties.map(obj => {
      const k = obj?.key;
      const v = obj?.value;
      if (k) {
        userProperties.props.push({k, v});
      }
    });
    return userProperties;
  }
}

