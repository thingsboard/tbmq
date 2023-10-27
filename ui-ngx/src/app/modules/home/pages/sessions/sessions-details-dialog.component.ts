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
import { ConnectionState, connectionStateColor, DetailedClientSessionInfo } from '@shared/models/session.model';
import { DialogComponent } from '@shared/components/dialog.component';
import { AppState } from '@core/core.state';
import { Store } from '@ngrx/store';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { FormArray, UntypedFormBuilder, UntypedFormGroup } from '@angular/forms';
import { ClientSessionService } from '@core/http/client-session.service';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { appearance } from '@shared/models/constants';
import { ClientType } from '@shared/models/client.model';

export interface SessionsDetailsDialogData {
  session: DetailedClientSessionInfo;
}

@Component({
  selector: 'tb-sessions-details-dialog',
  templateUrl: './sessions-details-dialog.component.html',
  styleUrls: ['./sessions-details-dialog.component.scss'],
  providers: [
    {
      provide: MAT_FORM_FIELD_DEFAULT_OPTIONS,
      useValue: appearance
    }
  ]
})
export class SessionsDetailsDialogComponent extends DialogComponent<SessionsDetailsDialogComponent>
  implements OnInit, OnDestroy, AfterContentChecked {

  entity: DetailedClientSessionInfo;
  entityForm: UntypedFormGroup;
  connectionStateColor = connectionStateColor;
  showAppClientShouldBePersistentWarning: boolean;

  get subscriptions(): FormArray {
    return this.entityForm.get('subscriptions').value as FormArray;
  }

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: SessionsDetailsDialogData,
              public dialogRef: MatDialogRef<SessionsDetailsDialogComponent>,
              private fb: UntypedFormBuilder,
              private clientSessionService: ClientSessionService,
              private cd: ChangeDetectorRef) {
    super(store, router, dialogRef);
  }

  ngOnInit(): void {
    this.entity = this.data.session;
    this.showAppClientShouldBePersistentWarning = this.entity.clientType === ClientType.APPLICATION && this.entity.cleanStart && this.entity.sessionExpiryInterval === 0;
    this.buildForms(this.entity);
  }

  ngAfterContentChecked(): void {
    this.cd.detectChanges();
  }

  private buildForms(entity: DetailedClientSessionInfo): void {
    this.buildSessionForm(entity);
    this.updateFormsValues(entity);
  }

  private buildSessionForm(entity: DetailedClientSessionInfo): void {
    this.entityForm = this.fb.group({
      clientId: [{value: entity ? entity.clientId : null, disabled: false}],
      clientType: [{value: entity ? entity.clientType : null, disabled: false}],
      clientIpAdr: [{value: entity ? entity.clientIpAdr : null, disabled: false}],
      nodeId: [{value: entity ? entity.nodeId : null, disabled: false}],
      keepAliveSeconds: [{value: entity ? entity.keepAliveSeconds : null, disabled: false}],
      sessionExpiryInterval: [{value: entity ? entity.sessionExpiryInterval : null, disabled: false}],
      sessionEndTs: [{value: entity ? entity.sessionEndTs : null, disabled: false}],
      connectedAt: [{value: entity ? entity.connectedAt : null, disabled: false}],
      connectionState: [{value: entity ? entity.connectionState : null, disabled: false}],
      disconnectedAt: [{value: entity ? entity.disconnectedAt : null, disabled: false}],
      subscriptions: [{value: entity ? entity.subscriptions : null, disabled: false}],
      cleanStart: [{value: entity ? entity.cleanStart : null, disabled: true}],
      subscriptionsCount: [{value: entity ? entity.subscriptionsCount : null, disabled: false}]
    });
    this.entityForm.get('subscriptions').valueChanges.subscribe(value => {
      this.entity.subscriptions = value;
    });
  }

  onEntityAction($event, action): void {
    switch (action) {
      case ('save'):
        this.onSave();
        break;
      case ('remove'):
        this.onRemove();
        break;
      case ('disconnect'):
        this.onDisconnect();
        break;
    }
  }

  isConnected(): boolean {
    return this.entityForm?.get('connectionState')?.value && this.entityForm.get('connectionState').value.toUpperCase() === ConnectionState.CONNECTED;
  }

  private onSave(): void {
    const value = {...this.entity, ...this.subscriptions.value};
    this.clientSessionService.updateShortClientSessionInfo(value).subscribe(() => {
      this.closeDialog();
    });

  }

  private onRemove(): void {
    this.clientSessionService.removeClientSession(this.entity.clientId, this.entity.sessionId).subscribe(() => {
      this.closeDialog();
    });
  }

  private onDisconnect(): void {
    this.clientSessionService.disconnectClientSession(this.entity.clientId, this.entity.sessionId).subscribe(
      () => {
        this.closeDialog();
    });
  }

  private updateFormsValues(entity: DetailedClientSessionInfo): void {
    this.entityForm.patchValue({clientId: entity.clientId});
    this.entityForm.patchValue({clientType: entity.clientType});
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
    this.entityForm.patchValue({subscriptionsCount: entity.subscriptions.length});
  }

  private closeDialog(): void {
    this.dialogRef.close();
  }
}
