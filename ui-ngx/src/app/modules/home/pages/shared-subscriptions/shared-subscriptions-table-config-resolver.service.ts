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

import { Injectable } from '@angular/core';

import { Resolve } from '@angular/router';
import {
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { DialogService } from '@core/services/dialog.service';
import { SharedSubscription } from "@shared/models/shared-subscription.model";
import { SharedSubscriptionComponent } from "@home/pages/shared-subscriptions/shared-subscription.component";
import { SharedSubscriptionService } from "@core/http/shared-subscription.service";

@Injectable()
export class SharedSubscriptionsTableConfigResolver implements Resolve<EntityTableConfig<SharedSubscription>> {

  private readonly config: EntityTableConfig<SharedSubscription> = new EntityTableConfig<SharedSubscription>();

  constructor(private store: Store<AppState>,
              private dialogService: DialogService,
              private sharedSubscriptionService: SharedSubscriptionService,
              private translate: TranslateService,
              private datePipe: DatePipe) {

    this.config.entityType = EntityType.SHARED_SUBSCRIPTION;
    this.config.entityComponent = SharedSubscriptionComponent;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.SHARED_SUBSCRIPTION);
    this.config.entityResources = entityTypeResources.get(EntityType.SHARED_SUBSCRIPTION);
    this.config.tableTitle = this.translate.instant('shared-subscription.shared-subscriptions');
    this.config.entityTitle = (entity) => entity ? entity.name : '';

    this.config.columns.push(
      new DateEntityTableColumn<SharedSubscription>('createdTime', 'common.created-time', this.datePipe, '150px'),
      new EntityTableColumn<SharedSubscription>('name', 'shared-subscription.name', '33%'),
      new EntityTableColumn<SharedSubscription>('partitions', 'shared-subscription.partitions', '33%'),
      new EntityTableColumn<SharedSubscription>('topic', 'shared-subscription.topic', '33%')
    );

    this.config.addActionDescriptors.push(
      {
        name: this.translate.instant('shared-subscription.add'),
        icon: 'add',
        isEnabled: () => true,
        onAction: ($event) => this.config.table.addEntity($event)
      }
    );

    this.config.deleteEntityTitle = entity => this.translate.instant('shared-subscription.delete-shared-subscription-title',
      { name: entity.name });
    this.config.deleteEntityContent = () => this.translate.instant('shared-subscription.delete-shared-subscription-text');
    this.config.deleteEntitiesTitle = count => this.translate.instant('shared-subscription.delete-shared-subscriptions-title', {count});
    this.config.deleteEntitiesContent = () => this.translate.instant('shared-subscription.delete-shared-subscriptions-text');

    this.config.loadEntity = id => this.loadEntity(id);
    this.config.saveEntity = entity => this.sharedSubscriptionService.saveSharedSubscription(entity);
    this.config.deleteEntity = id => this.deleteEntity(id);
  }

  resolve(): EntityTableConfig<SharedSubscription> {
    this.config.entitiesFetchFunction = pageLink => this.sharedSubscriptionService.getSharedSubscriptions(pageLink);
    return this.config;
  }

  loadEntity(id) {
    return this.sharedSubscriptionService.getSharedSubscriptionById(id);
  }

  deleteEntity(id) {
    return this.sharedSubscriptionService.deleteSharedSubscription(id);
  }
}
