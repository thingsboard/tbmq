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
import { AbstractControl, FormBuilder, UntypedFormGroup, ValidatorFn } from '@angular/forms';
import { Subject } from 'rxjs';
import { DialogComponent } from '@shared/components/dialog.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {
  PublishMessageProperties,
  TimeUnitTypeTranslationMap,
  WebSocketConnection,
  WebSocketTimeUnit
} from '@shared/models/ws-client.model';

export interface PropertiesDialogComponentData {
  props: PublishMessageProperties;
  connection: WebSocketConnection;
}

@Component({
  selector: 'tb-ws-client-properties',
  templateUrl: './ws-publish-message-properties-dialog.component.html',
  styleUrls: ['./ws-publish-message-properties-dialog.component.scss']
})
export class WsPublishMessagePropertiesDialogComponent extends DialogComponent<WsPublishMessagePropertiesDialogComponent> implements OnInit, OnDestroy, AfterContentChecked {

  formGroup: UntypedFormGroup;
  props: PublishMessageProperties;
  connection: WebSocketConnection;

  timeUnitTypes = Object.keys(WebSocketTimeUnit);
  timeUnitTypeTranslationMap = TimeUnitTypeTranslationMap;

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
    this.props = this.data.props;
    this.connection = this.data.connection;
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
      payloadFormatIndicator: [this.props ? this.props.payloadFormatIndicator : null, []],
      contentType: [this.props ? this.props.contentType : null, []],
      messageExpiryInterval: [this.props ? this.props.messageExpiryInterval : null, []],
      messageExpiryIntervalUnit: [this.props ? this.props.messageExpiryIntervalUnit : WebSocketTimeUnit.SECONDS, []],
      topicAlias: [{value: this.props ? this.props.topicAlias : null, disabled: this.connection.configuration?.topicAliasMax === 0}, [this.topicAliasMaxValidator()]],
      correlationData: [this.props ? this.props.correlationData : null, []],
      responseTopic: [this.props ? this.props.responseTopic : null, []],
      userProperties: [this.props ? this.props.userProperties : null, []]
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSave() {
    const properties = this.formGroup.getRawValue();
    properties.changed = this.countNonNull(properties) > 1;
    this.dialogRef.close(properties);
  }

  calcMax(unitControl: string) {
    const messageExpiryInterval = this.formGroup.get(unitControl)?.value;
    switch (messageExpiryInterval) {
      case WebSocketTimeUnit.MILLISECONDS:
        return 4294967295000;
      case WebSocketTimeUnit.SECONDS:
        return 4294967295;
      case WebSocketTimeUnit.MINUTES:
        return 71582788;
      case WebSocketTimeUnit.HOURS:
        return 1193046;
    }
  }

  private topicAliasMaxValidator(): ValidatorFn {
    return (control: AbstractControl): {[key: string]: boolean} | null => {
      if (control.value !== undefined && control.value > this.connection.configuration?.topicAliasMax) {
        return { 'topicAliasMaxError': true };
      }
      return null;
    };
  }

  private countNonNull(obj: any): number {
    let count = 0;
    for (let key in obj) {
      if (obj[key] !== null && obj[key] !== '') {
        count++;
      }
    }
    return count;
  }
}

