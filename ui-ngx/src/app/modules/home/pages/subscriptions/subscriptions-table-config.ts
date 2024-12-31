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
  CellActionDescriptor,
  CellActionDescriptorType,
  checkBoxCell,
  EntityTableColumn,
  EntityTableConfig,
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { DialogService } from '@core/services/dialog.service';
import { Observable } from 'rxjs';
import { PageLink, TimePageLink } from '@shared/models/page/page-link';
import { PageData } from '@shared/models/page/page-data';
import { deepClone } from '@core/utils';
import { ActivatedRoute, Router } from '@angular/router';
import { Direction } from '@shared/models/page/sort-order';
import {
  ClientSubscription,
  ClientSubscriptionFilterConfig,
  ClientSubscriptionsQuery
} from '@shared/models/subscription.model';
import { SubscriptionService } from '@core/http/subscription.service';
import { SubscriptionsTableHeaderComponent } from '@home/pages/subscriptions/subscriptions-table-header.component';
import { mqttQoSTypes } from '@shared/models/session.model';
import { RhOptions } from '@shared/models/ws-client.model';
import { ClientSessionService } from '@core/http/client-session.service';

export class SubscriptionsTableConfig extends EntityTableConfig<ClientSubscription, TimePageLink> {

  subscriptionsFilterConfig: ClientSubscriptionFilterConfig = {};

  private rhOptions = RhOptions;

  constructor(private dialogService: DialogService,
              private subscriptionService: SubscriptionService,
              private clientSessionService: ClientSessionService,
              private translate: TranslateService,
              public entityId: string = null,
              private route: ActivatedRoute,
              private router: Router) {
    super();

    this.entityType = EntityType.SUBSCRIPTION;
    this.headerComponent = SubscriptionsTableHeaderComponent;
    this.entityComponent = null;

    this.detailsPanelEnabled = false;
    this.entityTranslations = entityTypeTranslations.get(EntityType.SUBSCRIPTION);
    this.entityResources = entityTypeResources.get(EntityType.SUBSCRIPTION);
    this.tableTitle = this.translate.instant('subscription.subscriptions');
    this.entitiesDeleteEnabled = false;
    this.addEnabled = false;
    this.defaultSortOrder = {property: 'clientId', direction: Direction.DESC};

    this.columns.push(
      new EntityTableColumn<ClientSubscription>('clientId', 'mqtt-client.client-id', '50%',
        undefined, () => undefined, true, () => ({}), () => undefined, false,
        {
          name: this.translate.instant('action.copy'),
          nameFunction: (entity) => this.translate.instant('action.copy') + ' ' + entity.clientId,
          icon: 'content_copy',
          style: {
            padding: '0px',
            'font-size': '16px',
            'line-height': '16px',
            height: '16px',
            color: 'rgba(0,0,0,.87)'
          },
          isEnabled: () => true,
          onAction: ($event, entity) => entity.clientId,
          type: CellActionDescriptorType.COPY_BUTTON
        }),
      new EntityTableColumn<ClientSubscription>('topicFilter', 'subscription.topic-filter', '50%',
        entity => entity.subscription.topicFilter, () => undefined, true, () => ({}), () => undefined, false,
        {
          name: this.translate.instant('action.copy'),
          nameFunction: (entity) => this.translate.instant('action.copy') + ' ' + entity.subscription.topicFilter,
          icon: 'content_copy',
          style: {
            padding: '0px',
            'font-size': '16px',
            'line-height': '16px',
            height: '16px',
            color: 'rgba(0,0,0,.87)'
          },
          isEnabled: () => true,
          onAction: ($event, entity) => entity.subscription.topicFilter,
          type: CellActionDescriptorType.COPY_BUTTON
        }),
      new EntityTableColumn<ClientSubscription>('qos', 'mqtt-client-session.qos', '120px', entity => {
        const qos = mqttQoSTypes.find(el => el.value === entity.subscription.qos).name;
        return this.translate.instant(qos);
      }),
      new EntityTableColumn<ClientSubscription>('noLocal', 'subscription.nl', '120px', entity => checkBoxCell(entity.subscription?.options?.noLocal)),
      new EntityTableColumn<ClientSubscription>('retainAsPublish', 'subscription.rap', '120px', entity => checkBoxCell(entity.subscription?.options?.retainAsPublish)),
      new EntityTableColumn<ClientSubscription>('retainHandling', 'subscription.rh', '120px', entity => entity.subscription?.options?.retainHandling.toString(),
        undefined, undefined, undefined, entity => {
          const rh = this.rhOptions.find(el => el.value === entity.subscription?.options.retainHandling).name;
          return this.translate.instant(rh);
        }),
      new EntityTableColumn<ClientSubscription>('subscriptionId', 'subscription.subscription-id', '120px',
          entity => entity.subscription.subscriptionId ? entity.subscription.subscriptionId.toString() : ''),
    );

    this.headerActionDescriptors.push({
      name: this.translate.instant('subscription.clear-empty-subscription-nodes'),
      icon: 'delete_forever',
      isEnabled: () => true,
      onAction: ($event) => {
        this.clearEmptySubscriptionNodes($event);
      }
    });

    this.cellActionDescriptors = this.configureCellActions();
    this.entitiesFetchFunction = pageLink => this.fetchAllClientSubscriptions(pageLink);
  }

  private fetchAllClientSubscriptions(pageLink: PageLink): Observable<PageData<ClientSubscription>> {
    const routerQueryParams: ClientSubscriptionFilterConfig = this.route.snapshot.queryParams;
    if (routerQueryParams) {
      const queryParams = deepClone(routerQueryParams);
      let replaceUrl = false;
      if (routerQueryParams?.clientId) {
        this.subscriptionsFilterConfig.clientId = routerQueryParams?.clientId;
        delete queryParams.clientId;
      }
      if (routerQueryParams?.topicFilter) {
        this.subscriptionsFilterConfig.topicFilter = routerQueryParams?.topicFilter;
        delete queryParams.topicFilter;
      }
      if (routerQueryParams?.qosList) {
        this.subscriptionsFilterConfig.qosList = routerQueryParams?.qosList;
        delete queryParams.qosList;
      }
      if (routerQueryParams?.noLocalList) {
        this.subscriptionsFilterConfig.noLocalList = routerQueryParams?.noLocalList;
        delete queryParams.noLocalList;
      }
      if (routerQueryParams?.retainAsPublishList) {
        this.subscriptionsFilterConfig.retainAsPublishList = routerQueryParams?.retainAsPublishList;
        delete queryParams.retainAsPublishList;
      }
      if (routerQueryParams?.retainHandlingList) {
        this.subscriptionsFilterConfig.retainHandlingList = routerQueryParams?.retainHandlingList;
        delete queryParams.retainHandlingList;
      }
      if (routerQueryParams?.subscriptionId) {
        this.subscriptionsFilterConfig.subscriptionId = routerQueryParams?.subscriptionId;
        delete queryParams.subscriptionId;
      }
      if (replaceUrl) {
        this.router.navigate([], {
          relativeTo: this.route,
          queryParams,
          queryParamsHandling: '',
          replaceUrl: true
        });
      }
    }
    const filter = this.resolveSubscriptionsFilter(this.subscriptionsFilterConfig);
    const query = new ClientSubscriptionsQuery(pageLink, filter);
    return this.subscriptionService.getClientSubscriptionsV2(query);
  }

  private resolveSubscriptionsFilter(subscriptionsFilterConfig?: ClientSubscriptionFilterConfig): ClientSubscriptionFilterConfig {
    const filter: ClientSubscriptionFilterConfig = {};
    if (subscriptionsFilterConfig) {
      filter.clientId = subscriptionsFilterConfig.clientId;
      filter.topicFilter = subscriptionsFilterConfig.topicFilter;
      filter.qosList = subscriptionsFilterConfig.qosList;
      filter.noLocalList = subscriptionsFilterConfig.noLocalList;
      filter.retainAsPublishList = subscriptionsFilterConfig.retainAsPublishList;
      filter.retainHandlingList = subscriptionsFilterConfig.retainHandlingList;
      filter.subscriptionId = subscriptionsFilterConfig.subscriptionId;
    }
    return filter;
  }

  private clearEmptySubscriptionNodes($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('subscription.clear-empty-subscription-nodes-title'),
      this.translate.instant('subscription.clear-empty-subscription-nodes-text'),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        this.subscriptionService.clearEmptySubscriptionNodes().subscribe(
          () => {
            this.getTable().updateData();
          }
        );
      }
    });
  }

  private configureCellActions(): Array<CellActionDescriptor<ClientSubscription>> {
    const actions: Array<CellActionDescriptor<ClientSubscription>> = [];
    actions.push(
      {
        name: this.translate.instant('mqtt-client-session.details'),
        icon: 'mdi:book-multiple',
        isEnabled: () => true,
        onAction: ($event, entity) => this.showSessionDetails($event, entity.clientId)
      },
    );
    return actions;
  }

  private showSessionDetails($event: Event, clientId: string) {
    this.clientSessionService.openSessionDetailsDialog($event, clientId).subscribe(
      (dialog) => {
        dialog.afterClosed().subscribe((res) => {
          if (res) {
            setTimeout(() => {
              this.getTable().updateData();
            }, 500)
          }
        });
      }
    );
  }
}
