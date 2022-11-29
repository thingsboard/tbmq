///
/// Copyright © 2016-2022 The Thingsboard Authors
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
import { FormBuilder, FormControl, FormGroup, FormGroupDirective, NgForm } from '@angular/forms';
import { DialogComponent } from '@shared/components/dialog.component';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MqttClientCredentialsService } from '@core/http/mqtt-client-credentials.service';
import { ClientType } from '@shared/models/mqtt-client.model';
import { MqttClientCredentials, MqttCredentialsType } from '@shared/models/mqtt-client-crenetials.model';

export interface ManageCredentialsDialogData {
  mqttClientCredentials: MqttClientCredentials;
  isEdit: boolean;
}

@Component({
  selector: 'tb-manage-credentials-dialog',
  templateUrl: './manage-credentials-dialog.component.html',
  providers: [{provide: ErrorStateMatcher, useExisting: ManageCredentialsDialogComponent}],
  styleUrls: []
})
export class ManageCredentialsDialogComponent extends DialogComponent<ManageCredentialsDialogComponent, MqttClientCredentials> implements OnInit, ErrorStateMatcher {

  mqttCredentialsFormGroup: FormGroup;
  mqttCredentialsTypes = Object.values(ClientType);
  mqttCredentials: MqttClientCredentials;
  mqttCredentialsType: MqttCredentialsType;

  mqttClientCredentials = this.data.mqttClientCredentials;
  isEdit = this.data.isEdit || false;

  submitted = false;
  loadingCredentials = true;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: ManageCredentialsDialogData,
              private mqttClientCredentialsService: MqttClientCredentialsService,
              private translate: TranslateService,
              @SkipSelf() private errorStateMatcher: ErrorStateMatcher,
              public dialogRef: MatDialogRef<ManageCredentialsDialogComponent, MqttClientCredentials>,
              public fb: FormBuilder) {
    super(store, router, dialogRef);
  }

  ngOnInit(): void {
    this.mqttCredentialsFormGroup = this.fb.group({
      credential: [null]
    });
    this.loadMqttCredentials();
  }

  isErrorState(control: FormControl | null, form: FormGroupDirective | NgForm | null): boolean {
    const originalErrorState = this.errorStateMatcher.isErrorState(control, form);
    const customErrorState = !!(control && control.invalid && this.submitted);
    return originalErrorState || customErrorState;
  }

  loadMqttCredentials() {
    // @ts-ignore
    this.mqttClientCredentialsService.getMqttClientCredentials(this.data.mqttClientCredentials.id).subscribe(
      (mqttCredentials) => {
        this.mqttCredentialsType = mqttCredentials.credentialsType;
        this.mqttCredentials = mqttCredentials;
        this.mqttCredentialsFormGroup.patchValue({
          credential: mqttCredentials
        },{emitEvent: false});
        this.loadingCredentials = false;
      }
    );
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  save(): void {
    this.submitted = true;
    const credentialsValue = this.mqttCredentialsFormGroup.value.credential;
    this.mqttCredentials = {...this.mqttCredentials, ...credentialsValue};
    this.mqttClientCredentialsService.saveMqttClientCredentials(this.mqttCredentials).subscribe(
      (mqttCredentials) => {
        this.dialogRef.close(mqttCredentials);
      }
    );
  }

  onChangePasswordCloseDialog($event: MqttClientCredentials) {
    this.dialogRef.close($event);
  }

}
