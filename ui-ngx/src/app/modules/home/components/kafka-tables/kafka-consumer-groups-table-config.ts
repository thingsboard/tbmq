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
  EntityTableColumn,
  EntityTableConfig,
  GroupActionDescriptor
} from '@home/models/entity/entities-table-config.models';
import { TimePageLink } from '@shared/models/page/page-link';
import { forkJoin, Observable } from 'rxjs';
import { PageData } from '@shared/models/page/page-data';
import { KafkaConsumerGroup } from '@shared/models/kafka.model';
import { KafkaService } from '@core/http/kafka.service';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from '@core/services/dialog.service';

export class KafkaConsumerGroupsTableConfig extends EntityTableConfig<KafkaConsumerGroup, TimePageLink> {

  constructor(private kafkaService: KafkaService,
              private translate: TranslateService,
              private dialogService: DialogService,
              public entityId: string = null) {
    super();
    this.entityType = EntityType.KAFKA_CONSUMER_GROUP;
    this.entityTranslations = entityTypeTranslations.get(EntityType.KAFKA_CONSUMER_GROUP);
    this.entityResources = entityTypeResources.get(EntityType.KAFKA_CONSUMER_GROUP);
    this.tableTitle = this.translate.instant('kafka.consumer-groups')
    this.entityComponent = null;
    this.detailsPanelEnabled = false;
    this.addEnabled = false;
    this.entitiesDeleteEnabled = false;
    // this.entitySelectionEnabled = (entity) => entity.members === 0;

    this.entitiesFetchFunction = pageLink => this.fetchKafkaConsumerGroups(pageLink);
    this.groupActionDescriptors = this.configureGroupActions();
    this.cellActionDescriptors = this.configureCellActions();

    this.columns.push(
      new EntityTableColumn<KafkaConsumerGroup>('groupId', 'kafka.id', '70%'),
      new EntityTableColumn<KafkaConsumerGroup>('state', 'kafka.state', '10%'),
      new EntityTableColumn<KafkaConsumerGroup>('members', 'kafka.members', '10%',
        entity => entity.members.toString()),
      new EntityTableColumn<KafkaConsumerGroup>('lag', 'kafka.lag', '10%',
          entity => entity.lag)
    );
  }

  private fetchKafkaConsumerGroups(pageLink: TimePageLink): Observable<PageData<KafkaConsumerGroup>> {
    return this.kafkaService.getKafkaConsumerGroups(pageLink);
  }

  private configureCellActions(): Array<CellActionDescriptor<KafkaConsumerGroup>> {
    const actions: Array<CellActionDescriptor<KafkaConsumerGroup>> = [];
    actions.push(
      {
        name: this.translate.instant('kafka.delete-consumer-group'),
        icon: 'mdi:trash-can-outline',
        isEnabled: (entity) => (entity.members === 0),
        onAction: ($event, entity) => this.removeGroup($event, entity)
      }
    );
    return actions;
  }

  private configureGroupActions(): Array<GroupActionDescriptor<KafkaConsumerGroup>> {
    const actions: Array<GroupActionDescriptor<KafkaConsumerGroup>> = [];
    actions.push(
      {
        name: this.translate.instant('kafka.delete-consumer-groups'),
        icon: 'mdi:trash-can-outline',
        isEnabled: true,
        onAction: ($event, entities) =>
          this.removeGroups($event, entities.filter(entity => entity.members === 0), entities)
      }
    );
    return actions;
  }

  private removeGroup($event: Event, group: KafkaConsumerGroup) {
    if ($event) {
      $event.stopPropagation();
    }
    const title = this.translate.instant('kafka.delete-consumer-group-title', {id: group.groupId});
    const content = this.translate.instant('kafka.delete-consumer-group-text');
    this.dialogService.confirm(
      title,
      content,
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((res) => {
        if (res) {
          this.kafkaService.deleteConsumerGroup(group.groupId).subscribe(() => this.getTable().updateData());
        }
      }
    );
  }

  private removeGroups($event: Event, filteredGroups: Array<KafkaConsumerGroup>, groups: Array<KafkaConsumerGroup>) {
    if ($event) {
      $event.stopPropagation();
    }
    if (!filteredGroups.length) {
      const title = this.translate.instant('kafka.selected-consumer-groups', {count: groups.length});
      const content = this.translate.instant('kafka.selected-consumer-groups-remove-with-members');
      this.dialogService.alert(
        title,
        content).subscribe();
    } else {
      this.dialogService.confirm(
        this.translate.instant('kafka.delete-consumer-groups-title', {count: filteredGroups.length}),
        this.translate.instant('kafka.delete-consumer-groups-text'),
        this.translate.instant('action.no'),
        this.translate.instant('action.yes'),
        true
      ).subscribe((res) => {
          if (res) {
            const tasks: Observable<any>[] = [];
            filteredGroups.forEach(
              (group) => {
                tasks.push(this.kafkaService.deleteConsumerGroup(group.groupId));
              }
            );
            forkJoin(tasks).subscribe(() => this.getTable().updateData());
          }
        }
      );
    }
  }
}
