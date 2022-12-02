import { AfterContentChecked, ChangeDetectorRef, Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { ConnectionState, connectionStateColor, DetailedClientSessionInfo } from "@shared/models/mqtt-session.model";
import { DialogComponent } from "@shared/components/dialog.component";
import { AppState } from "@core/core.state";
import { Store } from "@ngrx/store";
import { Router } from "@angular/router";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { FormArray, FormBuilder, FormGroup } from "@angular/forms";
import { MqttClientSessionService } from "@core/http/mqtt-client-session.service";

export interface SessionsDetailsDialogData {
  session: DetailedClientSessionInfo;
}

@Component({
  selector: 'tb-sessions-details-dialog',
  templateUrl: './sessions-details-dialog.component.html',
  styleUrls: ['./sessions-details-dialog.component.scss']
})
export class SessionsDetailsDialogComponent extends DialogComponent<SessionsDetailsDialogComponent>
  implements OnInit, OnDestroy, AfterContentChecked {

  entity: DetailedClientSessionInfo;
  entityForm: FormGroup;
  sessionDetailsForm: FormGroup;
  connectionStateColor = connectionStateColor;

  get subscriptions(): FormArray {
    return this.entityForm.get('subscriptions').value as FormArray;
  }

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: SessionsDetailsDialogData,
              public dialogRef: MatDialogRef<SessionsDetailsDialogComponent>,
              private fb: FormBuilder,
              private mqttClientSessionService: MqttClientSessionService,
              private cd: ChangeDetectorRef) {
    super(store, router, dialogRef);
  }

  ngOnInit(): void {
    this.entity = this.data.session;
    this.buildForms(this.entity);
  }

  ngAfterContentChecked(): void {
    this.cd.detectChanges();
  }

  private buildForms(entity: DetailedClientSessionInfo): void {
    this.buildForm(entity);
    this.buildSubscriptionForm(entity);
    this.updateFormsValues(entity);
  }

  private buildForm(entity: DetailedClientSessionInfo): void {
    this.entityForm = this.fb.group({
      clientId: [entity ? entity.clientId : null],
      clientType: [entity ? entity.clientType : null],
      nodeId: [entity ? entity.nodeId : null],
      keepAliveSeconds: [entity ? entity.keepAliveSeconds : null],
      connectedAt: [entity ? entity.connectedAt : null],
      connectionState: [entity ? entity.connectionState : null],
      persistent: [entity ? entity.persistent : null],
      disconnectedAt: [entity ? entity.disconnectedAt : null],
      subscriptions: [entity ? entity.subscriptions : null]
    });
    this.entityForm.get('subscriptions').valueChanges.subscribe(value => {
      this.entity.subscriptions = value;
    });
  }

  private buildSubscriptionForm(entity: DetailedClientSessionInfo): void {
    this.sessionDetailsForm = this.fb.group({
      cleanSession: [entity ? !entity.persistent : null],
      subscriptionsCount: [entity ? entity.subscriptions.length : null]
    });
  }

  onEntityAction($event, action): void {
    switch (action) {
      case ('save'):
        this.onSave()
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
    this.mqttClientSessionService.updateShortClientSessionInfo(value).subscribe(() => {
      this.closeDialog();
    });

  }

  private onRemove(): void {
    this.mqttClientSessionService.removeClientSession(this.entity.clientId, this.entity.sessionId).subscribe(() => {
      this.closeDialog();
    });
  }

  private onDisconnect(): void {
    this.mqttClientSessionService.disconnectClientSession(this.entity.clientId, this.entity.sessionId).subscribe((value) => {
      this.mqttClientSessionService.getDetailedClientSessionInfo(this.entity.clientId).subscribe(
        (entity: DetailedClientSessionInfo) => {
          this.updateFormsValues(entity);
        }
      )
    });
  }

  private updateFormsValues(entity: DetailedClientSessionInfo): void {
    this.entityForm.patchValue({clientId: entity.clientId} );
    this.entityForm.patchValue({clientType: entity.clientType} );
    this.entityForm.patchValue({nodeId: entity.nodeId} );
    this.entityForm.patchValue({keepAliveSeconds: entity.keepAliveSeconds} );
    this.entityForm.patchValue({connectedAt: entity.connectedAt} );
    this.entityForm.patchValue({connectionState: entity.connectionState});
    this.entityForm.patchValue({persistent: entity.persistent} );
    this.entityForm.patchValue({persistent: entity.persistent} );
    this.entityForm.patchValue({disconnectedAt: entity.disconnectedAt} );
    this.entityForm.patchValue({subscriptions: entity.subscriptions} );
    this.sessionDetailsForm.patchValue({cleanSession: !entity.persistent});
    this.sessionDetailsForm.patchValue({subscriptionsCount: entity.subscriptions.length});
  }

  private closeDialog(): void {
    this.dialogRef.close();
  }
}
