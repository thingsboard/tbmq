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

import { booleanAttribute, Component, input } from '@angular/core';
import { TooltipPosition, MatTooltip } from '@angular/material/tooltip';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { ClientCredentialsService } from '@core/http/client-credentials.service';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';

@Component({
    selector: 'tb-edit-client-credentials-button',
    styleUrls: ['edit-client-credentials-button.component.scss'],
    templateUrl: './edit-client-credentials-button.component.html',
    imports: [MatIconButton, MatTooltip, MatIcon, TranslateModule]
})
export class EditClientCredentialsButtonComponent {

  readonly disabled = input(false, {transform: booleanAttribute});
  readonly name = input<string>();
  readonly tooltipText = input<string>(this.translate.instant('action.edit'));
  readonly tooltipPosition = input<TooltipPosition>('above');

  constructor(private clientCredentialsService: ClientCredentialsService,
              private store: Store<AppState>,
              private translate: TranslateService,
              private router: Router) {
  }

  editClientCredentials($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    this.clientCredentialsService.getClientCredentialsByName(this.name(), {ignoreErrors: true}).subscribe(
      credentials => {
        if (credentials) {
          this.router.navigate(['client-credentials', credentials.id]);
        } else {
          this.clientNotFound();
        }
      },
      () => {
        this.clientNotFound();
      }
    );
  }

  private clientNotFound() {
    this.store.dispatch(new ActionNotificationShow(
      {
        message: this.translate.instant('mqtt-client-credentials.no-client-credentials-text'),
        type: 'error',
        duration: 2000,
      })
    );
  }

}
