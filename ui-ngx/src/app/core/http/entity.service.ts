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

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { EntityType } from '@shared/models/entity-type.models';
import { UserService } from '@core/http/user.service';
import { OAuth2Service } from '@core/http/oauth2.service';
import { concatMap, EMPTY, Observable, of, throwError, toArray } from 'rxjs';
import { RequestConfig } from '@core/http/http-utils';
import { BaseData } from '@shared/models/base-data';
import { PageLink } from '@shared/models/page/page-link';
import { Direction } from '@shared/models/page/sort-order';
import { expand, map } from 'rxjs/operators';
import { PageData } from '@shared/models/page/page-data';
import { DomainService } from '@core/http/domain.service';

@Injectable({
  providedIn: 'root'
})
export class EntityService {

  constructor(
    private http: HttpClient,
    private store: Store<AppState>,
    private domainService: DomainService,
    private oAuth2Service: OAuth2Service,
  ) {}

  private getEntitiesObservable(entityType: EntityType, entityIds: Array<string>,
                                config?: RequestConfig): Observable<Array<BaseData>> {
    let observable: Observable<Array<BaseData>>;
    switch (entityType) {
      case EntityType.OAUTH2_CLIENT:
        observable = this.oAuth2Service.findTenantOAuth2ClientInfosByIds(entityIds, config);
        break;
    }
    return observable;
  }

  public getEntities(entityType: EntityType, entityIds: Array<string>,
                     config?: RequestConfig): Observable<Array<BaseData>> {
    const entitiesObservable = this.getEntitiesObservable(entityType, entityIds, config);
    if (entitiesObservable) {
      return entitiesObservable;
    } else {
      return throwError(null);
    }
  }

  public getEntitiesByNameFilter(entityType: EntityType, entityNameFilter: string,
                                 pageSize: number, subType: string = '',
                                 config?: RequestConfig): Observable<Array<BaseData>> {
    const pageLink = new PageLink(pageSize, 0, entityNameFilter, {
      property: 'name',
      direction: Direction.ASC
    });
    if (pageSize === -1) { // all
      pageLink.pageSize = 100;
      return this.getEntitiesByPageLink(entityType, pageLink, subType, config).pipe(
        map((data) => data && data.length ? data : null)
      );
    } else {
      const entitiesObservable: Observable<PageData<BaseData>> =
        this.getEntitiesByPageLinkObservable(entityType, pageLink, subType, config);
      if (entitiesObservable) {
        return entitiesObservable.pipe(
          map((data) => data && data.data.length ? data.data : null)
        );
      } else {
        return of(null);
      }
    }
  }

  private getEntitiesByPageLink(entityType: EntityType, pageLink: PageLink, subType: string = '',
                                config?: RequestConfig): Observable<Array<BaseData>> {
    const entitiesObservable: Observable<PageData<BaseData>> =
      this.getEntitiesByPageLinkObservable(entityType, pageLink, subType, config);
    if (entitiesObservable) {
      return entitiesObservable.pipe(
        expand((data) => {
          if (data.hasNext) {
            pageLink.page += 1;
            return this.getEntitiesByPageLinkObservable(entityType, pageLink, subType, config);
          } else {
            return EMPTY;
          }
        }),
        map((data) => data.data),
        concatMap((data) => data),
        toArray()
      );
    } else {
      return of(null);
    }
  }

  private getEntitiesByPageLinkObservable(entityType: EntityType, pageLink: PageLink, subType: string = '',
                                          config?: RequestConfig): Observable<PageData<BaseData>> {
    let entitiesObservable: Observable<PageData<BaseData>>;
    switch (entityType) {
      case EntityType.OAUTH2_CLIENT:
        pageLink.sortOrder.property = 'title';
        entitiesObservable = this.oAuth2Service.findTenantOAuth2ClientInfos(pageLink, config);
        break;
      case EntityType.DOMAIN:
        pageLink.sortOrder.property = 'name';
        entitiesObservable = this.domainService.getDomainInfos(pageLink, config);
        break;
    }
    return entitiesObservable;
  }

}
