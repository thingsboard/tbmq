///
/// Copyright © 2016-2024 The Thingsboard Authors
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
  CellActionDescriptorType,
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { User } from '@shared/models/user.model';
import { UserComponent } from '@home/pages/users/user.component';
import { UserService } from '@core/http/user.service';
import { AuthorityTranslationMap } from '@shared/models/authority.enum';
import { getCurrentAuthUser } from '@core/auth/auth.selectors';

export class UserTableConfig extends EntityTableConfig<User> {

  private readonly authorityTranslationMap = AuthorityTranslationMap;

  constructor(private store: Store<AppState>,
              private adminService: UserService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              public entityId: string = null) {
    super();

    this.entityType = EntityType.USER;
    this.entityComponent = UserComponent;
    this.entityTranslations = entityTypeTranslations.get(EntityType.USER);
    this.entityResources = entityTypeResources.get(EntityType.USER);
    this.tableTitle = this.translate.instant('user.users');
    this.entitySelectionEnabled = (user) => user.id !== getCurrentAuthUser(this.store).userId;
    this.deleteEnabled = (user) => user ? user.id !== getCurrentAuthUser(this.store).userId : true;
    this.entityTitle = (user) => user ? user.email : '';
    this.addDialogStyle = {height: '600px'};

    this.columns.push(
      new DateEntityTableColumn<User>('createdTime', 'common.created-time', this.datePipe, '150px'),
      new EntityTableColumn<User>('email', 'user.email', '25%',
        undefined, () => undefined,
        true, () => ({}), () => undefined, false,
        {
          name: this.translate.instant('action.copy'),
          icon: 'content_copy',
          style: {
            padding: '0px',
            'font-size': '16px',
            'line-height': '16px',
            height: '16px',
            color: 'rgba(0,0,0,.87)'
          },
          isEnabled: (entity) => !!entity.email?.length,
          onAction: ($event, entity) => entity.email,
          type: CellActionDescriptorType.COPY_BUTTON
        }),
      new EntityTableColumn<User>('authority', 'user.role', '25%',
        entity => this.translate.instant(this.authorityTranslationMap.get(entity.authority)),
        undefined, false),
      new EntityTableColumn<User>('firstName', 'user.first-name', '25%'),
      new EntityTableColumn<User>('lastName', 'user.last-name', '25%')
    );

    this.addActionDescriptors.push(
      {
        name: this.translate.instant('user.add'),
        icon: 'add',
        isEnabled: () => true,
        onAction: ($event) => this.getTable().addEntity($event)
      }
    );

    this.deleteEntityTitle = user => this.translate.instant('user.delete-user-title',
      {userEmail: user.email});
    this.deleteEntityContent = () => this.translate.instant('user.delete-user-text');
    this.deleteEntitiesTitle = count => this.translate.instant('user.delete-users-title', {count});
    this.deleteEntitiesContent = () => this.translate.instant('user.delete-users-text');
    this.loadEntity = id => this.adminService.getUser(id);
    this.saveEntity = user => this.adminService.saveUser(user);
    this.deleteEntity = id => this.adminService.deleteUser(id);

    this.entitiesFetchFunction = pageLink => this.adminService.getUsers(pageLink)
  }
}
