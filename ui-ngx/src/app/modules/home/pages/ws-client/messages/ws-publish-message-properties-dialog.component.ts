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

import { AfterContentChecked, ChangeDetectorRef, Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { AbstractControl, FormBuilder, UntypedFormGroup, ValidatorFn, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { DialogComponent } from '@shared/components/dialog.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogClose, MatDialogContent, MatDialogActions } from '@angular/material/dialog';
import {
  PublishMessageProperties,
  TimeUnitTypeTranslationMap,
  WebSocketConnection,
  AboveSecWebSocketTimeUnit,
  isDefinedProps
} from '@shared/models/ws-client.model';
import { MqttJsClientService } from '@core/http/mqtt-js-client.service';
import { MatToolbar } from '@angular/material/toolbar';
import { FlexModule } from '@angular/flex-layout/flex';
import { TranslateModule } from '@ngx-translate/core';
import { MatIconButton, MatButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { AsyncPipe } from '@angular/common';
import { MatProgressBar } from '@angular/material/progress-bar';
import { CdkScrollable } from '@angular/cdk/scrolling';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatFormField, MatLabel, MatSuffix, MatError } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatSelect } from '@angular/material/select';
import { MatOption } from '@angular/material/core';
import { UserPropertiesComponent } from '../../../components/client-credentials-templates/user-properties.component';

export interface PropertiesDialogComponentData {
  props: PublishMessageProperties;
  connection: WebSocketConnection;
}

@Component({
    selector: 'tb-ws-client-properties',
    templateUrl: './ws-publish-message-properties-dialog.component.html',
    styleUrls: ['./ws-publish-message-properties-dialog.component.scss'],
    imports: [FormsModule, ReactiveFormsModule, MatToolbar, FlexModule, TranslateModule, MatIconButton, MatDialogClose, MatTooltip, MatIcon, MatProgressBar, CdkScrollable, MatDialogContent, MatSlideToggle, MatFormField, MatLabel, MatInput, MatSuffix, MatSelect, MatOption, MatError, UserPropertiesComponent, MatDialogActions, MatButton, AsyncPipe]
})
export class WsPublishMessagePropertiesDialogComponent extends DialogComponent<WsPublishMessagePropertiesDialogComponent> implements OnInit, OnDestroy, AfterContentChecked {

  formGroup: UntypedFormGroup;
  props: PublishMessageProperties;
  connection: WebSocketConnection;

  timeUnitTypes = Object.keys(AboveSecWebSocketTimeUnit);
  timeUnitTypeTranslationMap = TimeUnitTypeTranslationMap;
  resetForm = false;

  private destroy$ = new Subject<void>();

  constructor(public fb: FormBuilder,
              public cd: ChangeDetectorRef,
              protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: any,
              public dialogRef: MatDialogRef<null>,
              private mqttJsClientService: MqttJsClientService) {
    super(store, router, dialogRef);
  }

  ngOnInit(): void {
    this.props = this.data.props;
    this.connection = this.data.connection;
    this.buildForms();

    this.mqttJsClientService.connection$.subscribe(
      connection => {
        if (connection) {
          this.connection = connection;
        }
      }
    );
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
      messageExpiryIntervalUnit: [this.props ? this.props.messageExpiryIntervalUnit : AboveSecWebSocketTimeUnit.SECONDS, []],
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

  save() {
    const properties = this.formGroup.getRawValue();
    properties.changed = isDefinedProps(properties);
    this.dialogRef.close(properties);
  }

  reset() {
    this.formGroup.reset({
      payloadFormatIndicator: null,
      contentType: null,
      messageExpiryInterval: null,
      messageExpiryIntervalUnit: AboveSecWebSocketTimeUnit.SECONDS,
      topicAlias: null,
      correlationData: null,
      responseTopic: null,
      userProperties: null
    });
    this.resetForm = true;
  }

  calcMax(unitControl: string) {
    const messageExpiryInterval = this.formGroup.get(unitControl)?.value;
    switch (messageExpiryInterval) {
      case AboveSecWebSocketTimeUnit.SECONDS:
        return 4294967295;
      case AboveSecWebSocketTimeUnit.MINUTES:
        return 71582788;
      case AboveSecWebSocketTimeUnit.HOURS:
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
}

