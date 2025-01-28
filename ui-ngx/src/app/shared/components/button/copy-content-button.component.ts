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

import { Component, input } from '@angular/core';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { MatButton } from '@angular/material/button';
import { ClipboardModule } from 'ngx-clipboard';
import { MatIcon } from '@angular/material/icon';

@Component({
    selector: 'tb-copy-content-button',
    templateUrl: './copy-content-button.component.html',
    imports: [MatButton, ClipboardModule, MatIcon, TranslateModule]
})
export class CopyContentButtonComponent {

  readonly title = input('action.copy-id');
  readonly cbContent = input<string>();
  readonly isEdit = input<boolean>();

  constructor(private store: Store<AppState>,
              private translate: TranslateService) {
  }

  onCopied() {
    this.store.dispatch(new ActionNotificationShow(
      {
        message: this.translate.instant('action.on-copied'),
        type: 'success',
        duration: 1000,
        verticalPosition: 'top',
        horizontalPosition: 'left'
      }));
  }
}
