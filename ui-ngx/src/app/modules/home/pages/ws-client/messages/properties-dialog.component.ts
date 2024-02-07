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

import { AfterContentChecked, ChangeDetectorRef, Component, Inject, Input, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, UntypedFormGroup } from '@angular/forms';
import { Subject } from 'rxjs';
import { DialogComponent } from '@shared/components/dialog.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { WebSocketTimeUnit, timeUnitTypeTranslationMap } from '@shared/models/ws-client.model';
import { convertTimeUnits, isDefinedAndNotNull } from '@core/utils';

export interface PropertiesDialogComponentData {
  mqttVersion?: number;
}

@Component({
  selector: 'tb-ws-client-properties',
  templateUrl: './properties-dialog.component.html',
  styleUrls: ['./properties-dialog.component.scss']
})
export class PropertiesDialogComponent extends DialogComponent<PropertiesDialogComponent> implements OnInit, OnDestroy, AfterContentChecked {

  mqttVersion: number;
  formGroup: UntypedFormGroup;
  entity: any;

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
    this.mqttVersion = this.data.mqttVersion;
  }

  ngOnInit(): void {
    this.entity = this.data;
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
      payloadFormatIndicator: [false, []],
      contentType: [null, []],
      messageExpiryInterval: [null, []],
      messageExpiryIntervalUnit: [WebSocketTimeUnit.SECONDS, []],
      topicAlias: [null, []],
      subscriptionIdentifier: [null, []],
      correlationData: [null, []],
      responseTopic: [null, []],
      userProperties: [null, []]
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSave() {
    const properties = this.formGroup.getRawValue();
    if (isDefinedAndNotNull(properties.messageExpiryInterval)) {
      properties.messageExpiryInterval = convertTimeUnits(properties.messageExpiryInterval, properties.messageExpiryIntervalUnit, WebSocketTimeUnit.SECONDS);
    }
    delete properties.messageExpiryIntervalUnit;
    this.dialogRef.close(properties)
  }
}

