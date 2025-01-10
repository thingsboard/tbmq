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

import { Component, Inject } from '@angular/core';
import { DialogComponent } from '@shared/components/dialog.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogContent, MatDialogActions } from '@angular/material/dialog';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { isNotEmptyStr } from '@core/utils';
import { MatToolbar } from '@angular/material/toolbar';
import { FlexModule } from '@angular/flex-layout/flex';
import { MatIconButton, MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { AsyncPipe } from '@angular/common';
import { MatProgressBar } from '@angular/material/progress-bar';
import { CdkScrollable } from '@angular/cdk/scrolling';
import { JsonObjectEditComponent } from '../json-object-edit.component';

export interface JsonObjectEditDialogData {
  jsonValue: object;
  required?: boolean;
  title?: string;
  saveLabel?: string;
  cancelLabel?: string;
}

@Component({
    selector: 'tb-object-edit-dialog',
    templateUrl: './json-object-edit-dialog.component.html',
    styleUrls: [],
    imports: [FormsModule, ReactiveFormsModule, MatToolbar, FlexModule, MatIconButton, MatIcon, MatProgressBar, CdkScrollable, MatDialogContent, JsonObjectEditComponent, MatDialogActions, MatButton, AsyncPipe, TranslateModule]
})
export class JsonObjectEditDialogComponent extends DialogComponent<JsonObjectEditDialogComponent, object> {

  jsonFormGroup: FormGroup;
  title = this.translate.instant('details.edit-json');
  saveButtonLabel = this.translate.instant('action.save');
  cancelButtonLabel = this.translate.instant('action.cancel');

  required = this.data.required === true;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: JsonObjectEditDialogData,
              public dialogRef: MatDialogRef<JsonObjectEditDialogComponent, object>,
              public fb: FormBuilder,
              private translate: TranslateService) {
    super(store, router, dialogRef);
    if (isNotEmptyStr(this.data.title)) {
      this.title = this.data.title;
    }
    if (isNotEmptyStr(this.data.saveLabel)) {
      this.saveButtonLabel = this.data.saveLabel;
    }
    if (isNotEmptyStr(this.data.cancelLabel)) {
      this.cancelButtonLabel = this.data.cancelLabel;
    }
    this.jsonFormGroup = this.fb.group({
      json: [this.data.jsonValue, []]
    });
  }

  cancel(): void {
    this.dialogRef.close(undefined);
  }

  add(): void {
    this.dialogRef.close(this.jsonFormGroup.get('json').value);
  }
}
