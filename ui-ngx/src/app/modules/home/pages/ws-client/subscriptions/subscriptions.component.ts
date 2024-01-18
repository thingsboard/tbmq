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

import { ChangeDetectorRef, Component } from '@angular/core';
import { WsClientService } from '@core/http/ws-client.service';
import { Connection, WsSubscription } from '@shared/models/ws-client.model';
import { MatDialog } from '@angular/material/dialog';
import { Observable, ReplaySubject } from 'rxjs';
import { map, share, tap } from 'rxjs/operators';
import {
  AddWsClientSubscriptionDialogData,
  SubscriptionDialogComponent
} from '@home/pages/ws-client/subscriptions/subscription-dialog.component';
import { isDefinedAndNotNull } from '@core/utils';

@Component({
  selector: 'tb-subscriptions',
  templateUrl: './subscriptions.component.html',
  styleUrls: ['./subscriptions.component.scss']
})
export class SubscriptionsComponent {

  subscriptions$: Observable<WsSubscription[]>;
  subscriptions: WsSubscription[];
  connection: Connection;
  loadSubscriptions = false;

  constructor(private dialog: MatDialog,
              private wsClientService: WsClientService,
              private cd: ChangeDetectorRef) {
  }

  ngOnInit() {
    this.wsClientService.selectedConnection$.subscribe(
      res => {
        this.loadSubscriptions = true;
        this.connection = res;
        this.fetchSubcription(res);
      }
    );
  }

  fetchSubcription(connection) {
    this.subscriptions$ = this.wsClientService.getSubscriptionsV3(connection.id).pipe(
      map(res => {
        if (res.length) {
          this.subscriptions = res;
          return res;
        }
        return [];
      }),
      share({
        connector: () => new ReplaySubject(1)
      }),
      tap(() => setTimeout(() => this.cd.markForCheck()))
    );
    // this.subscriptions$.subscribe();
  }

  addSubscription($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<SubscriptionDialogComponent, AddWsClientSubscriptionDialogData>(SubscriptionDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        connection: this.connection
      }
    }).afterClosed()
      .subscribe((res) => {
        if (isDefinedAndNotNull(res)) {
          this.wsClientService.saveSubscriptionV3(this.connection.id, res, false).subscribe(
            (value) => {

            }
          );
        }
      });
  }

  trackById(index: number, item: Connection): string {
    return item.id;
  }
}

