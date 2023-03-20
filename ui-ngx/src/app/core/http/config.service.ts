///
/// Copyright © 2016-2023 The Thingsboard Authors
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
import { Observable, of } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { PageLink } from '@shared/models/page/page-link';
import { PageData } from '@shared/models/page/page-data';
import { DetailedClientSessionInfo } from '@shared/models/session.model';

@Injectable({
  providedIn: 'root'
})
export class ConfigService {

  constructor(private http: HttpClient) {
  }

  public getConfig(config?: RequestConfig): Observable<any> {
    return of([
        {
          key: 'PORT_MQTT',
          value: 1883
        },
        {
          key: 'TLS_TCP_PORT',
          value: 8883
        },
        {
          key: 'TCP_LISTENER',
          value: true
        },
        {
          key: 'TCP_LISTENER_MAX_PAYLOAD_SIZE',
          value: '65536 bytes'
        },
        {
          key: 'TLS_LISTENER',
          value: true
        },
        {
          key: 'TLS_LISTENER_MAX_PAYLOAD_SIZE',
          value: '65536 bytes'
        },
        {
          key: 'BASIC_AUTH',
          value: true
        },
        {
          key: 'X509_CERT_CHAIN_AUTH',
          value: false
        }
      ]
    );
  }

}
