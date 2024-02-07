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
import { Subject } from 'rxjs';
import { DialogComponent } from '@shared/components/dialog.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { PublishMessageProperties, timeUnitTypeTranslationMap, WebSocketTimeUnit } from '@shared/models/ws-client.model';

export interface PropertiesDialogComponentData {
  mqttVersion?: number;
  entity?: PublishMessageProperties;
}

@Component({
  selector: 'tb-ws-client-properties',
  templateUrl: './ws-publish-message-properties-dialog.component.html',
  styleUrls: ['./ws-publish-message-properties-dialog.component.scss']
})
export class WsPublishMessagePropertiesDialogComponent extends DialogComponent<WsPublishMessagePropertiesDialogComponent> implements OnInit, OnDestroy, AfterContentChecked {

  mqttVersion: number;
  formGroup: UntypedFormGroup;
  entity: PublishMessageProperties;

  timeUnitTypes = Object.keys(WebSocketTimeUnit);
  timeUnitTypeTranslationMap = timeUnitTypeTranslationMap;

  private destroy$ = new Subject<void>();

  constructor(public fb: FormBuilder,
              public cd: ChangeDetectorRef,
              protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: any,
              public dialogRef: MatDialogRef<null>) {
    super(store, router, dialogRef);
  }

  ngOnInit(): void {
    this.mqttVersion = this.data.mqttVersion;
    this.entity = this.data.entity;
    this.buildForms();
  }

  ngAfterContentChecked(): void {
    this.cd.detectChanges();
  }

  private buildForms(): void {
    this.buildForm();
  }

  private buildForm(): void {
    this.formGroup = this.fb.group({
      payloadFormatIndicator: [this.entity ? this.entity.payloadFormatIndicator : true, []],
      contentType: [this.entity ? this.entity.contentType : null, []],
      messageExpiryInterval: [this.entity ? this.entity.messageExpiryInterval : null, []],
      messageExpiryIntervalUnit: [this.entity ? this.entity.messageExpiryIntervalUnit : WebSocketTimeUnit.SECONDS, []],
      topicAlias: [this.entity ? this.entity.topicAlias : null, []],
      correlationData: [this.entity ? this.entity.correlationData : null, []],
      responseTopic: [this.entity ? this.entity.responseTopic : null, []],
      userProperties: [this.entity ? this.entity.userProperties : null, []]
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSave() {
    const properties = this.formGroup.getRawValue();
    this.dialogRef.close(properties);
  }
}

