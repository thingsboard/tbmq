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
  DateEntityTableColumn,
  EntityColumn,
  EntityTableColumn,
  EntityTableConfig,
  HeaderActionDescriptor
} from '@home/models/entity/entities-table-config.models';
import {
  getIntegrationHelpLink,
  Integration,
  IntegrationInfo,
  integrationTypeInfoMap
} from '@shared/models/integration.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { UtilsService } from '@core/services/utils.service';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { IntegrationService } from '@core/http/integration.service';
import { DialogService } from '@core/services/dialog.service';
import { EntityType, entityTypeTranslations } from '@shared/models/entity-type.models';
import { IntegrationComponent } from '@home/pages/integration/integration.component';
import { IntegrationTabsComponent } from '@home/pages/integration/integration-tabs.component';
import { Observable } from 'rxjs';
import { AddEntityDialogData, EntityAction } from '@home/models/entity/entity-component.models';
import { PageLink } from '@shared/models/page/page-link';
import { IntegrationWizardDialogComponent } from '@home/components/wizard/integration-wizard-dialog.component';

export class IntegrationsTableConfig extends EntityTableConfig<Integration, PageLink, IntegrationInfo> {

  constructor(private integrationService: IntegrationService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private router: Router,
              private utils: UtilsService,
              private dialogService: DialogService,
              private dialog: MatDialog) {
    super();

    this.entityType = EntityType.INTEGRATION;
    this.entityComponent = IntegrationComponent;
    this.entityTabsComponent = IntegrationTabsComponent;
    this.entityTranslations = entityTypeTranslations.get(EntityType.INTEGRATION);
    this.entityResources = {
      helpLinkId: null,
      helpLinkIdForEntity(entity: Integration): string {
        return getIntegrationHelpLink(entity);
      }
    };
    this.addDialogStyle = {width: '800px'};

    this.componentsData = {};

    this.deleteEntityTitle = integration =>
      this.translate.instant('integration.delete-integration-title', { integrationName: integration.name });
    this.deleteEntityContent = () => this.translate.instant('integration.delete-integration-text');
    this.deleteEntitiesTitle = count => this.translate.instant('integration.delete-integrations-title', {count});
    this.deleteEntitiesContent = () => this.translate.instant('integration.delete-integrations-text');

    this.tableTitle = this.translate.instant('integration.integrations');

    this.columns = this.configureEntityTableColumns();
    this.addActionDescriptors = this.configureAddActions();
    this.cellActionDescriptors = this.configureCellActions();

    this.loadEntity = id => this.integrationService.getIntegration(id);
    this.entitiesFetchFunction = pageLink => this.integrationService.getIntegrationsInfo(pageLink);
    this.saveEntity = integration => this.integrationService.saveIntegration(integration);
    this.deleteEntity = id => this.integrationService.deleteIntegration(id);

    this.onEntityAction = action => this.onIntegrationAction(action);

    this.addEntity = () => this.addIntegration();
  }

  private configureEntityTableColumns(): Array<EntityColumn<IntegrationInfo>> {
    const columns: Array<EntityColumn<IntegrationInfo>> = [];

    this.entityTitle = (integration) => integration ? integration.name : '';

    columns.push(
      new DateEntityTableColumn<IntegrationInfo>('createdTime', 'common.created-time', this.datePipe, '15%'),
      new EntityTableColumn<IntegrationInfo>('name', 'integration.name', '15%'),
      new EntityTableColumn<IntegrationInfo>('type', 'integration.type', '15%', (integration) => this.translate.instant(integrationTypeInfoMap.get(integration.type).name)),
      new EntityTableColumn<IntegrationInfo>('status', 'integration.status.status', '55%',
        integration => this.integrationStatus(integration),
        integration => this.integrationStatusStyle(integration), false),
    );
    return columns;
  }

  private configureAddActions(): Array<HeaderActionDescriptor> {
    const actions: Array<HeaderActionDescriptor> = [
      {
        name: this.translate.instant('integration.add'),
        icon: 'add',
        isEnabled: () => true,
        onAction: ($event) => this.getTable().addEntity($event)
      }
    ];
    return actions;
  }

  private configureCellActions(): Array<CellActionDescriptor<Integration>> {
    const actions: Array<CellActionDescriptor<Integration>> = [];
    actions.push(
      {
        name: this.translate.instant('integration.enable'),
        nameFunction: (entity) => entity.enabled ? this.translate.instant('integration.disable') : this.translate.instant('integration.enable'),
        iconFunction: (entity) => entity.enabled ? 'mdi:toggle-switch' : 'mdi:toggle-switch-off-outline',
        isEnabled: () => true,
        onAction: ($event, entity) => this.toggleIntegration($event, entity)
      },
      {
        name: this.translate.instant('integration.restart'),
        icon: 'mdi:restart',
        isEnabled: (entity) => entity.enabled,
        onAction: ($event, entity) => this.restartIntegration($event, entity)
      }
    );
    return actions;
  }

  openIntegration($event: Event, integration: Integration) {
    if ($event) {
      $event.stopPropagation();
    }
    this.router.navigateByUrl(`integrations/${integration.id}`);
  }

  restartIntegration($event: Event, integration: Integration) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('integration.restart-title', {name: integration.name}),
      this.translate.instant('integration.restart-text'),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        this.integrationService.restartIntegration(integration.id)
          .subscribe(() => this.integrationService.onRestartIntegration())
      }
    });
  }

  toggleIntegration($event: Event, integration: Integration) {
    if ($event) {
      $event.stopPropagation();
    }
    const integrationValue: Integration = JSON.parse(JSON.stringify(integration));
    integrationValue.enabled = !integrationValue.enabled;
    this.integrationService.saveIntegration(integrationValue).subscribe(() => this.getTable().updateData());
  }

  onIntegrationAction(action: EntityAction<Integration>): boolean {
    switch (action.action) {
      case 'open':
        this.openIntegration(action.event, action.entity);
        return true;
      case 'restart':
        this.restartIntegration(action.event, action.entity);
        return true;
    }
    return false;
  }

  private addIntegration(): Observable<Integration> {
    return this.dialog.open<IntegrationWizardDialogComponent, AddEntityDialogData<IntegrationInfo>,
      Integration>(IntegrationWizardDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        entitiesTableConfig: this as any,
      }
    }).afterClosed();
  }

  private integrationStatus(integration: IntegrationInfo): string {
    let translateKey = 'integration.status.active';
    let backgroundColor = 'rgba(25, 128, 56, 0.08)';
    if (!integration.enabled) {
      translateKey = 'integration.status.disabled';
      backgroundColor = 'rgba(0, 0, 0, 0.08)';
    } else if (!integration.status) {
      translateKey = 'integration.status.pending';
      backgroundColor = 'rgba(212, 125, 24, 0.08)';
    } else if (!integration.status.success) {
      translateKey = 'integration.status.failed';
      backgroundColor = 'rgba(209, 39, 48, 0.08)';
    }
    return `<div class="status" style="border-radius: 16px; height: 32px; line-height: 32px; padding: 0 12px; width: fit-content; background-color: ${backgroundColor}">
                ${this.translate.instant(translateKey)}
            </div>`;
  }

  private integrationStatusStyle(integration: IntegrationInfo): object {
    const styleObj = {
      fontSize: '14px',
      color: '#198038',
      cursor: 'pointer'
    };
    if (!integration.enabled) {
      styleObj.color = 'rgba(0, 0, 0, 0.54)';
    } else if (!integration.status) {
      styleObj.color = '#D47D18';
    } else if (!integration.status.success) {
      styleObj.color = '#d12730';
    }
    return styleObj;
  }
}
