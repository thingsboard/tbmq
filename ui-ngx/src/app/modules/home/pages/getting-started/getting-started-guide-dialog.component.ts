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

import { Component, OnDestroy } from '@angular/core';
import { DialogComponent } from '@shared/components/dialog.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { MatButton } from '@angular/material/button';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { FormsModule } from '@angular/forms';
import { GettingStartedHomeComponent } from '@home/components/getting-started/getting-started-home.component';

@Component({
  selector: 'tb-getting-started-guide-dialog',
  templateUrl: './getting-started-guide-dialog.component.html',
  styleUrls: ['./getting-started-guide-dialog.component.scss'],
  imports: [TranslateModule, MatSlideToggle, FormsModule, MatButton, GettingStartedHomeComponent]
})
export class GettingStartedGuideDialogComponent extends DialogComponent<GettingStartedGuideDialogComponent> implements OnDestroy {

  notShowAgain = false;

  constructor(protected store: Store<AppState>, protected router: Router, public dialogRef: MatDialogRef<GettingStartedGuideDialogComponent>) {
    super(store, router, dialogRef);
  }

  ngOnDestroy() {
    super.ngOnDestroy();
  }

  close(): void {
    if (this.notShowAgain) {
      localStorage.setItem('notDisplayGettingStartedGuide', 'true');
      this.dialogRef.close(null);
    } else {
      this.dialogRef.close(null);
    }
  }

}
