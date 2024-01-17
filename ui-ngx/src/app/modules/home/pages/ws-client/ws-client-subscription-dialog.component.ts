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
import { FormBuilder, UntypedFormGroup } from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import { MqttQoS, MqttQoSType, mqttQoSTypes } from '@shared/models/session.model';
import { TranslateService } from '@ngx-translate/core';
import { WsClientService } from '@core/http/ws-client.service';
import { DialogComponent } from '@shared/components/dialog.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Connection, rhOptions } from '@shared/models/ws-client.model';
import { randomColor } from '@core/utils';

export interface AddWsClientSubscriptionDialogData {
  subscription: any;
}

@Component({
  selector: 'tb-ws-client-subscription',
  templateUrl: './ws-client-subscription-dialog.component.html',
  styleUrls: ['./ws-client-subscription-dialog.component.scss']
})
export class WsClientSubscriptionDialogComponent extends DialogComponent<WsClientSubscriptionDialogComponent>
  implements OnInit, OnDestroy, AfterContentChecked {

  formGroup: UntypedFormGroup;
  entity: any;

  subscriptions = [];
  subscription;

  rhOptions = rhOptions;
  mqttQoSTypes = mqttQoSTypes;

  private valueChangeSubscription: Subscription = null;
  private destroy$ = new Subject<void>();

  constructor(public fb: FormBuilder,
              public cd: ChangeDetectorRef,
              protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: any,
              public dialogRef: MatDialogRef<WsClientSubscriptionDialogComponent>,
              private wsClientService: WsClientService,
              private translate: TranslateService) {
    super(store, router, dialogRef);
  }

  ngOnInit(): void {
    this.entity = this.data.subscription;
    this.buildForms(this.entity);
  }

  ngAfterContentChecked(): void {
    this.cd.detectChanges();
  }

  private buildForms(entity: Connection): void {
    this.buildSessionForm();
    if (entity) {
      this.updateFormsValues(entity);
    }
  }

  private buildSessionForm(): void {
    this.formGroup = this.fb.group({
      topic: ['testtopic', []],
      qos: [MqttQoS.AT_LEAST_ONCE, []],
      nl: [null, []],
      rap: [null, []],
      rh: [null, []],
      subscriptionIdentifier: [null, []],
      color: [randomColor(), []]
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateFormsValues(entity: any): void {
    this.formGroup.patchValue({topic: entity.topic});
  }

  mqttQoSValue(mqttQoSValue: MqttQoSType): string {
    const index = mqttQoSTypes.findIndex(object => {
      return object.value === mqttQoSValue.value;
    });
    const name = this.translate.instant(mqttQoSValue.name);
    return index + ' - ' + name;
  }

  onSave() {

  }
}

