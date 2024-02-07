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

import { Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { DialogComponent } from '@shared/components/dialog.component';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { FormBuilder, UntypedFormGroup } from '@angular/forms';
import { PublishMessageProperties, timeUnitTypeTranslationMap, WebSocketTimeUnit } from '@shared/models/ws-client.model';

export interface WsMessagePropertiesDialogData {
  entity: PublishMessageProperties;
}

@Component({
  selector: 'tb-ws-message-properties-dialog',
  templateUrl: './ws-message-properties-dialog.component.html',
  styleUrls: ['./ws-message-properties-dialog.component.scss']
})
export class WsMessagePropertiesDialogComponent extends DialogComponent<WsMessagePropertiesDialogData> implements OnInit, OnDestroy {

  entity: PublishMessageProperties;
  formGroup: UntypedFormGroup;

  timeUnitTypes = Object.keys(WebSocketTimeUnit);
  timeUnitTypeTranslationMap = timeUnitTypeTranslationMap;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              public fb: FormBuilder,
              @Inject(MAT_DIALOG_DATA) public data: WsMessagePropertiesDialogData,
              public dialogRef: MatDialogRef<WsMessagePropertiesDialogComponent>,
              private translate: TranslateService) {
    super(store, router, dialogRef);
  }

  ngOnInit(): void {
    this.entity = this.data.entity;
    this.buildForm(this.entity);
  }

  ngOnDestroy(): void {
    super.ngOnDestroy();
  }

  private buildForm(entity: PublishMessageProperties): void {
    this.formGroup = this.fb.group({
      payloadFormatIndicator: [{value: entity.payloadFormatIndicator, disabled: true}, []],
      contentType: [{value: entity.contentType, disabled: true}, []],
      messageExpiryInterval: [{value: entity.messageExpiryInterval, disabled: true}, []],
      messageExpiryIntervalUnit: [{value: entity.messageExpiryIntervalUnit, disabled: true}, []],
      topicAlias: [{value: entity.topicAlias, disabled: true}, []],
      correlationData: [{value: entity.correlationData, disabled: true}, []],
      responseTopic: [{value: entity.responseTopic, disabled: true}, []],
      userProperties: [{value: entity.userProperties, disabled: true}, []]
    });
  }
}
