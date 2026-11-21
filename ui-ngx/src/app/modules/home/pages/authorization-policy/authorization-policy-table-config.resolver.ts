///
/// Copyright © 2016-2026 The Thingsboard Authors
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
import { ActivatedRouteSnapshot, Resolve, RouterStateSnapshot } from '@angular/router';
import { Observable, of } from 'rxjs';
import { DatePipe } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { AuthorizationPolicyService } from '@core/http/authorization-policy.service';
import { AuthorizationPolicy } from '@shared/models/credentials.model';
import { DateEntityTableColumn, EntityTableColumn, EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { AuthorizationPolicyComponent } from './authorization-policy.component';

@Injectable()
export class AuthorizationPolicyTableConfigResolver implements Resolve<EntityTableConfig<AuthorizationPolicy>> {

  private readonly config = new EntityTableConfig<AuthorizationPolicy>();

  constructor(private policyService: AuthorizationPolicyService,
              private translate: TranslateService,
              private datePipe: DatePipe) {
    this.config.entityType = EntityType.AUTHORIZATION_POLICY;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.AUTHORIZATION_POLICY);
    this.config.entityResources = entityTypeResources.get(EntityType.AUTHORIZATION_POLICY);
    this.config.tableTitle = this.translate.instant('authorization-policy.policies');
    this.config.entityTitle = entity => entity?.name || '';
    this.config.entityComponent = AuthorizationPolicyComponent;
    this.config.columns.push(
      new DateEntityTableColumn<AuthorizationPolicy>('createdTime', 'common.created-time', this.datePipe, '150px'),
      new EntityTableColumn<AuthorizationPolicy>('name', 'authorization-policy.name', '100%', entity => entity.name)
    );
    this.config.deleteEntityTitle = entity => this.translate.instant('authorization-policy.delete-title', {name: entity.name});
    this.config.deleteEntityContent = () => this.translate.instant('authorization-policy.delete-text');
    this.config.deleteEntitiesTitle = count => this.translate.instant('authorization-policy.delete-many-title', {count});
    this.config.deleteEntitiesContent = () => this.translate.instant('authorization-policy.delete-text');
    this.config.loadEntity = id => this.policyService.getPolicy(id);
    this.config.saveEntity = entity => this.policyService.savePolicy(entity);
    this.config.deleteEntity = id => this.policyService.deletePolicy(id);
    this.config.entitiesFetchFunction = pageLink => this.policyService.getPolicies(pageLink);
  }

  resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<EntityTableConfig<AuthorizationPolicy>> {
    return of(this.config);
  }
}
