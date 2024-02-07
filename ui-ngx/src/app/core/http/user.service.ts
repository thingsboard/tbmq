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

import { Injectable } from '@angular/core';
import { defaultHttpOptionsFromConfig, RequestConfig } from './http-utils';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { User } from '@shared/models/user.model';
import { PageLink } from '@shared/models/page/page-link';
import { PageData } from '@shared/models/page/page-data';
import { DEFAULT_PASSWORD } from '@core/auth/auth.models';

@Injectable({
  providedIn: 'root'
})
export class UserService {

  constructor(private http: HttpClient) {
  }

  public getUser(userId: string, config?: RequestConfig): Observable<User> {
    return this.http.get<User>(`/api/admin/user/${userId}`, defaultHttpOptionsFromConfig(config));
  }

  public saveUser(user: User, config?: RequestConfig): Observable<User> {
    if (!user.password) {
      user.password = DEFAULT_PASSWORD;
    }
    return this.http.post<User>(`/api/admin`, user, defaultHttpOptionsFromConfig(config));
  }

  public deleteUser(userId: string, config?: RequestConfig): Observable<void> {
    return this.http.delete<void>(`/api/admin/${userId}`, defaultHttpOptionsFromConfig(config));
  }

  public getUsers(pageLink: PageLink, config?: RequestConfig): Observable<PageData<User>> {
    return this.http.get<PageData<User>>(`/api/admin${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }

  public saveAdminUser(user: User, config?: RequestConfig): Observable<User> {
    return this.http.post<User>(`/api/admin/user`, user, defaultHttpOptionsFromConfig(config));
  }
}
