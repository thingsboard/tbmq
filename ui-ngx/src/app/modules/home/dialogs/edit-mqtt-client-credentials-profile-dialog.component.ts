///
/// Copyright © 2016-2020 The Thingsboard Authors
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

import { Component, Inject, OnInit, SkipSelf } from '@angular/core';
import { ErrorStateMatcher } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { FormBuilder, FormControl, FormGroup, FormGroupDirective, NgForm, Validators } from '@angular/forms';
import { DialogComponent } from '@shared/components/dialog.component';
import { Router } from '@angular/router';
import {
  MqttCredentials,
  MqttCredentialsType,
  ClientType,
  credentialsTypeNames
} from '@shared/models/mqtt.models';
import { TranslateService } from '@ngx-translate/core';
import { MqttClientCredentialsService } from '@core/http/mqtt-client-credentials.service';

export interface EditMqttClientCredentialsDialogData {
  mqttClientCredentials: MqttCredentials
}

@Component({
  selector: 'tb-edit-mqtt-client-credentials-profile-dialog',
  templateUrl: './edit-mqtt-client-credentials-profile-dialog.component.html',
  providers: [{provide: ErrorStateMatcher, useExisting: EditMqttClientCredentialsProfileDialogComponent}],
  styleUrls: []
})
export class EditMqttClientCredentialsProfileDialogComponent extends
  DialogComponent<EditMqttClientCredentialsProfileDialogComponent, boolean> implements OnInit, ErrorStateMatcher {

  editMqttClientCredentialsProfileTitle: string;
  editMqttClientCredentialsProfileText: string;
  submitted = false;

  editMqttClientCredentialsProfileFormGroup: FormGroup;

  mqttClientCredentials: MqttCredentials;
  mqttClientCredentialsTypes = Object.values(ClientType);
  mqttClientCredentialsTypeTranslationMap = credentialsTypeNames;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: EditMqttClientCredentialsDialogData,
              private mqttClientCredentialsService: MqttClientCredentialsService,
              private translate: TranslateService,
              @SkipSelf() private errorStateMatcher: ErrorStateMatcher,
              public dialogRef: MatDialogRef<EditMqttClientCredentialsProfileDialogComponent, boolean>,
              public fb: FormBuilder) {
    super(store, router, dialogRef);
    this.mqttClientCredentials = this.data.mqttClientCredentials;
  }

  ngOnInit(): void {
    this.editMqttClientCredentialsProfileFormGroup = this.fb.group({
      credentialsType: [this.mqttClientCredentials.credentialsType, [Validators.required]]
    });
    this.editMqttClientCredentialsProfileTitle = 'mqtt-client-credentials.edit-profile-title';
    this.editMqttClientCredentialsProfileText = this.translate.instant('mqtt-client-credentials.edit-profile-text', { mqttClientName: this.mqttClientCredentials.name });
  }

  isErrorState(control: FormControl | null, form: FormGroupDirective | NgForm | null): boolean {
    const originalErrorState = this.errorStateMatcher.isErrorState(control, form);
    const customErrorState = !!(control && control.invalid && this.submitted);
    return originalErrorState || customErrorState;
  }

  cancel(): void {
    this.dialogRef.close(false);
  }

  save(): void {
    this.submitted = true;
    const clientCredentialsType: MqttCredentialsType = this.editMqttClientCredentialsProfileFormGroup.get('credentialsType').value;
    this.mqttClientCredentialsService.saveMqttClientCredentials({...this.data.mqttClientCredentials, credentialsType: clientCredentialsType })
      .subscribe(
        () => {
          this.dialogRef.close(true);
        }
      );
  }

}
