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

import { AfterContentChecked, ChangeDetectorRef, Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Subject } from 'rxjs';
import { WsMqttQoSType, WsQoSTypes, WsQoSTranslationMap } from '@shared/models/session.model';
import { DialogComponent } from '@shared/components/dialog.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { rhOptions, WebSocketSubscription } from '@shared/models/ws-client.model';
import { randomColor } from '@core/utils';

export interface AddWsClientSubscriptionDialogData {
  mqttVersion: number;
  subscription?: WebSocketSubscription;
}

@Component({
  selector: 'tb-subscription-dialog',
  templateUrl: './subscription-dialog.component.html',
  styleUrls: ['./subscription-dialog.component.scss']
})
export class SubscriptionDialogComponent extends DialogComponent<SubscriptionDialogComponent>
  implements OnInit, OnDestroy, AfterContentChecked {

  formGroup: UntypedFormGroup;
  rhOptions = rhOptions;

  qoSTypes = WsQoSTypes;
  qoSTranslationMap = WsQoSTranslationMap;

  title = 'ws-client.subscriptions.add-subscription';
  actionButtonLabel = 'action.add';

  entity: WebSocketSubscription;
  mqttVersion: number;

  private destroy$ = new Subject<void>();

  constructor(public fb: FormBuilder,
              public cd: ChangeDetectorRef,
              protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: any,
              public dialogRef: MatDialogRef<SubscriptionDialogComponent>) {
    super(store, router, dialogRef);
  }

  ngOnInit(): void {
    this.entity = this.data?.subscription;
    this.mqttVersion = this.data.mqttVersion;
    if (this.entity) {
      this.title = 'ws-client.subscriptions.edit-subscription';
      this.actionButtonLabel = 'action.save';
    }
    this.buildForms();
  }

  ngAfterContentChecked(): void {
    this.cd.detectChanges();
  }

  private buildForms(): void {
    this.formGroup = this.fb.group({
      topicFilter: [this.entity ? this.entity.configuration.topicFilter : 'sensors/#', [Validators.required]],
      qos: [this.entity ? this.entity.configuration.qos : WsMqttQoSType.AT_LEAST_ONCE, []],
      color: [this.entity ? this.entity.configuration.color : randomColor(), []],
      options: this.fb.group({
        noLocal: [{value: this.entity ? this.entity.configuration.options.noLocal : null, disabled: this.disableMqtt5Features()}, []],
        retainAsPublish: [{value: this.entity ? this.entity.configuration.options.retainAsPublish : null, disabled: this.disableMqtt5Features()}, []],
        retainHandling: [{value: this.entity ? this.entity.configuration.options.retainHandling : null, disabled: this.disableMqtt5Features()}, []]
      })
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  save() {
    const formValues = this.formGroup.getRawValue();
    formValues.color = formValues.color || randomColor();
    const result: WebSocketSubscription = {...this.entity, ...{ configuration: formValues } };
    this.dialogRef.close(result);
  }

  disableMqtt5Features() {
    return this.mqttVersion !== 5;
  }
}

