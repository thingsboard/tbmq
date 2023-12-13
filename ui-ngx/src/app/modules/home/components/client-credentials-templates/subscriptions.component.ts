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

import { ChangeDetectorRef, Component, Input } from '@angular/core';
import { FormBuilder, } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { WsClientService } from '@core/http/ws-client.service';
import { Connection } from '@shared/models/ws-client.model';
import { isDefinedAndNotNull } from '@core/utils';
import { MatDialog } from '@angular/material/dialog';
import {
  AddWsClientSubscriptionDialogData,
  WsClientSubscriptionDialogComponent
} from '@home/components/client-credentials-templates/ws-client-subscription-dialog.component';

@Component({
  selector: 'tb-ws-client-subscriptions',
  templateUrl: './subscriptions.component.html',
  styleUrls: ['./subscriptions.component.scss']
})
export class SubscriptionsComponent {

  @Input()
  connection: Connection;

  subscriptions = [];

  constructor(public fb: FormBuilder,
              public cd: ChangeDetectorRef,
              private dialog: MatDialog,
              private wsClientService: WsClientService,
              private translate: TranslateService) {
    /*wsClientService.getSubscriptions().subscribe(res => {
      // this.subscriptions = res.data;
    });
    wsClientService.allSubscriptions$.subscribe(res => {
      this.subscriptions = res;
    })*/
    wsClientService.selectedConnection$.subscribe(
      res => {
        if (res) {
          wsClientService.getSubscriptions().subscribe(
            subs => this.subscriptions = subs
          );
        }
      }
    );
  }

  addSubscription() {
    const data = {
      subscription: null
    };
    this.dialog.open<WsClientSubscriptionDialogComponent, AddWsClientSubscriptionDialogData>(WsClientSubscriptionDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data
    }).afterClosed()
      .subscribe((res) => {
        if (isDefinedAndNotNull(res)) {
          this.wsClientService.addSubscription(res);
        }
      });
  }

  editSubscription(subscription) {

  }

  deleteSubscription(subscription) {

  }
}

