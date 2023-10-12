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

import {
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { SharedSubscription } from "@shared/models/shared-subscription.model";
import { SharedSubscriptionComponent } from "@home/pages/shared-subscriptions/shared-subscription.component";
import { SharedSubscriptionService } from "@core/http/shared-subscription.service";

export class SharedSubscriptionsTableConfig extends EntityTableConfig<SharedSubscription> {

  constructor(private sharedSubscriptionService: SharedSubscriptionService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              public entityId: string = null) {
    super();

    this.entityType = EntityType.SHARED_SUBSCRIPTION;
    this.entityComponent = SharedSubscriptionComponent;
    this.entityTranslations = entityTypeTranslations.get(EntityType.SHARED_SUBSCRIPTION);
    this.entityResources = entityTypeResources.get(EntityType.SHARED_SUBSCRIPTION);
    this.tableTitle = this.translate.instant('shared-subscription.application-shared-subscriptions');
    this.entityTitle = (entity) => entity ? entity.name : '';

    this.columns.push(
      new DateEntityTableColumn<SharedSubscription>('createdTime', 'common.created-time', this.datePipe, '150px'),
      new EntityTableColumn<SharedSubscription>('name', 'shared-subscription.name', '33%'),
      new EntityTableColumn<SharedSubscription>('topicFilter', 'shared-subscription.topic-filter', '33%'),
      new EntityTableColumn<SharedSubscription>('partitions', 'shared-subscription.partitions', '33%')
    );

    this.addActionDescriptors.push(
      {
        name: this.translate.instant('shared-subscription.add'),
        icon: 'add',
        isEnabled: () => true,
        onAction: ($event) => this.getTable().addEntity($event)
      }
    );

    this.deleteEntityTitle = entity => this.translate.instant('shared-subscription.delete-shared-subscription-title',
      { name: entity.name });
    this.deleteEntityContent = () => this.translate.instant('shared-subscription.delete-shared-subscription-text');
    this.deleteEntitiesTitle = count => this.translate.instant('shared-subscription.delete-shared-subscriptions-title', {count});
    this.deleteEntitiesContent = () => this.translate.instant('shared-subscription.delete-shared-subscriptions-text');

    this.loadEntity = id => this.sharedSubscriptionService.getSharedSubscriptionById(id);
    this.saveEntity = entity => this.sharedSubscriptionService.saveSharedSubscription(entity);
    this.deleteEntity = id => this.sharedSubscriptionService.deleteSharedSubscription(id);

    this.entitiesFetchFunction = pageLink => this.sharedSubscriptionService.getSharedSubscriptions(pageLink);
  }
}
