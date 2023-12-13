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
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { RequestConfig } from '@core/http/http-utils';
import { PageData } from '@shared/models/page/page-data';
import { Connection, ConnectionDetailed } from '@shared/models/ws-client.model';
import { PageLink } from '@shared/models/page/page-link';
import mqtt from 'mqtt';

@Injectable({
  providedIn: 'root'
})
export class WsClientService {

  private connections = [];
  private subscriptions = [];

  private connection$ = new BehaviorSubject<any>(null);
  private connections$ = new BehaviorSubject<any>(this.connections);

  private subscription$ = new BehaviorSubject<any>(null);
  private subscriptions$ = new BehaviorSubject<any>(this.subscriptions);

  selectedConnection$ = this.connection$.asObservable();
  allConnections$ = this.connections$.asObservable();
  selectedSubscription$ = this.subscription$.asObservable();
  allSubscriptions$ = this.subscriptions$.asObservable();

  mqttJsClients: any[] = [];
  mqttJsClient: any;

  constructor(private http: HttpClient) {
  }

  connectClient(client) {
    const findConnection = this.connections.find(el => el.id === client.id);
    if (!findConnection) {
      this.createMqttJsClient(client);
    }
  }

  createMqttJsClient(client) {
    const host = client?.uri + client?.host + client?.port + client?.path;
    const options = {
      keepalive: 3660,
      clientId: 'tbmq_dev', // this.client?.clientId,
      username: 'tbmq_dev', // this.client?.username,
      password: 'tbmq_dev', // this.client?.password,
      protocolId: 'MQTT'
    };
    // @ts-ignore
    const mqttJsClient = mqtt.connect(host, options);
    this.mqttJsClient = mqttJsClient;
    this.mqttJsClients.push(mqttJsClient);
  }

  getMqttJsClient() {
    return this.mqttJsClient;
  }

  addConnection(value: any) {
    this.connection$.next(value);
    this.connections.push(value);
    this.connections$.next(this.connections);
  }

  addSubscription(value: any) {
    this.subscription$.next(value);
    this.subscriptions.push(value);
    this.subscriptions$.next(this.subscriptions);
  }

  selectConnection(connection: any) {
    this.connection$.next(connection);
  }

  public getConnectionMessages(id?: string, config?: RequestConfig): Observable<PageData<any>> {
    // return this.http.get<PageData<Connection>>(`/api/`, defaultHttpOptionsFromConfig(config));
    const mockData = {
      data: [
        {
          commentId: '1',
          displayName: 'clientId1',
          createdTime: 0,
          createdDateAgo: 'testtopic1',
          edit: false,
          isEdited: false,
          editedTime: 'string',
          editedDateAgo: 'string',
          showActions: false,
          commentText: 'Comment 1',
          isSystemComment: false,
          avatarBgColor: 'red',
          type: 'sub'
        },
        {
          commentId: '3',
          displayName: 'clientId3',
          createdTime: 0,
          createdDateAgo: 'testtopic3',
          edit: false,
          isEdited: false,
          editedTime: 'string',
          editedDateAgo: 'string',
          showActions: false,
          commentText: 'Comment 3',
          isSystemComment: false,
          avatarBgColor: 'red',
          type: 'sub'
        },
        {
          commentId: '2',
          displayName: 'clientId2',
          createdTime: 0,
          createdDateAgo: 'testtopic2',
          edit: false,
          isEdited: false,
          editedTime: 'string',
          editedDateAgo: 'string',
          showActions: false,
          commentText: 'Comment 2',
          isSystemComment: false,
          avatarBgColor: 'red',
          type: 'sub'
        },
        {
          commentId: '4',
          displayName: 'clientId1',
          createdTime: 0,
          createdDateAgo: 'testtopic4',
          edit: false,
          isEdited: false,
          editedTime: 'string',
          editedDateAgo: 'string',
          showActions: false,
          commentText: 'Comment 4',
          isSystemComment: false,
          avatarBgColor: 'blue',
          type: 'pub'
        }
      ],
      'totalPages': 1,
      'totalElements': 4,
      'hasNext': false
    };
    return of(mockData);
  }

  public getConnections(pageLink?: PageLink, config?: RequestConfig): Observable<PageData<Connection>> {
    // return this.http.get<PageData<Connection>>(`/api/`, defaultHttpOptionsFromConfig(config));
    const mockData = {
      data: [
        {
          id: '62397b5f-d04c-4d2e-957b-29209348cad3',
          name: 'TBMQ Device Demo',
          createdTime: 1701696568854,
          connected: true
        },
        {
          id: '8702ecc5-824e-4304-81ff-da2093574995',
          name: 'TBMQ Application Demo',
          createdTime: 1701696566405,
          connected: false
        },
        {
          id: '83508373-9b58-40d3-8fcb-0f25e6aa2999',
          name: 'Yes P',
          createdTime: 1701418253485,
          connected: true
        },
        {
          id: 'a6d1871e-55df-4dca-94c9-066e5d5220cd',
          name: 'Credentials TLS Demo',
          createdTime: 1700486480079,
          connected: false
        },
        {
          id: 'a6d1871e-55df-4dca-94c9-066e5d522052',
          name: 'D2',
          createdTime: 1700486480079,
          connected: false
        },
        {
          id: 'a6d1871e-55df-4dca-94c9-066e5d522012',
          name: 'TYR 23',
          createdTime: 1700486480079,
          connected: false
        },
        {
          id: 'a6d1871e-55df-4dca-94c9-066e5d522034',
          name: 'YTR 868',
          createdTime: 1700486480079,
          connected: false
        },
        {
          id: 'a6d1871e-55df-4dca-94c9-066e5d522099',
          name: 'RER 34',
          createdTime: 1700486480079,
          connected: false
        },
        {
          id: 'a6d1871e-55df-4dca-94c9-066e5d522088',
          name: 'DBS 4',
          createdTime: 1700486480079,
          connected: false
        },
        {
          id: 'a6d1871e-55df-4dca-94c9-066e5d522077',
          name: 'MHG 98',
          createdTime: 1700486480079,
          connected: false
        }
      ],
      'totalPages': 2,
      'totalElements': 10,
      'hasNext': true
    };
    return of(mockData);
  }

  public getConnection(id: string, config?: RequestConfig): Observable<ConnectionDetailed> {
    // return this.http.get<Connection>(`/api/${id}`, defaultHttpOptionsFromConfig(config));
    const mockData = {
      'name': 'Works 33',
      'uri': 'ws://',
      'host': 'localhost:',
      'port': 8084,
      'path': '/mqtt',
      'clientId': 'tbmq_dev',
      'username': 'tbmq_dev',
      'password': 'tbmq_dev',
      'keepAlive': 60,
      'reconnectPeriod': 1000,
      'connectTimeout': 30000,
      'clean': true,
      'protocolVersion': '5',
      'properties': {
        'sessionExpiryInterval': null,
        'receiveMaximum': null,
        'maximumPacketSize': null,
        'topicAliasMaximum': null,
        'requestResponseInformation': null,
        'requestProblemInformation': null,
        'userProperties': null
      },
      'userProperties': null,
      'will': null,
      'subscriptions': [
        {
          topic: 'test1',
          qos: 1,
          retain: false,
          color: 'yellow'
        },
        {
          topic: 'test2',
          qos: 2,
          retain: false,
          color: 'blue'
        },
        {
          topic: 'test3',
          qos: 0,
          retain: true,
          color: 'green'
        }
      ]
    };
    return of(mockData);
  }

  public saveConnection(entity: any, config?: RequestConfig): Observable<Connection> {
    const mockData = {
      id: '62397b5f-d04c-4d2e-957b-29209348cad3',
      name: 'TBMQ Device Demo',
      createdTime: 1701696568854
    };
    return of(mockData);
  }

  public getSubscriptions(config?: RequestConfig): Observable<any> {
    // return this.http.get<PageData<any>>(`/api/`, defaultHttpOptionsFromConfig(config));
    const mockData = [
      {
        id: '62397b5f-d04c-4d2e-957b-29209348cad3',
        topic: 'Sub 1',
        createdTime: 1701696568854,
        connected: true,
        'color': 'blue'
      },
      {
        id: '8702ecc5-824e-4304-81ff-da2093574995',
        topic: 'Sub 2',
        createdTime: 1701696566405,
        connected: false,
        'color': 'green'
      },
      {
        id: '83508373-9b58-40d3-8fcb-0f25e6aa2999',
        topic: 'Sub 3',
        createdTime: 1701418253485,
        connected: true,
        'color': 'green'
      },
      {
        id: 'a6d1871e-55df-4dca-94c9-066e5d5220cd',
        topic: 'Sub 4',
        createdTime: 1700486480079,
        connected: false,
        'color': 'orange'
      }
    ];
    return of(mockData);
  }

}
