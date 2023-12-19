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
import { connectionStateColor } from '@shared/models/session.model';
import { DialogComponent } from '@shared/components/dialog.component';
import { AppState } from '@core/core.state';
import { Store } from '@ngrx/store';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { appearance } from '@shared/models/constants';
import { clientTypeIcon, clientTypeTranslationMap } from '@shared/models/client.model';
import { ClientCredentials } from '@shared/models/credentials.model';
import { WsClientService } from '@core/http/ws-client.service';
import { isDefinedAndNotNull } from '@core/utils';
import { Connection, ConnectionDetailed } from '@shared/models/ws-client.model';

export interface AddWsClientConnectionDialogData {
  connection: Connection;
}

@Component({
  selector: 'tb-ws-client-details-dialog',
  templateUrl: './ws-client-connection-dialog.component.html',
  styleUrls: ['./ws-client-connection-dialog.component.scss'],
  providers: [
    {
      provide: MAT_FORM_FIELD_DEFAULT_OPTIONS,
      useValue: appearance
    }
  ]
})
export class WsClientConnectionDialogComponent extends DialogComponent<WsClientConnectionDialogComponent>
  implements OnInit, OnDestroy, AfterContentChecked {

  entity: ConnectionDetailed;
  connectionStateColor = connectionStateColor;
  clientTypeTranslationMap = clientTypeTranslationMap;
  clientTypeIcon = clientTypeIcon;

  clientForm: UntypedFormGroup;
  displayPasswordWarning: boolean;
  selectedExistingCredentials: ClientCredentials;
  client = true;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: any,
              public dialogRef: MatDialogRef<WsClientConnectionDialogComponent>,
              private fb: UntypedFormBuilder,
              private wsClientService: WsClientService,
              private cd: ChangeDetectorRef) {
    super(store, router, dialogRef);
  }

  ngOnInit(): void {
    this.entity = this.data.connection;
    this.buildForms(this.entity);
  }

  ngAfterContentChecked(): void {
    this.cd.detectChanges();
  }

  private buildForms(entity: ConnectionDetailed): void {
    this.buildSessionForm();
    if (entity) {
      this.updateFormsValues(entity);
    }
  }

  private buildSessionForm(): void {
    this.clientForm = this.fb.group({
      name: ['TBMQ Client', [Validators.required]],
      protocol: ['ws://', [Validators.required]],
      host: [window.location.hostname, [Validators.required]],
      port: [8084, [Validators.required]],
      path: ['/mqtt', [Validators.required]],
      clientId: ['tbmq_dev', []],
      username: ['tbmq_dev', []],
      password: ['tbmq_dev', []],
      keepAlive: [60, [Validators.required]],
      reconnectPeriod: [1000, [Validators.required]],
      connectTimeout: [30 * 1000, [Validators.required]],
      clean: [true, []],
      protocolVersion: ['5', []],
      properties: this.fb.group({
        sessionExpiryInterval: [null, []],
        receiveMaximum: [null, []],
        maximumPacketSize: [null, []],
        topicAliasMaximum: [null, []],
        requestResponseInformation: [null, []],
        requestProblemInformation: [null, []],
        userProperties: [null, []],
      }),
      userProperties: [null, []],
      will: [null, []],
      autocomplete: [null, []]
    });
  }

  isConnected(): boolean {
    return true;
  }

  onSave(): void {
    const value = this.clientForm.getRawValue();
    this.dialogRef.close(value);
    /*this.wsClientService.saveConnection(this.entity).subscribe((res) => {
      if (res) {
        const value = this.clientForm.getRawValue();
        this.dialogRef.close(value);
      }
    });*/
  }

  private updateFormsValues(entity: ConnectionDetailed): void {
    this.clientForm.patchValue({name: entity.name});
    /*this.entityForm.patchValue({clientType: entity.clientType});
    this.entityForm.patchValue({clientIpAdr: entity.clientIpAdr});
    this.entityForm.patchValue({nodeId: entity.nodeId});
    this.entityForm.patchValue({keepAliveSeconds: entity.keepAliveSeconds});
    this.entityForm.patchValue({sessionExpiryInterval: entity.sessionExpiryInterval});
    this.entityForm.patchValue({sessionEndTs: entity.sessionEndTs});
    this.entityForm.patchValue({connectedAt: entity.connectedAt});
    this.entityForm.patchValue({connectionState: entity.connectionState});
    this.entityForm.patchValue({disconnectedAt: entity.disconnectedAt});
    this.entityForm.patchValue({subscriptions: entity.subscriptions});
    this.entityForm.patchValue({cleanStart: entity.cleanStart});
    this.entityForm.patchValue({subscriptionsCount: entity.subscriptions.length});*/
  }

  addClient() {
    this.wsClientService.addConnection(this.clientForm.getRawValue());
  }

  clientCredentialsChanged(credentials: ClientCredentials) {
    if (credentials?.credentialsValue) {
      const credentialsValue = JSON.parse(credentials.credentialsValue);
      this.clientForm.patchValue({
        clientId: credentialsValue.clientId,
        username: credentialsValue.userName,
        password: null
      });
      if (isDefinedAndNotNull(credentialsValue.password)) {
        this.displayPasswordWarning = true;
        this.clientForm.get('password').setValidators([Validators.required]);
      } else {
        this.displayPasswordWarning = false;
        this.clientForm.get('password').clearValidators();
      }
      this.clientForm.get('password').updateValueAndValidity();
    } else {
      this.clientForm.patchValue({
        clientId: null,
        username: null
      })
    }
  }
}
