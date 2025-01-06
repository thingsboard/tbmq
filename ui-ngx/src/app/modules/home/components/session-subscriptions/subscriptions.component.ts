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

import { Component, forwardRef, Input, OnInit, OnDestroy } from '@angular/core';
import { AbstractControl, ControlValueAccessor, UntypedFormArray, UntypedFormBuilder, UntypedFormGroup, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { DEFAULT_QOS, QoS  } from '@shared/models/session.model';
import { TranslateModule } from '@ngx-translate/core';
import { TopicSubscription } from '@shared/models/ws-client.model';
import { FlexModule } from '@angular/flex-layout/flex';
import { MatLabel, MatFormField, MatSuffix, MatError } from '@angular/material/form-field';
import { NgFor, NgIf } from '@angular/common';
import { MatInput } from '@angular/material/input';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { ExtendedModule } from '@angular/flex-layout/extended';
import { SubscriptionOptionsComponent } from './subscription-options.component';
import { MatIconButton, MatButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { QosSelectComponent } from '@shared/components/qos-select.component';

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
  standalone: true,
  imports: [TranslateModule, FlexModule, MatLabel, NgFor, FormsModule, ReactiveFormsModule, MatFormField, MatInput, CopyButtonComponent, QosSelectComponent, MatSuffix, ExtendedModule, NgIf, MatError, SubscriptionOptionsComponent, MatIconButton, MatTooltip, MatIcon, MatButton]
})
export class SubscriptionsComponent implements ControlValueAccessor, OnInit, OnDestroy {

  @Input() disabled: boolean;

  topicListFormGroup: UntypedFormGroup;

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
    this.disabled = isDisabled;
    if (this.disabled) {
      this.topicListFormGroup.disable({emitEvent: false});
    } else {
      this.topicListFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(topics: TopicSubscription[]): void {
    const subscriptionsControls: Array<AbstractControl> = [];
    if (topics?.length) {
      if (topics) {
        for (let topic of topics) {
          topic.qos = QoS[topic.qos] as unknown as QoS;
          const topicControl = this.fb.group(topic);
          subscriptionsControls.push(topicControl);
        }
      }
    }
    this.topicListFormGroup.setControl('subscriptions', this.fb.array(subscriptionsControls));
  }

  removeTopic(index: number) {
    (this.subscriptionsFormArray()).removeAt(index);
  }

  addTopic() {
    const group = this.fb.group({
      topicFilter: [null, [Validators.required]],
      qos: [DEFAULT_QOS, []],
      subscriptionId: [null, []],
      options: this.fb.group({
        retainAsPublish: [false, []],
        retainHandling: [0, []],
        noLocal: [false, []],
      })
    });
    this.subscriptionsFormArray().push(group);
  }

  validate(control: AbstractControl): ValidationErrors | null {
    return !this.topicListFormGroup.invalid ? null : {
      topicFilters: {valid: false}
    };
  }

  subscriptionOptionsChanged(value: TopicSubscription, topicFilter: AbstractControl<TopicSubscription>) {
    topicFilter.patchValue(value);
  }

  private updateModel() {
    this.propagateChange(this.topicListFormGroup.getRawValue().subscriptions);
  }
}
