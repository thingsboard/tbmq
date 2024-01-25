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
import { Subject, Subscription } from 'rxjs';
import { WsMqttQoSType, WsQoSTypes, WsQoSTranslationMap } from '@shared/models/session.model';
import { TranslateService } from '@ngx-translate/core';
import { WsClientService } from '@core/http/ws-client.service';
import { DialogComponent } from '@shared/components/dialog.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Connection, rhOptions, WsSubscription } from '@shared/models/ws-client.model';
import { randomColor } from '@core/utils';

export interface AddWsClientSubscriptionDialogData {
  subscription?: WsSubscription;
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

  private destroy$ = new Subject<void>();

  constructor(public fb: FormBuilder,
              public cd: ChangeDetectorRef,
              protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: any,
              public dialogRef: MatDialogRef<SubscriptionDialogComponent>,
              private wsClientService: WsClientService,
              private translate: TranslateService) {
    super(store, router, dialogRef);
  }

  ngOnInit(): void {
    const subscription: WsSubscription = this.data?.subscription;
    if (subscription) {
      this.title = 'ws-client.subscriptions.edit-subscription';
      this.actionButtonLabel = 'action.save';
    }
    this.buildForms(subscription);
  }

  ngAfterContentChecked(): void {
    this.cd.detectChanges();
  }

  private buildForms(entity: WsSubscription): void {
    this.formGroup = this.fb.group({
      topic: [{value: entity ? entity.topic : 'sensors/#', disabled: !!entity}, [Validators.required]],
      color: [entity ? entity.color : randomColor(), []],
      options: this.fb.group({
        qos: [entity ? entity.options.qos : WsMqttQoSType.AT_LEAST_ONCE, []],
        nl: [entity ? entity.options.nl : null, []],
        rap: [entity ? entity.options.rap : null, []],
        rh: [entity ? entity.options.rh : null, []],
        properties: this.fb.group({
          subscriptionIdentifier: [entity ? entity.options.properties.subscriptionIdentifier : null, []]
        })
      })
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  save() {
    const value = this.formGroup.getRawValue();
    value.color = value.color || randomColor();
    this.dialogRef.close(value);
  }

}

