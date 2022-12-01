import { Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { ConnectionState, connectionStateColor, DetailedClientSessionInfo } from "@shared/models/mqtt-session.model";
import { DialogComponent } from "@shared/components/dialog.component";
import { AppState } from "@core/core.state";
import { Store } from "@ngrx/store";
import { Router } from "@angular/router";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { FormBuilder, FormGroup } from "@angular/forms";

export interface SessionsDetailsDialogData {
  session: DetailedClientSessionInfo;
}

@Component({
  selector: 'tb-sessions-details-dialog',
  templateUrl: './sessions-details-dialog.component.html',
  styleUrls: ['./sessions-details-dialog.component.scss']
})
export class SessionsDetailsDialogComponent extends DialogComponent<SessionsDetailsDialogComponent> implements OnInit, OnDestroy {

  entity: DetailedClientSessionInfo;
  entityForm: FormGroup;
  sessionDetailsForm: FormGroup;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: SessionsDetailsDialogData,
              public dialogRef: MatDialogRef<SessionsDetailsDialogComponent>,
              private fb: FormBuilder) {
    super(store, router, dialogRef);
  }

  ngOnInit(): void {
    this.entity = this.data.session;
    this.buildForm(this.entity);
  }

  private buildForm(entity: DetailedClientSessionInfo) {

    this.sessionDetailsForm = this.fb.group({
      cleanSession: [entity ? !entity.persistent : null],
      subscriptionsCount: [entity ? entity.subscriptions.length : null]
    });

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

    this.sessionDetailsForm.patchValue({cleanSession: !entity.persistent}, {emitEvent: false} );
    this.sessionDetailsForm.patchValue({subscriptionsCount: entity.subscriptions.length}, {emitEvent: false} );
  }

  onEntityAction($event, action) {
    switch (action) {
      case ('save'):
        this.onSave($event)
        break;
      case ('remove'):
          this.onRemove($event);
        break;
      case ('disconnect'):
          this.onDisconnect($event);
        break;
    }
  }

  isConnected(): boolean {
    if (this.entityForm.get('connectionState').value) {
      return this.entityForm.get('connectionState').value.toUpperCase() === ConnectionState.CONNECTED;
    }
  }

  getColor(): {color: string} {
    if (this.entityForm.get('connectionState').value) {
      return {color: connectionStateColor.get(this.entityForm.get('connectionState').value.toUpperCase())};
    }
  }

  private onSave($event) {

  }

  private onRemove($event) {

  }

  private onDisconnect($event) {

  }
}
