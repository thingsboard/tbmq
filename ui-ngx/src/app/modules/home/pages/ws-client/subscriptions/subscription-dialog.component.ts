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

import { AfterContentChecked, ChangeDetectorRef, Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Subject } from 'rxjs';
import { WsMqttQoSType, WsQoSTypes, WsQoSTranslationMap } from '@shared/models/session.model';
import { DialogComponent } from '@shared/components/dialog.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { colorRandom, defaultSubscriptionTopicFilter, RhOptions, WebSocketSubscription } from '@shared/models/ws-client.model';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { TranslateService } from '@ngx-translate/core';

export interface AddWsClientSubscriptionDialogData {
  mqttVersion: number;
  subscription?: WebSocketSubscription;
  subscriptions?: WebSocketSubscription[];
}

@Component({
  selector: 'tb-subscription-dialog',
  templateUrl: './subscription-dialog.component.html',
  styleUrls: ['./subscription-dialog.component.scss']
})
export class SubscriptionDialogComponent extends DialogComponent<SubscriptionDialogComponent>
  implements OnInit, OnDestroy, AfterContentChecked {

  formGroup: UntypedFormGroup;

  rhOptions = RhOptions;
  qoSTypes = WsQoSTypes;
  qoSTranslationMap = WsQoSTranslationMap;

  title = 'subscription.add-subscription';
  actionButtonLabel = 'action.add';
  entity: WebSocketSubscription;
  topicFilterDuplicate: boolean;

  private destroy$ = new Subject<void>();

  constructor(public fb: FormBuilder,
              public cd: ChangeDetectorRef,
              protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: any,
              public dialogRef: MatDialogRef<SubscriptionDialogComponent>,
              private translate: TranslateService) {
    super(store, router, dialogRef);
  }

  ngOnInit(): void {
    this.entity = this.data?.subscription;
    if (this.entity) {
      this.title = 'subscription.edit-subscription';
      this.actionButtonLabel = 'action.save';
    }
    this.buildForm();
    const connectionTopicFilterList: string[] = this.data?.subscriptions
      .filter(el => !(this.entity?.id === el.id))
      .map(el => el.configuration.topicFilter);
    this.topicFilterDuplicate = connectionTopicFilterList.includes(this.formGroup.get('topicFilter').value);
    this.formGroup.get('topicFilter').valueChanges.subscribe(topicFilter => {
      this.topicFilterDuplicate = connectionTopicFilterList.includes(topicFilter);
    });
  }

  ngAfterContentChecked(): void {
    this.cd.detectChanges();
  }

  private buildForm(): void {
    const disabled = this.data.mqttVersion !== 5;
    this.formGroup = this.fb.group({
      topicFilter: [this.entity ? this.entity.configuration.topicFilter : defaultSubscriptionTopicFilter, [Validators.required]],
      qos: [this.entity ? this.entity.configuration.qos : WsMqttQoSType.AT_LEAST_ONCE, []],
      color: [this.entity ? this.entity.configuration.color : colorRandom(), []],
      options: this.fb.group({
        noLocal: [{value: this.entity ? this.entity.configuration.options.noLocal : null, disabled}, []],
        retainAsPublish: [{value: this.entity ? this.entity.configuration.options.retainAsPublish : null, disabled}, []],
        retainHandling: [{value: this.entity ? this.entity.configuration.options.retainHandling : 0, disabled}, []],
        subscriptionId: [{value: this.entity ? this.entity.configuration.subscriptionId : null, disabled}, []]
      })
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  save() {
    const formValues = this.formGroup.getRawValue();
    formValues.color = formValues.color || colorRandom();
    formValues.subscriptionId = formValues.options.subscriptionId;
    delete formValues.options.subscriptionId;
    const result: WebSocketSubscription = {...this.entity, ...{ configuration: formValues } };
    if (!this.topicFilterDuplicate) {
      this.dialogRef.close(result);
    } else {
      this.store.dispatch(new ActionNotificationShow(
        {
          message: this.translate.instant('subscription.topic-filter-duplicate'),
          type: 'error',
          duration: 2000,
          verticalPosition: 'top',
          horizontalPosition: 'left'
        })
      );
    }
  }
}

