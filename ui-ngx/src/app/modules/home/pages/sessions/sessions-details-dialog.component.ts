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

import {AfterContentChecked, ChangeDetectorRef, Component, Inject, OnDestroy, OnInit} from '@angular/core';
import {
  ConnectionState,
  connectionStateColor,
  DetailedClientSessionInfo,
  MqttVersionTranslationMap
} from '@shared/models/session.model';
import {DialogComponent} from '@shared/components/dialog.component';
import {AppState} from '@core/core.state';
import {Store} from '@ngrx/store';
import {Router} from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogClose, MatDialogActions } from '@angular/material/dialog';
import { FormArray, UntypedFormBuilder, UntypedFormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import {ClientSessionService} from '@core/http/client-session.service';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS, MatFormField, MatLabel, MatHint, MatSuffix } from '@angular/material/form-field';
import {appearance} from '@shared/models/constants';
import {ClientType, clientTypeIcon, clientTypeTranslationMap} from '@shared/models/client.model';
import { MatToolbar } from '@angular/material/toolbar';
import { FlexModule } from '@angular/flex-layout/flex';
import { TranslateModule } from '@ngx-translate/core';
import { HelpComponent } from '@shared/components/help.component';
import { MatIconButton, MatButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { NgIf, NgStyle, AsyncPipe, TitleCasePipe, DatePipe } from '@angular/common';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatTabGroup, MatTab, MatTabContent } from '@angular/material/tabs';
import { CopyContentButtonComponent } from '@shared/components/button/copy-content-button.component';
import { MatInput } from '@angular/material/input';
import { ExtendedModule } from '@angular/flex-layout/extended';
import { MatCheckbox } from '@angular/material/checkbox';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { EditClientCredentialsButtonComponent } from '@shared/components/button/edit-client-credentials-button.component';
import { SubscriptionsComponent } from '../../components/session-subscriptions/subscriptions.component';
import { SessionMetricsComponent } from '../../components/session-metrics/session-metrics.component';

export interface SessionsDetailsDialogData {
  session: DetailedClientSessionInfo;
  selectedTab?: number;
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
    ],
    standalone: true,
    imports: [FormsModule, ReactiveFormsModule, MatToolbar, FlexModule, TranslateModule, HelpComponent, MatIconButton, MatDialogClose, MatTooltip, MatIcon, NgIf, MatProgressBar, MatTabGroup, MatTab, MatButton, CopyContentButtonComponent, MatFormField, MatLabel, MatInput, ExtendedModule, NgStyle, MatHint, MatCheckbox, CopyButtonComponent, MatSuffix, EditClientCredentialsButtonComponent, MatTabContent, SubscriptionsComponent, SessionMetricsComponent, MatDialogActions, AsyncPipe, TitleCasePipe, DatePipe]
})
export class SessionsDetailsDialogComponent extends DialogComponent<SessionsDetailsDialogComponent>
  implements OnInit, OnDestroy, AfterContentChecked {

  entity: DetailedClientSessionInfo;
  entityForm: UntypedFormGroup;
  connectionStateColor = connectionStateColor;
  showAppClientShouldBePersistentWarning: boolean;
  selectedTab: number;
  clientTypeTranslationMap = clientTypeTranslationMap;
  clientTypeIcon = clientTypeIcon;
  clientCredentials = '';

  private mqttVersionTranslationMap = MqttVersionTranslationMap;

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
    this.selectedTab = this.data.selectedTab || 0;
    this.showAppClientShouldBePersistentWarning = this.entity.clientType === ClientType.APPLICATION && this.entity.cleanStart && this.entity.sessionExpiryInterval === 0;
    this.buildForms(this.entity);
  }

  ngAfterContentChecked(): void {
    this.cd.detectChanges();
  }

  private buildForms(entity: DetailedClientSessionInfo): void {
    this.buildSessionForm(entity);
    this.updateFormsValues(entity);
    this.getAdditionalInfo(entity);
  }

  private buildSessionForm(entity: DetailedClientSessionInfo): void {
    this.entityForm = this.fb.group({
      clientId: [{value: entity ? entity.clientId : null, disabled: true}],
      clientType: [{value: entity ? entity.clientType : null, disabled: true}],
      clientIpAdr: [{value: entity ? entity.clientIpAdr : null, disabled: true}],
      nodeId: [{value: entity ? entity.nodeId : null, disabled: true}],
      keepAliveSeconds: [{value: entity ? entity.keepAliveSeconds : null, disabled: true}],
      sessionExpiryInterval: [{value: entity ? entity.sessionExpiryInterval : null, disabled: true}],
      sessionEndTs: [{value: entity ? entity.sessionEndTs : null, disabled: true}],
      connectedAt: [{value: entity ? entity.connectedAt : null, disabled: true}],
      connectionState: [{value: entity ? entity.connectionState : null, disabled: true}],
      disconnectedAt: [{value: entity ? entity.disconnectedAt : null, disabled: true}],
      subscriptions: [{value: entity ? entity.subscriptions : null, disabled: false}],
      cleanStart: [{value: entity ? entity.cleanStart : null, disabled: true}],
      subscriptionsCount: [{value: entity ? entity.subscriptionsCount : null, disabled: false}],
      credentials: [{value: null, disabled: true}],
      mqttVersion: [{value: null, disabled: true}]
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

  getAdditionalInfo(entity: DetailedClientSessionInfo) {
    const result = 'Unknown';
    this.clientSessionService.getClientSessionDetails(entity.clientId, {ignoreErrors: true}).subscribe(
      credentials => {
        this.entityForm.patchValue({
          credentials: credentials.name || result,
          mqttVersion: this.mqttVersionTranslationMap.get(credentials.mqttVersion) || result
        });
      },
      () => {
        this.entityForm.patchValue({
          credentials: result,
          mqttVersion: result
        });
      }
    );
  }

  private onSave(): void {
    this.clientSessionService.updateShortClientSessionInfo(this.entity).subscribe(() => {
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
    this.dialogRef.close(true);
  }
}
