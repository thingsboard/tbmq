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
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { User } from '@shared/models/user.model';
import { UserComponent } from '@home/pages/users/user.component';
import { UserService } from '@core/http/user.service';
import { AuthorityTranslationMap } from '@shared/models/authority.enum';
import { getCurrentAuthUser } from '@core/auth/auth.selectors';
import { EntityAction } from '@home/models/entity/entity-component.models';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { Injectable } from '@angular/core';
import { mergeMap, Observable, of } from 'rxjs';
import { AuthService } from '@core/http/auth.service';

@Injectable()
export class UsersTableConfigResolver {

  private readonly authorityTranslationMap = AuthorityTranslationMap;
  private readonly config: EntityTableConfig<User> = new EntityTableConfig<User>();
  private readonly currentUserId = () => getCurrentAuthUser(this.store)?.userId;

  constructor(private store: Store<AppState>,
              private adminService: UserService,
              private authService: AuthService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private router: Router) {

    this.config.entityType = EntityType.USER;
    this.config.entityComponent = UserComponent;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.USER);
    this.config.entityResources = entityTypeResources.get(EntityType.USER);
    this.config.tableTitle = this.translate.instant('user.users');
    this.config.entitySelectionEnabled = (user) => user.id !== this.currentUserId();
    this.config.deleteEnabled = (user) => user ? user.id !== this.currentUserId() : true;
    this.config.entityTitle = (user) => user ? user.email : '';
    this.config.addDialogStyle = {height: '600px'};
    this.config.onEntityAction = action => this.onAction(action, this.config);

    this.config.columns.push(
      new DateEntityTableColumn<User>('createdTime', 'common.created-time', this.datePipe, '150px'),
      new EntityTableColumn<User>('email', 'user.email', '40%',
        entity => entity.email, () => undefined, true, () => ({}), () => undefined, false,
        copyContentActionCell('email', this.translate)),
      new EntityTableColumn<User>('authority', 'user.role', '100px',
        entity => this.translate.instant(this.authorityTranslationMap.get(entity.authority)),
        undefined, false),
      new EntityTableColumn<User>('firstName', 'user.first-name', '150px'),
      new EntityTableColumn<User>('lastName', 'user.last-name', '150px')
    );

    this.config.addActionDescriptors.push(
      {
        name: this.translate.instant('user.add'),
        icon: 'add',
        isEnabled: () => true,
        onAction: ($event) => this.config.getTable().addEntity($event)
      }
    );

    this.config.deleteEntityTitle = user => this.translate.instant('user.delete-user-title',
      {userEmail: user.email});
    this.config.deleteEntityContent = () => this.translate.instant('user.delete-user-text');
    this.config.deleteEntitiesTitle = count => this.translate.instant('user.delete-users-title', {count});
    this.config.deleteEntitiesContent = () => this.translate.instant('user.delete-users-text');
    this.config.loadEntity = id => this.adminService.getUser(id);
    this.config.saveEntity = user => this.adminService.saveUser(user);
    this.config.deleteEntity = id => this.adminService.deleteUser(id);

    this.config.entitiesFetchFunction = pageLink => this.adminService.getUsers(pageLink);
  }

  resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<EntityTableConfig<User>> {
    return this.authService.loadIsUserTokenAccessEnabled(getCurrentAuthUser(this.store)).pipe(
      mergeMap(isTokenAccessEnabled => {
        if (isTokenAccessEnabled) {
          this.config.cellActionDescriptors = [];
          this.config.cellActionDescriptors.push(
            {
              name: this.translate.instant('login.login-as-user'),
              icon: 'mdi:login',
              isEnabled: (entity) => entity.id !== this.currentUserId(),
              onAction: ($event, entity) => this.loginAsUser($event, entity)
            }
          );
        }
        return of(this.config);
      })
    )
  }

  onAction(action: EntityAction<User>, config: EntityTableConfig<User>): boolean {
    switch (action.action) {
      case 'open':
        this.openUser(action.event, action.entity, config);
        return true;
      case 'loginAsUser':
        this.loginAsUser(action.event, action.entity);
        return true;
    }
    return false;
  }

  private openUser($event: Event, user: User, config: EntityTableConfig<User>) {
    if ($event) {
      $event.stopPropagation();
    }
    const url = this.router.createUrlTree([user.id], {relativeTo: config.getActivatedRoute()});
    this.router.navigateByUrl(url);
  }

  private loginAsUser($event: Event, user: User) {
    if ($event) {
      $event.stopPropagation();
    }
    this.authService.loginAsUser(user.id).subscribe();
  }
}
