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
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogContent, MatDialogRef } from '@angular/material/dialog';
import { MatToolbar } from '@angular/material/toolbar';
import { MatIcon } from '@angular/material/icon';
import { TbMarkdownComponent } from '@shared/components/markdown.component';
import { MatButton, MatIconButton } from '@angular/material/button';
import { AsyncPipe } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';

export interface PasswordChangedDialogData {
  password: string;
}

@Component({
  selector: 'tb-password-changed-dialog',
  templateUrl: './password-changed-dialog.component.html',
  imports: [
    MatToolbar,
    MatIcon,
    TbMarkdownComponent,
    MatDialogActions,
    MatButton,
    AsyncPipe,
    TranslateModule,
    MatDialogContent,
    MatIconButton
  ],
  styleUrls: ['password-changed-dialog.component.scss']
})
export class PasswordChangedDialogComponent extends DialogComponent<PasswordChangedDialogComponent, void> {

  constructor(protected store: Store<AppState>,
              protected router: Router,
              protected dialogRef: MatDialogRef<PasswordChangedDialogComponent, void>,
              @Inject(MAT_DIALOG_DATA) public data: PasswordChangedDialogData) {
    super(store, router, dialogRef);
  }

  close(): void {
    this.dialogRef.close(null);
  }

  createMarkDownCommand(command: string): string {
    return '```bash\n' +
      command +
      '{:copy-code}\n' +
      '```';
  }
}
