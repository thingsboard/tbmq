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

import { ChangeDetectorRef, Component, Input, NgZone, OnDestroy, OnInit } from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { TbPopoverComponent } from '@shared/components/popover.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { BehaviorSubject, Observable, ReplaySubject, Subscription } from 'rxjs';
import { map, share, tap } from 'rxjs/operators';
import { Router } from '@angular/router';
import { WsClientService } from '@core/http/ws-client.service';
import { Connection } from '@shared/models/ws-client.model';
import { ConnectionDialogData, ConnectionWizardDialogComponent } from '@home/components/wizard/connection-wizard-dialog.component';
import { isDefinedAndNotNull } from "@core/utils";
import { MatDialog } from "@angular/material/dialog";

@Component({
  selector: 'tb-show-connections-popover',
  templateUrl: './show-select-connection-popover.component.html',
  styleUrls: []
})
export class ShowSelectConnectionPopoverComponent extends PageComponent implements OnDestroy, OnInit {

  @Input()
  onClose: () => void;

  @Input()
  popoverComponent: TbPopoverComponent;

  // private notificationSubscriber: NotificationSubscriber;
  // private notificationCountSubscriber: Subscription;

  connections$: Observable<Connection[]>;
  loadConnection = true;
  connectionsTotal: number;

  constructor(protected store: Store<AppState>,
              private wsClientService: WsClientService,
              private dialog: MatDialog,
              private zone: NgZone,
              private cd: ChangeDetectorRef,
              private router: Router) {
    super(store);
  }

  ngOnInit() {
    // this.notificationSubscriber = NotificationSubscriber.createNotificationsSubscription(this.notificationWsService, this.zone, 6);
    this.connections$ = this.wsClientService.getConnections().pipe(
      map(res => {
        if (res.data?.length) {
          this.connectionsTotal = res.data.length;
          this.loadConnection = true;
          return res.data;
        }
        return [];
      }),
      share({
        connector: () => new ReplaySubject(1)
      }),
      tap(() => setTimeout(() => this.cd.markForCheck()))
    );
    // this.notificationCountSubscriber = this.notificationSubscriber.notificationCount$.subscribe(value => this.counter.next(value));
    // this.notificationSubscriber.subscribe();
  }

  ngOnDestroy() {
    super.ngOnDestroy();
    // this.notificationCountSubscriber.unsubscribe();
    // this.notificationSubscriber.unsubscribe();
    this.onClose();
  }

  addConnection($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<ConnectionWizardDialogComponent, ConnectionDialogData>(ConnectionWizardDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        connectionsTotal: this.connectionsTotal
      }
    }).afterClosed()
      .subscribe((res) => {
        if (isDefinedAndNotNull(res)) {
          /*this.wsClientService.saveConnection(res).subscribe(
            () => {
              // this.updateData()
            }
          );*/
        } else {
          this.onClose();
        }
      });
  }

  trackById(index: number, item: Connection): string {
    return item.id;
  }

  close() {
    this.onClose();
  }
}
