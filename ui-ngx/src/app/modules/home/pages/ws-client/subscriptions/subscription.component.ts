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

import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import {Connection, ConnectionDetailed, SubscriptionTopicFilter} from '@shared/models/ws-client.model';
import { CellActionDescriptor } from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { WsClientService } from '@core/http/ws-client.service';
import {BaseData} from "@shared/models/base-data";
import {MatDialog} from "@angular/material/dialog";
import {DialogService} from "@core/services/dialog.service";
import {ConnectionWizardDialogComponent} from "@home/components/wizard/connection-wizard-dialog.component";
import {isDefinedAndNotNull} from "@core/utils";
import {
  AddWsClientSubscriptionDialogData,
  WsClientSubscriptionDialogComponent
} from "@home/pages/ws-client/ws-client-subscription-dialog.component";

@Component({
  selector: 'tb-subscription',
  templateUrl: './subscription.component.html',
  styleUrls: ['./subscription.component.scss']
})
export class SubscriptionComponent implements OnInit {

  @Input()
  subscription: SubscriptionTopicFilter;

  @Output()
  subscriptionDeleted = new EventEmitter<SubscriptionTopicFilter>();

  @Output()
  subscriptionEdited = new EventEmitter<SubscriptionTopicFilter>();

  showActions = false;
  hiddenActions = this.configureCellHiddenActions();

  constructor(private wsClientService: WsClientService,
              private dialog: MatDialog,
              private dialogService: DialogService,
              private translate: TranslateService) {

  }

  ngOnInit() {
  }

  onCommentMouseEnter(): void {
    this.showActions = true;
  }

  onCommentMouseLeave(): void {
    this.showActions = false;
  }

  private configureCellHiddenActions(): Array<CellActionDescriptor<SubscriptionTopicFilter>> {
    const actions: Array<CellActionDescriptor<SubscriptionTopicFilter>> = [];
    actions.push(
      {
        name: this.translate.instant('action.edit'),
        icon: 'edit',
        isEnabled: () => true,
        onAction: ($event, entity) => this.edit($event, entity)
      },
      {
        name: this.translate.instant('action.delete'),
        icon: 'delete',
        isEnabled: () => true,
        onAction: ($event, entity) => this.delete($event, entity)
      }
    );
    return actions;
  }

  private delete($event: Event, entity: SubscriptionTopicFilter) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('ws-client.subscriptions.delete-subscription-title', {topic: this.subscription.topic}),
      this.translate.instant('ws-client.subscriptions.delete-subscription-text', {topic: this.subscription.topic}),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        this.subscriptionDeleted.emit(this.subscription);
      }
    });
  }

  private edit($event: Event, entity: SubscriptionTopicFilter) {
    if ($event) {
      $event.stopPropagation();
    }
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
          this.wsClientService.saveConnection(res).subscribe(
            () => {
              // this.updateData()
            }
          );
        }
      });
  }

}
