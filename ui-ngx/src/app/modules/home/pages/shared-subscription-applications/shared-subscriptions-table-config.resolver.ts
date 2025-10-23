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

import {
  copyContentActionCell,
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { SharedSubscription } from '@shared/models/shared-subscription.model';
import { SharedSubscriptionComponent } from '@home/pages/shared-subscription-applications/shared-subscription.component';
import { SharedSubscriptionService } from '@core/http/shared-subscription.service';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { Observable, of } from 'rxjs';
import { EntityAction } from '@home/models/entity/entity-component.models';
import { Injectable } from '@angular/core';
import { saveTopicsToLocalStorage } from '@core/utils';
import { DialogService } from '@core/services/dialog.service';
import { ConfigService } from '@core/http/config.service';

@Injectable()
export class SharedSubscriptionsTableConfigResolver {

  private readonly config: EntityTableConfig<SharedSubscription> = new EntityTableConfig<SharedSubscription>();

  constructor(private sharedSubscriptionService: SharedSubscriptionService,
              private translate: TranslateService,
              private router: Router,
              private dialogService: DialogService,
              private configService: ConfigService,
              private datePipe: DatePipe) {

    this.config.entityType = EntityType.SHARED_SUBSCRIPTION;
    this.config.entityComponent = SharedSubscriptionComponent;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.SHARED_SUBSCRIPTION);
    this.config.entityResources = entityTypeResources.get(EntityType.SHARED_SUBSCRIPTION);
    this.config.tableTitle = this.translate.instant('shared-subscription.application-shared-subscriptions');
    this.config.entityTitle = (entity) => entity ? entity.name : '';
    this.config.addDialogStyle = {width: '800px'};
    this.config.onEntityAction = action => this.onAction(action, this.config);

    this.config.columns.push(
      new DateEntityTableColumn<SharedSubscription>('createdTime', 'common.created-time', this.datePipe, '150px'),
      new EntityTableColumn<SharedSubscription>('name', 'shared-subscription.name', '250px'),
      new EntityTableColumn<SharedSubscription>('topicFilter', 'shared-subscription.topic-filter', '50%',
        undefined, () => undefined, true, () => ({}), () => undefined, false,
        copyContentActionCell('topicFilter', this.translate)),
      new EntityTableColumn<SharedSubscription>('partitions', 'shared-subscription.partitions', '100px')
    );

    this.config.addActionDescriptors.push(
      {
        name: this.translate.instant('shared-subscription.add'),
        icon: 'add',
        isEnabled: () => true,
        onAction: ($event) => this.config.getTable().addEntity($event)
      }
    );

    this.config.headerActionDescriptors.push(
      {
        name: this.translate.instant('shared-subscription.create-kafka-topics'),
        icon: 'playlist_add',
        isEnabled: () => true,
        onAction: ($event) => this.createKafkaTopicsForAppSharedSubscriptions($event)
      },
      {
        name: this.translate.instant('shared-subscription.delete-kafka-topics'),
        icon: 'playlist_remove',
        isEnabled: () => this.configService.brokerConfig.allowKafkaTopicDeletion,
        onAction: ($event) => this.deleteKafkaTopicsForAppSharedSubscriptions($event)
      }
    );

    this.config.deleteEntityTitle = entity => this.translate.instant('shared-subscription.delete-shared-subscription-title',
      { name: entity.name });
    this.config.deleteEntityContent = () => this.translate.instant('shared-subscription.delete-shared-subscription-text');
    this.config.deleteEntitiesTitle = count => this.translate.instant('shared-subscription.delete-shared-subscriptions-title', {count});
    this.config.deleteEntitiesContent = () => this.translate.instant('shared-subscription.delete-shared-subscriptions-text');

    this.config.loadEntity = id => this.sharedSubscriptionService.getSharedSubscriptionById(id);
    this.config.saveEntity = entity => this.saveSharedSubscription(entity);
    this.config.deleteEntity = id => this.sharedSubscriptionService.deleteSharedSubscription(id);

    this.config.entitiesFetchFunction = pageLink => this.sharedSubscriptionService.getSharedSubscriptions(pageLink);
  }

  resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<EntityTableConfig<SharedSubscription>> {
    return of(this.config);
  }

  onAction(action: EntityAction<SharedSubscription>, config: EntityTableConfig<SharedSubscription>): boolean {
    switch (action.action) {
      case 'open':
        this.openSharedSubscription(action.event, action.entity, config);
        return true;
    }
    return false;
  }

  private openSharedSubscription($event: Event, subscription: SharedSubscription, config: EntityTableConfig<SharedSubscription>) {
    if ($event) {
      $event.stopPropagation();
    }
    const url = this.router.createUrlTree([subscription.id], {relativeTo: config.getActivatedRoute()});
    this.router.navigateByUrl(url);
  }

  private saveSharedSubscription(entity: SharedSubscription): Observable<SharedSubscription> {
    saveTopicsToLocalStorage(entity.topicFilter);
    return this.sharedSubscriptionService.saveSharedSubscription(entity);
  }

  private createKafkaTopicsForAppSharedSubscriptions($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('shared-subscription.create-kafka-topics-title'),
      this.translate.instant('shared-subscription.create-kafka-topics-text'),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        this.sharedSubscriptionService.createKafkaTopicsForAppSharedSubscriptions().subscribe();
      }
    });
  }

  private deleteKafkaTopicsForAppSharedSubscriptions($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('shared-subscription.delete-kafka-topics-title'),
      this.translate.instant('shared-subscription.delete-kafka-topics-text'),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        this.sharedSubscriptionService.deleteKafkaTopicsForAppSharedSubscriptions().subscribe();
      }
    });
  }
}
