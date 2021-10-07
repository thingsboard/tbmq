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
import { EntityType } from '@shared/models/entity-type.models';
import { DialogComponent } from '@shared/components/dialog.component';
import { Router } from '@angular/router';
import {
  Client,
  ClientType,
  clientTypeTranslationMap
} from '@shared/models/mqtt.models';
import { MqttClientService } from '@core/http/mqtt-client.service';
import { TranslateService } from '@ngx-translate/core';

export interface EditMqttClientDialogData {
  mqttClient: Client
}

@Component({
  selector: 'tb-edit-mqtt-client-profile-dialog',
  templateUrl: './edit-mqtt-client-profile-dialog.component.html',
  providers: [{provide: ErrorStateMatcher, useExisting: EditMqttClientProfileDialogComponent}],
  styleUrls: []
})
export class EditMqttClientProfileDialogComponent extends
  DialogComponent<EditMqttClientProfileDialogComponent, boolean> implements OnInit, ErrorStateMatcher {

  editMqttClientProfileTitle: string;
  editMqttClientProfileText: string;
  submitted = false;

  editMqttClientProfileFormGroup: FormGroup;

  mqttClient: Client;
  mqttClientTypes = Object.values(ClientType);
  mqttClientTypeTranslationMap = clientTypeTranslationMap;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: EditMqttClientDialogData,
              private mqttClientService: MqttClientService,
              private translate: TranslateService,
              @SkipSelf() private errorStateMatcher: ErrorStateMatcher,
              public dialogRef: MatDialogRef<EditMqttClientProfileDialogComponent, boolean>,
              public fb: FormBuilder) {
    super(store, router, dialogRef);
    this.mqttClient = this.data.mqttClient;
  }

  ngOnInit(): void {
    this.editMqttClientProfileFormGroup = this.fb.group({
      clientType: [this.mqttClient.type, [Validators.required]]
    });
    this.editMqttClientProfileTitle = 'mqtt-client.edit-client-profile-title';
    this.editMqttClientProfileText = this.translate.instant('mqtt-client.edit-client-profile-text', { mqttClientId: this.mqttClient.clientId });
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
    const clientType: ClientType = this.editMqttClientProfileFormGroup.get('clientType').value;
    this.mqttClientService.saveMqttClient({...this.data.mqttClient, type: clientType })
      .subscribe(
        () => {
          this.dialogRef.close(true);
        }
      );
  }

}
