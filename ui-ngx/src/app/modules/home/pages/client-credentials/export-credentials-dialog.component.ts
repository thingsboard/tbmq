///
/// Copyright © 2016-2026 The Thingsboard Authors
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

import { Component } from '@angular/core';
import { MatDialogActions, MatDialogContent, MatDialogRef } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { FormsModule, ReactiveFormsModule, UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { AsyncPipe } from '@angular/common';
import { MatToolbar } from '@angular/material/toolbar';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatOption, MatSelect } from '@angular/material/select';
import { DialogComponent } from '@shared/components/dialog.component';
import { CSV_DELIMITERS, DEFAULT_AUTH_RULES_DELIMITER } from '@shared/import-export/import-export.models';
import { ClientCredentialsService } from '@core/http/client-credentials.service';
import { takeUntil } from 'rxjs/operators';

@Component({
  selector: 'tb-export-credentials-dialog',
  templateUrl: './export-credentials-dialog.component.html',
  styles: [':host { display: block; width: 480px; max-width: 100%; }'],
  imports: [FormsModule, ReactiveFormsModule, TranslateModule, AsyncPipe, MatToolbar, MatIconButton, MatIcon, MatProgressBar,
    MatDialogContent, MatDialogActions, MatButton, MatCheckbox, MatFormField, MatLabel, MatSelect, MatOption]
})
export class ExportCredentialsDialogComponent extends DialogComponent<ExportCredentialsDialogComponent, void> {

  exportFormGroup: UntypedFormGroup;

  delimiters = CSV_DELIMITERS;

  exporting = false;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              public dialogRef: MatDialogRef<ExportCredentialsDialogComponent, void>,
              private fb: UntypedFormBuilder,
              private clientCredentialsService: ClientCredentialsService) {
    super(store, router, dialogRef);
    this.exportFormGroup = this.fb.group({
      includePasswordHashes: [true],
      authRulesDelim: [DEFAULT_AUTH_RULES_DELIMITER, [Validators.required]]
    });
  }

  export(): void {
    if (this.exporting) {
      return;
    }
    this.exporting = true;
    this.clientCredentialsService.exportCredentialsCsv(
      this.exportFormGroup.get('includePasswordHashes').value, this.exportFormGroup.get('authRulesDelim').value)
      .pipe(takeUntil(this.dialogRef.beforeClosed()))
      .subscribe({
        next: () => this.dialogRef.close(),
        error: () => this.exporting = false
      });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
