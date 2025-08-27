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

import { Component, forwardRef, OnInit, OnDestroy, model } from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor,
  UntypedFormArray,
  UntypedFormBuilder,
  UntypedFormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validators,
  FormsModule,
  ReactiveFormsModule,
  FormGroup
} from '@angular/forms';
import { DEFAULT_QOS, QoS } from '@shared/models/session.model';
import { TranslateModule } from '@ngx-translate/core';
import { TopicSubscription } from '@shared/models/ws-client.model';
import { MatLabel, MatFormField, MatSuffix } from '@angular/material/form-field';

import { MatInput } from '@angular/material/input';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { SubscriptionOptionsComponent } from './subscription-options.component';
import { MatIconButton, MatButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { QosSelectComponent } from '@shared/components/qos-select.component';
import { filterTopics, isString, topicFilterValidator } from '@core/utils';
import { MatAutocomplete, MatAutocompleteTrigger, MatOption } from '@angular/material/autocomplete';

@Component({
    selector: 'tb-session-subscriptions',
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
        }],
    imports: [TranslateModule, MatLabel, FormsModule, ReactiveFormsModule, MatFormField, MatInput, CopyButtonComponent, QosSelectComponent, MatSuffix, SubscriptionOptionsComponent, MatIconButton, MatTooltip, MatIcon, MatButton, MatAutocompleteTrigger, MatAutocomplete, MatOption]
})
export class SubscriptionsComponent implements ControlValueAccessor, OnInit, OnDestroy {

  disabled = model<boolean>();

  topicListFormGroup: UntypedFormGroup;
  filteredTopics = [];

  private propagateChange = (v: any) => {};
  private destroy$ = new Subject<void>();

  constructor(private fb: UntypedFormBuilder) {
  }

  ngOnInit(): void {
    this.topicListFormGroup = this.fb.group({});
    this.topicListFormGroup.addControl('subscriptions', this.fb.array([]));
    this.topicListFormGroup.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.updateModel());
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  subscriptionsFormArray(): UntypedFormArray {
    return this.topicListFormGroup.get('subscriptions') as UntypedFormArray;
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState?(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
    if (this.disabled()) {
      this.topicListFormGroup.disable({emitEvent: false});
    } else {
      this.topicListFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(topics: TopicSubscription[]): void {
    const subscriptionsControls: Array<AbstractControl> = [];
    if (topics?.length) {
      if (topics) {
        for (const topic of topics) {
          topic.qos = isString(topic.qos) ? QoS[topic.qos] : topic.qos;
          const topicControl = this.fb.group(topic);
          subscriptionsControls.push(topicControl);
        }
      }
    }
    this.topicListFormGroup.setControl('subscriptions', this.fb.array(subscriptionsControls));
    this.topicFiltersSubscribeValueChanges();
  }

  removeTopic(index: number) {
    (this.subscriptionsFormArray()).removeAt(index);
  }

  addTopic() {
    const group = this.fb.group({
      topicFilter: [null, [Validators.required, topicFilterValidator]],
      qos: [DEFAULT_QOS, []],
      subscriptionId: [null, []],
      options: this.fb.group({
        retainAsPublish: [false, []],
        retainHandling: [0, []],
        noLocal: [false, []],
      })
    });
    this.subscriptionsFormArray().push(group);
    this.subscribeTopicValueChanges(group);
  }

  validate(control: AbstractControl): ValidationErrors | null {
    return !this.topicListFormGroup.invalid ? null : {
      topicFilters: {valid: false}
    };
  }

  subscriptionOptionsChanged(value: TopicSubscription, topicFilter: AbstractControl<TopicSubscription>) {
    topicFilter.patchValue(value);
  }

  topicFiltersSubscribeValueChanges() {
    this.subscriptionsFormArray().controls.forEach(control => this.subscribeTopicValueChanges(control as FormGroup));
  }

  clearFilteredOptions() {
    setTimeout(() => {
      this.filteredTopics = null;
    }, 100);
  }

  private subscribeTopicValueChanges(control: FormGroup) {
    control.get('topicFilter').valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(value => this.filteredTopics = filterTopics(value));
  }

  private updateModel() {
    this.propagateChange(this.topicListFormGroup.getRawValue().subscriptions);
  }
}
