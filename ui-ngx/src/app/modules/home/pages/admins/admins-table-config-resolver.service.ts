///
/// Copyright © 2016-2022 The Thingsboard Authors
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
import { User } from '@shared/models/user.model';
import { AdminComponent } from '@home/pages/admins/admin.component';
import { AdminService } from '@core/http/admin.service';
import { AuthorityTranslationMap } from '@shared/models/authority.enum';

@Injectable()
export class AdminsTableConfigResolver implements Resolve<EntityTableConfig<User>> {

  private readonly config: EntityTableConfig<User> = new EntityTableConfig<User>();

  private readonly authorityTranslationMap = AuthorityTranslationMap;

  constructor(private store: Store<AppState>,
              private dialogService: DialogService,
              private adminService: AdminService,
              private translate: TranslateService,
              private datePipe: DatePipe) {

    this.config.entityType = EntityType.MQTT_CLIENT_CREDENTIALS;
    this.config.entityComponent = AdminComponent;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.USER);
    this.config.entityResources = entityTypeResources.get(EntityType.USER);
    this.config.tableTitle = this.translate.instant('user.users');
    this.config.entitiesDeleteEnabled = false;
    this.config.entityTitle = (user) => user ? user.email : '';

    this.config.columns.push(
      new DateEntityTableColumn<User>('createdTime', 'common.created-time', this.datePipe, '150px'),
      new EntityTableColumn<User>('email', 'user.email', '25%'),
      new EntityTableColumn<User>('authority', 'user.role', '25%',
        entity => this.translate.instant(this.authorityTranslationMap.get(entity.authority))),
      new EntityTableColumn<User>('firstName', 'user.first-name', '25%'),
      new EntityTableColumn<User>('lastName', 'user.last-name', '25%')
    );

    this.config.addActionDescriptors.push(
      {
        name: this.translate.instant('user.add'),
        icon: 'add',
        isEnabled: () => true,
        onAction: ($event) => this.config.table.addEntity($event)
      }
    );

    this.config.deleteEntityTitle = user => this.translate.instant('user.delete-user-title',
      { userEmail: user.email });
    this.config.deleteEntityContent = () => this.translate.instant('user.delete-user-text');
    this.config.deleteEntitiesTitle = count => this.translate.instant('user.delete-users-title', {count});
    this.config.deleteEntitiesContent = () => this.translate.instant('user.delete-users-text');
    this.config.loadEntity = id => this.loadEntity(id);
    this.config.saveEntity = user => this.adminService.saveAdmin(user);
    this.config.deleteEntity = id => this.deleteEntity(id);
  }

  resolve(): EntityTableConfig<User> {
    this.config.entitiesFetchFunction = pageLink => this.adminService.getAdmins(pageLink);
    return this.config;
  }

  loadEntity(id) {
    return this.adminService.getAdmin(id);
  }

  deleteEntity(id) {
    return this.adminService.deleteAdmin(id);
  }
}
