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
import { FormBuilder, UntypedFormGroup } from '@angular/forms';
import { Subject } from 'rxjs';
import { mqttQoSTypes } from '@shared/models/session.model';
import { DialogComponent } from '@shared/components/dialog.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Connection } from '@shared/models/ws-client.model';

@Component({
  selector: 'tb-ws-client-properties',
  templateUrl: './properties-dialog.component.html',
  styleUrls: ['./properties-dialog.component.scss']
})
export class PropertiesDialogComponent extends DialogComponent<PropertiesDialogComponent>
  implements OnInit, OnDestroy, AfterContentChecked {

  formGroup: UntypedFormGroup;
  entity: any;

  mqttQoSTypes = mqttQoSTypes;

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
    this.entity = this.data;
    this.buildForms(this.entity);
  }

  ngAfterContentChecked(): void {
    this.cd.detectChanges();
  }

  private buildForms(entity: Connection): void {
    this.buildForm();
    if (entity) {
      this.updateFormsValues(entity);
    }
  }

  private buildForm(): void {
    this.formGroup = this.fb.group({
      payloadFormatIndicator: [null, []],
      contentType: [null, []],
      messageExpiryInterval: [null, []],
      topicAlias: [null, []],
      subscriptionIdentifier: [null, []],
      correlationData: [null, []],
      responseTopic: [null, []],
      userProperties: [null, []]
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateFormsValues(entity: any): void {
    this.formGroup.patchValue({});
  }

  onSave() {

  }
}

