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

import { Component, forwardRef, Input, OnDestroy } from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor,
  UntypedFormArray,
  UntypedFormBuilder,
  UntypedFormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
  Validators, ReactiveFormsModule
} from '@angular/forms';
import { MqttTopicFilter } from '@shared/models/integration.models';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { TranslateModule } from '@ngx-translate/core';
import { MatFormField } from '@angular/material/form-field';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { DEFAULT_QOS } from '@shared/models/session.model';
import { QosSelectComponent } from '@shared/components/qos-select.component';

@Component({
  selector: 'tb-mqtt-topic-filters',
  templateUrl: './mqtt-topic-filters.component.html',
  styleUrls: ['./mqtt-topic-filters.component.scss'],
  imports: [
    ReactiveFormsModule,
    TranslateModule,
    MatFormField,
    MatIconButton,
    MatTooltip,
    MatIcon,
    MatButton,
    MatInput,
    QosSelectComponent
  ],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => MqttTopicFiltersComponent),
    multi: true
  },
  {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => MqttTopicFiltersComponent),
    multi: true,
  }]
})
export class MqttTopicFiltersComponent implements ControlValueAccessor, Validator, OnDestroy {

  mqttTopicFiltersForm: UntypedFormGroup;

  @Input()
  disabled: boolean;

  private destroy$ = new Subject<void>();
  private propagateChange = (v: any) => { };

  constructor(private fb: UntypedFormBuilder) {
    this.mqttTopicFiltersForm = this.fb.group({
      filters: this.fb.array([], Validators.required)
    });
    this.mqttTopicFiltersForm.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe((value) => {
      this.updateModel(value.filters);
    });
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  writeValue(value: MqttTopicFilter[]) {
    if (this.mqttFiltersFromArray.length === value?.length) {
      this.mqttTopicFiltersForm.get('filters').patchValue(value, {emitEvent: false});
    } else {
      const filtersControls: Array<AbstractControl> = [];
      if (value) {
        value.forEach((filter) => {
          filtersControls.push(this.fb.group({
            filter: [filter.filter, [Validators.required]],
            qos: [DEFAULT_QOS, [Validators.required]]
          }));
        });
      }
      this.mqttTopicFiltersForm.setControl('filters', this.fb.array(filtersControls), {emitEvent: false});
      if (this.disabled) {
        this.mqttTopicFiltersForm.disable({emitEvent: false});
      } else {
        this.mqttTopicFiltersForm.enable({emitEvent: false});
      }
    }
  }

  registerOnChange(fn: any) {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any) { }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.mqttTopicFiltersForm.disable({emitEvent: false});
    } else {
      this.mqttTopicFiltersForm.enable({emitEvent: false});
    }
  }

  get mqttFiltersFromArray(): UntypedFormArray {
    return this.mqttTopicFiltersForm.get('filters') as UntypedFormArray;
  }

  addTopicFilter() {
    this.mqttFiltersFromArray.push(this.fb.group({
      filter: ['', [Validators.required]],
      qos: [DEFAULT_QOS, [Validators.required]]
    }));
  }

  private updateModel(value: MqttTopicFilter[]) {
    this.propagateChange(value);
  }

  validate(): ValidationErrors | null {
    return this.mqttTopicFiltersForm.valid ? null : {
      mqttTopicFilters: {valid: false}
    };
  }

}
