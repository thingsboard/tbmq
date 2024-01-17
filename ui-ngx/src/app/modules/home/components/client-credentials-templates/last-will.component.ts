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
  UntypedFormGroup,
  ValidationErrors,
  Validator
} from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import { MqttQoS, MqttQoSType, mqttQoSTypes } from '@shared/models/session.model';
import { TranslateService } from '@ngx-translate/core';
import { Connection, TimeUnitType, timeUnitTypeTranslationMap } from '@shared/models/ws-client.model';
import { convertTimeUnits } from '@core/utils';

export interface LastWill {
  topic: string;
  qos: number;
}

@Component({
  selector: 'tb-last-will',
  templateUrl: './last-will.component.html',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => LastWillComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => LastWillComponent),
      multi: true,
    }],
  styleUrls: ['./last-will.component.scss']
})
export class LastWillComponent implements OnInit, ControlValueAccessor, Validator, OnDestroy {

  @Input()
  disabled: boolean;

  @Input()
  mqttVersion: number;

  @Input()
  entity: Connection;

  formGroup: UntypedFormGroup;
  mqttQoSTypes = mqttQoSTypes;
  timeUnitTypes = Object.keys(TimeUnitType);
  timeUnitTypeTranslationMap = timeUnitTypeTranslationMap;

  private valueChangeSubscription: Subscription = null;
  private destroy$ = new Subject<void>();
  private propagateChange = (v: any) => {
  };

  constructor(public fb: FormBuilder,
              public cd: ChangeDetectorRef,
              private translate: TranslateService) {
  }

  ngOnInit() {
    this.initForm(this.entity);
  }

  private initForm(entity: Connection) {
    this.formGroup = this.fb.group({
      topic: [entity?.will ? entity?.will.topic : null, []],
      payload: [entity?.will ? entity?.will.payload : null, []],
      qos: [entity?.will ? entity?.will.qos : MqttQoS.AT_LEAST_ONCE, []],
      retain: [entity?.will ? entity?.will.retain : false, []],
      properties: this.fb.group({
        willDelayInterval: [entity?.will ? convertTimeUnits(entity?.will.properties.willDelayInterval, TimeUnitType.SECONDS, entity?.will.properties.willDelayIntervalUnit) : null, []],
        willDelayIntervalUnit: [entity?.will ? entity?.will.properties.willDelayIntervalUnit : TimeUnitType.SECONDS, []],
        payloadFormatIndicator: [entity?.will ? entity?.will.properties.payloadFormatIndicator : null, []],
        messageExpiryInterval: [entity?.will ? convertTimeUnits(entity?.will.properties.messageExpiryInterval, TimeUnitType.SECONDS, entity?.will.properties.messageExpiryIntervalUnit) : null, []],
        messageExpiryIntervalUnit: [entity?.will ? entity?.will.properties.messageExpiryIntervalUnit : TimeUnitType.SECONDS, []],
        contentType: [entity?.will ? entity?.will.properties.contentType : null, []],
        responseTopic: [entity?.will ? entity?.will.properties.responseTopic : null, []],
        correlationData: [entity?.will ? entity?.will.properties.correlationData : null, []]
      })
    });
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
      this.formGroup.disable({emitEvent: false});
    } else {
      this.formGroup.enable({emitEvent: false});
    }
  }

  validate(): ValidationErrors | null {
    return this.formGroup.valid ? null : {lastWill: true};
  }

  writeValue(value: LastWill): void {
    if (this.valueChangeSubscription) {
      this.valueChangeSubscription.unsubscribe();
    }
    this.valueChangeSubscription = this.formGroup.valueChanges.subscribe((value) => {
      this.updateView(value);
    });
  }

  mqttQoSValue(mqttQoSValue: MqttQoSType): string {
    const index = mqttQoSTypes.findIndex(object => {
      return object.value === mqttQoSValue.value;
    });
    const name = this.translate.instant(mqttQoSValue.name);
    return index + ' - ' + name;
  }

  private updateView(value: LastWill) {
    this.propagateChange(value);
  }
}

