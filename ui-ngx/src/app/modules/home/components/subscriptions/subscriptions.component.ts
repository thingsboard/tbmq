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

import { Component, forwardRef, Injector, Input, OnInit } from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor,
  FormArray,
  FormBuilder,
  FormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
  Validators
} from '@angular/forms';
import { MqttQoS, mqttQoSTypes, TopicSubscription } from '@shared/models/mqtt.models';
import { PageComponent } from '@shared/components/page.component';
import { Subscription } from 'rxjs';
import { AppState } from '@core/core.state';
import { Store } from '@ngrx/store';

@Component({
  selector: 'tb-subscriptions',
  templateUrl: './subscriptions.component.html',
  styleUrls: ['./subscriptions.component.scss'],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => SubscriptionsComponent),
    multi: true
  },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => SubscriptionsComponent),
      multi: true
    }]
})
export class SubscriptionsComponent extends PageComponent implements ControlValueAccessor, Validator, OnInit {

  @Input() disabled: boolean;

  topicListFormGroup: FormGroup;
  mqttQoSTypes = mqttQoSTypes;

  private propagateChange = null;

  private valueChangeSubscription: Subscription = null;

  constructor(protected store: Store<AppState>,
              private injector: Injector,
              private fb: FormBuilder) {
    super(store);
  }

  ngOnInit(): void {
    this.topicListFormGroup = this.fb.group({});
    this.topicListFormGroup.addControl('subscriptions',
      this.fb.array([]));
  }

  subscriptionsFormArray(): FormArray {
    return this.topicListFormGroup.get('subscriptions') as FormArray;
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState?(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.topicListFormGroup.disable({emitEvent: false});
    } else {
      this.topicListFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(topics: TopicSubscription[]): void {
    if (this.valueChangeSubscription) {
      this.valueChangeSubscription.unsubscribe();
    }
    const subscriptionsControls: Array<AbstractControl> = [];
    if (topics) {
      for (const topic of topics) {
        const topicControl = this.fb.group(topic);
        if (this.disabled) {
          topicControl.disable();
        }
        subscriptionsControls.push(topicControl);
      }
    }
    this.topicListFormGroup.setControl('subscriptions', this.fb.array(subscriptionsControls));
    this.valueChangeSubscription = this.topicListFormGroup.valueChanges.subscribe((value) => {
      this.updateView(value);
    });
  }

  public removeTopic(index: number) {
    (this.topicListFormGroup.get('subscriptions') as FormArray).removeAt(index);
  }

  public addTopic() {
    const subscriptionsFormArray = this.topicListFormGroup.get('subscriptions') as FormArray;
    subscriptionsFormArray.push(this.fb.group({
      topic: [null, [Validators.required]],
      qos: [MqttQoS.AT_LEAST_ONCE, [Validators.required]]
    }));
  }

  validate(control: AbstractControl): ValidationErrors | null {
    return control.value.length && this.topicListFormGroup.valid ? null : {
      topicFilters: {valid: false}
    };
  }

  private updateView(value: TopicSubscription[]) {
    this.propagateChange(this.topicListFormGroup.get('subscriptions').value);
  }

}
