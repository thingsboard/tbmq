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
import {
  Connection,
  DataSizeUnitType,
  TimeUnitType,
  WsMessage, WsSubscription
} from '@shared/models/ws-client.model';
import { PageLink } from '@shared/models/page/page-link';
import mqtt from 'mqtt';
import { MessagesDisplayData } from '@home/pages/ws-client/messages/messages.component';
import { DateAgoPipe } from '@shared/pipe/date-ago.pipe';
import { DatePipe } from '@angular/common';

@Injectable({
  providedIn: 'root'
})
export class WsClientService {

  private connections = [];
  private subscriptions = [];

  private connection$ = new BehaviorSubject<any>(null);
  private message$ = new BehaviorSubject<any>(null);
  private connections$ = new BehaviorSubject<any>(this.connections);

  private subscription$ = new BehaviorSubject<any>(null);
  private subscriptions$ = new BehaviorSubject<any>(this.subscriptions);

  selectedConnection$ = this.connection$.asObservable();
  allConnections$ = this.connections$.asObservable();
  selectedSubscription$ = this.subscription$.asObservable();
  allSubscriptions$ = this.subscriptions$.asObservable();

  newMessage$ = this.message$.asObservable();

  mqttJsClients: any[] = [];
  mqttJsClient: any;

  mockConnections: Connection[] = [{
    id: '1',
    name: 'WebSocket Connection 1',
    protocol: 'ws://',
    host: 'localhost',
    port: 8084,
    url: 'ws://localhost:8084/mqtt',
    path: '/mqtt',
    protocolId: 'MQTT', // Or 'MQIsdp' in MQTT 3.1 and 5.0
    protocolVersion: 5, // Or 3 in MQTT 3.1, or 5 in MQTT 5.0
    clean: false, // Can also be false
    clientId: 'tbmq_dev',
    keepalive: 0, // Seconds which can be any positive number, with 0 as the default setting
    keepaliveUnit: TimeUnitType.SECONDS,
    connectTimeout: 30000,
    connectTimeoutUnit: TimeUnitType.MILLISECONDS,
    reconnectPeriod: 10000,
    reconnectPeriodUnit: TimeUnitType.MILLISECONDS,
    username: 'tbmq_dev',
    password: 'tbmq_dev', // Passwords are buffers
    existingCredentials: {
      id: "62397b5f-d04c-4d2e-957b-29209348cad3",
      name: "TBMQ Device Demo"
    },
    will: {
      topic: 'mydevice/status',
      qos: 2,
      retain: false,
      payload: {'a': 123}, // Payloads are buffers
      properties: { // MQTT 5.0
        willDelayInterval: 1234,
        willDelayIntervalUnit: TimeUnitType.SECONDS,
        payloadFormatIndicator: true,
        messageExpiryInterval: 4321,
        messageExpiryIntervalUnit: TimeUnitType.SECONDS,
        contentType: 'test',
        responseTopic: 'topic',
        correlationData: [1, 2, 3, 4]
      }
    },
    properties: { // MQTT 5.0 properties
      sessionExpiryInterval: 1234,
      receiveMaximum: 432,
      maximumPacketSize: 100,
      topicAliasMaximum: 456,
      requestResponseInformation: true,
      requestProblemInformation: true,
      authenticationMethod: 'test',
      authenticationData: [1, 2, 3, 4],
      sessionExpiryIntervalUnit: TimeUnitType.SECONDS,
      maximumPacketSizeUnit: DataSizeUnitType.KB,
      userProperties: {
        'key_1': 'value 1',
        'key_2': 'value 2'
      },
    }
  }];

  mockSubscriptions = [
    {
      id: '1',
      data: [{
        topic: 'sensors/1',
        color: 'blue',
        options: {
          qos: 2,
          rh: 2,
          nl: true,
          rap: false,
          properties: {
            subscriptionIdentifier: 12
          }
        }
      },
        {
          topic: 'sensors/2',
          color: 'red',
          options: {
            qos: 2,
            rh: 2,
            nl: null,
            rap: null,
            properties: {
              subscriptionIdentifier: 12
            }
          }
        }
      ]
    }
  ];

  constructor(private http: HttpClient,
              private datePipe: DatePipe,
              private dateAgoPipe: DateAgoPipe) {
  }

  connectClient(connection) {
    const mqttJsConnection = this.findMqttJsConnection(connection);
    if (mqttJsConnection) {
      mqttJsConnection.connect();
      this.subscribeConnectionForTopics(mqttJsConnection);
    } else {
      this.getConnection(connection.id).subscribe(
        res => {
          this.createMqttJsClient(res);
        }
      );
    }
  }

  disconnectClient(connection) {
    const mqttJsConnection = this.findMqttJsConnection(connection);
    if (mqttJsConnection) {
      mqttJsConnection.disconnect();
    }
  }

  private createMqttJsClient(connection) {
    const host = connection?.protocol + connection?.host + ":" + connection?.port + connection?.path;
    /*const options = {};
    for (let [key, value] of Object.entries(connection)) {
      options[key] = value;
    }*/
    const options = {
      id: connection.id,
      clientId: 'tbmq_dev', // this.client?.clientId,
      username: 'tbmq_dev', // this.client?.username,
      password: 'tbmq_dev', // this.client?.password,
      protocolId: 'MQTT'
    };
    /*const options = {
      id: connection.id,
      keepalive: 60,
      clientId: connection.clientId,
      username: connection.username,
      password: connection.password,
      protocolId: 'MQTT',
      protocolVersion: 4,
      clean: true,
      reconnectPeriod: 1000,
      connectTimeout: 30 * 1000,
      will: {
        topic: 'WillMsg',
        payload: 'Connection Closed abnormally..!',
        qos: 0,
        retain: false
      },
    }*/
    // @ts-ignore
    const mqttJsClient = mqtt.connect(host, options);
    this.mqttJsClient = mqttJsClient;
    this.mqttJsClients.push(mqttJsClient);
    this.subscribeConnectionForTopics(mqttJsClient);
  }

  private findMqttJsConnection(mqttJsConnection) {
    return this.mqttJsClients.find(el => el.options.id === mqttJsConnection.id);
  }

  private subscribeConnectionForTopics(mqttJsConnection) {
    this.getSubscriptions(mqttJsConnection.options.id).subscribe(
      res => {
        for (let i = 0; i < res.data?.length; i++) {
          const subscription = res.data[i];
          this.createSubscription(mqttJsConnection, subscription);
        }
      }
    );
    mqttJsConnection.on('message', (topic, message, packet) => {
      if (message) {
        const data: any = new TextDecoder("utf-8").decode(message);
        if (data?.length) {
          let message: string;
          let isSubMessage: string;
          try {
            const res = JSON.parse(data);
            message = res.value;
            isSubMessage = 'pub';
          } catch (error) {
            message = data;
            isSubMessage = 'sub';
          }
          this.message$.next({
            message: data,
            topic,
            connection: mqttJsConnection,
            type: 'sub'
          });
        }
      }
    });
  }

  publishMessage(res) {
    this.message$.next({
      message: res.message,
      topic: res.topic,
      type: res.type
    });
  }

  private createSubscription(mqttJsConnection, subscription) {
    mqttJsConnection.subscribe(subscription.topic, {qos: 1});
  }

  getMqttJsConnection() {
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

  public getConnectionMessages(id?: number, config?: RequestConfig): Observable<PageData<MessagesDisplayData>> {
    // return this.http.get<PageData<Connection>>(`/api/`, defaultHttpOptionsFromConfig(config));
    const mockData = {
      data: [
        [{
          commentId: '1',
          displayName: 'topic1_1',
          qos: 1,
          retain: false,
          createdTime: this.datePipe.transform(1703588110741, 'yyyy-MM-dd HH:mm:ss'),
          createdDateAgo: this.dateAgoPipe.transform(1703588110741, {applyAgo: true, short: true, textPart: true}),
          edit: false,
          isEdited: false,
          editedTime: 'string',
          editedDateAgo: 'string',
          showActions: false,
          commentText: JSON.stringify({completed: false,id: 1,title: "delectus aut autem",userId: 1}, null, 2),
          isSystemComment: false,
          avatarBgColor: 'yellow',
          type: 'sub',
          contentType: 'JSON',
          userProperties: {'key1':'value1','key2':'value2'}
        },
          {
            commentId: '1',
            displayName: 'topic1_2',
            qos: 1,
            retain: false,
            createdTime: this.datePipe.transform(1703588110741, 'yyyy-MM-dd HH:mm:ss'),
            createdDateAgo: this.dateAgoPipe.transform(1703588110741),
            edit: false,
            isEdited: false,
            editedTime: 'string',
            editedDateAgo: 'string',
            showActions: false,
            commentText: JSON.stringify({ active: true, codes: [48348, 28923, 39080], city: "London" }, null, 2),
            isSystemComment: false,
            avatarBgColor: 'yellow',
            type: 'pub',
            userProperties: {'key1':'value1','key2':'value2'}
          },
          {
            commentId: '1',
            displayName: 'topic1_1',
            qos: 1,
            retain: false,
            createdTime: this.datePipe.transform(1703588110741, 'yyyy-MM-dd HH:mm:ss'),
            createdDateAgo: this.dateAgoPipe.transform(1703588110741),
            edit: false,
            isEdited: false,
            editedTime: 'string',
            editedDateAgo: 'string',
            showActions: false,
            commentText: 'topic is a String topic to subscribe to or an Array of topics to subscribe to. It can also be an object, it has as object keys the topic name and as value the QoS, like {\'test1\': {qos: 0}, \'test2\': {qos: 1}}. MQTT',
            isSystemComment: false,
            avatarBgColor: 'blue',
            type: 'sub'
          },
          {
            commentId: '1',
            displayName: 'topic1_2',
            qos: 1,
            retain: false,
            createdTime: this.datePipe.transform(1703588110741, 'yyyy-MM-dd HH:mm:ss'),
            createdDateAgo: this.dateAgoPipe.transform(1703588110741),
            edit: false,
            isEdited: false,
            editedTime: 'string',
            editedDateAgo: 'string',
            showActions: false,
            commentText: 'Comment 1',
            isSystemComment: false,
            avatarBgColor: 'yellow',
            type: 'pub'
          },
          {
          commentId: '1',
          displayName: 'topic1_2',
          qos: 1,
          retain: false,
          createdTime: this.datePipe.transform(1703588110741, 'yyyy-MM-dd HH:mm:ss'),
          createdDateAgo: this.dateAgoPipe.transform(1703588110741),
          edit: false,
          isEdited: false,
          editedTime: 'string',
          editedDateAgo: 'string',
          showActions: false,
          commentText: 'Comment 1',
          isSystemComment: false,
          avatarBgColor: 'yellow',
          type: 'pub'
        },
          {
          commentId: '1',
          displayName: 'topic1_2',
          qos: 1,
          retain: false,
          createdTime: this.datePipe.transform(1703588110741, 'yyyy-MM-dd HH:mm:ss'),
          createdDateAgo: this.dateAgoPipe.transform(1703588110741),
          edit: false,
          isEdited: false,
          editedTime: 'string',
          editedDateAgo: 'string',
          showActions: false,
          commentText: 'Comment 1',
          isSystemComment: false,
          avatarBgColor: 'yellow',
          type: 'pub'
        },
          {
            commentId: '1',
            displayName: 'topic1_2',
            qos: 1,
            retain: false,
            createdTime: this.datePipe.transform(1703588110741, 'yyyy-MM-dd HH:mm:ss'),
            createdDateAgo: this.dateAgoPipe.transform(1703588110741),
            edit: false,
            isEdited: false,
            editedTime: 'string',
            editedDateAgo: 'string',
            showActions: false,
            commentText: 'Comment 1',
            isSystemComment: false,
            avatarBgColor: 'yellow',
            type: 'pub'
          },
          {
            commentId: '1',
            displayName: 'topic1_2',
            qos: 1,
            retain: false,
            createdTime: this.datePipe.transform(1703588110741, 'yyyy-MM-dd HH:mm:ss'),
            createdDateAgo: this.dateAgoPipe.transform(1703588110741),
            edit: false,
            isEdited: false,
            editedTime: 'string',
            editedDateAgo: 'string',
            showActions: false,
            commentText: 'Comment 1',
            isSystemComment: false,
            avatarBgColor: 'yellow',
            type: 'pub'
          },
          {
          commentId: '1',
          displayName: 'topic1_2',
          qos: 1,
          retain: false,
          createdTime: this.datePipe.transform(1703588110741, 'yyyy-MM-dd HH:mm:ss'),
          createdDateAgo: this.dateAgoPipe.transform(1703588110741),
          edit: false,
          isEdited: false,
          editedTime: 'string',
          editedDateAgo: 'string',
          showActions: false,
          commentText: 'Comment 1',
          isSystemComment: false,
          avatarBgColor: 'yellow',
          type: 'pub'
        },
          {
          commentId: '1',
          displayName: 'topic1_2',
          qos: 1,
          retain: false,
          createdTime: this.datePipe.transform(1703588110741, 'yyyy-MM-dd HH:mm:ss'),
          createdDateAgo: this.dateAgoPipe.transform(1703588110741),
          edit: false,
          isEdited: false,
          editedTime: 'string',
          editedDateAgo: 'string',
          showActions: false,
          commentText: 'Comment 1',
          isSystemComment: false,
          avatarBgColor: 'yellow',
          type: 'pub'
        }],
        [{
          commentId: '3',
          displayName: 'topic3',
          qos: 0,
          retain: true,
          createdTime: this.datePipe.transform(1703588110741, 'yyyy-MM-dd HH:mm:ss'),
          createdDateAgo: this.dateAgoPipe.transform(1703588110741),
          edit: false,
          isEdited: false,
          editedTime: 'string',
          editedDateAgo: 'string',
          showActions: false,
          commentText: 'Comment 3',
          isSystemComment: false,
          avatarBgColor: 'green',
          type: 'sub'
        }],
        [{
          commentId: '2',
          displayName: 'topic2',
          qos: 2,
          retain: true,
          createdTime: this.datePipe.transform(1703588110741, 'yyyy-MM-dd HH:mm:ss'),
          createdDateAgo: this.dateAgoPipe.transform(1703588110741),
          edit: false,
          isEdited: false,
          editedTime: 'string',
          editedDateAgo: 'string',
          showActions: false,
          commentText: 'Comment 2',
          isSystemComment: false,
          avatarBgColor: 'pink',
          type: 'sub'
        }],
        [{
          commentId: '4',
          displayName: 'topic4',
          qos: 0,
          retain: true,
          createdTime: this.datePipe.transform(1703588110741, 'yyyy-MM-dd HH:mm:ss'),
          createdDateAgo: this.dateAgoPipe.transform(1703588110741),
          edit: false,
          isEdited: false,
          editedTime: 'string',
          editedDateAgo: 'string',
          showActions: false,
          commentText: 'Comment 4',
          isSystemComment: false,
          avatarBgColor: 'blue',
          type: 'pub'
        }]
      ],
      'totalPages': 1,
      'totalElements': 4,
      'hasNext': false
    };
    const result = mockData.data[id-1];
    return of({
      'totalPages': 1,
      'totalElements': 4,
      'hasNext': false,
      data: result
    });
  }

  public getMessages(): Observable<PageData<WsMessage>> {
    return of({
      'totalPages': 1,
      'totalElements': 2,
      'hasNext': false,
      data: [
        {
          createdTime: 45745745745,
          retain: true,
          payload: null,
          qos: 1,
          topic: 'abc/topic/abc/topic/abc/topic/abc/topic',
          userProperties: 'present',
          color: 'green'
        },
        {
          createdTime: 54645673457,
          retain: false,
          payload: "{temp: 2}",
          qos: 2,
          topic: 'cde/topic/abc/topic',
          color: 'green'
        },
        {
          createdTime: 54645673457,
          retain: true,
          payload: "{temp: 2}",
          qos: 2,
          topic: 'cde/topic',
          color: 'blue'
        },
        {
          createdTime: 54645673457,
          retain: false,
          payload: "{temp: 2}",
          qos: 2,
          topic: 'cde/topic4',
          color: 'green'
        },
        {
          createdTime: 54645673457,
          retain: false,
          payload: "{temp: 1, humidity: 2, air: 2, time: 3, state: 'ok', state: 'ok', state: 'ok', state: 'ok', state: 'ok', state: 'ok', state: 'ok', state: 'ok', state: 'ok'}",
          qos: 2,
          topic: 'cde/topic',
          color: 'orange'
        },
        {
          createdTime: 54645673457,
          retain: false,
          payload: "{temp: 2}",
          qos: 2,
          topic: 'cde/topic',
          color: 'orange'
        },
        {
          createdTime: 54645673457,
          retain: false,
          payload: "{temp: 2}",
          qos: 2,
          topic: 'cde/topic'
        },
        {
          createdTime: 54645673457,
          retain: false,
          payload: "{temp: 2}",
          qos: 2,
          topic: 'cde/topic'
        },
        {
          createdTime: 54645673457,
          retain: false,
          payload: "{temp: 2}",
          qos: 2,
          topic: 'cde/topic'
        },
        {
          createdTime: 54645673457,
          retain: false,
          payload: "{temp: 2}",
          qos: 2,
          topic: 'cde/topic'
        }]
    });
  }

  public getConnections(pageLink?: PageLink, config?: RequestConfig): Observable<PageData<Connection>> {
    // return this.http.get<PageData<Connection>>(`/api/`, defaultHttpOptionsFromConfig(config));
    const mockData = {
      data: this.mockConnections,
      'totalPages': 1,
      'totalElements': 999,
      'hasNext': false
    };
    return of(mockData);
  }

  public getConnection(id: string, config?: RequestConfig): Observable<Connection> {
    // return this.http.get<Connection>(`/api/${id}`, defaultHttpOptionsFromConfig(config));
    const target = this.mockConnections.find(el => el.id == id);
    return of(target);
  }

  public saveConnection(entity: Connection, config?: RequestConfig): Observable<Connection> {
    const index = this.mockConnections.findIndex(el => el.id == entity.id);
    if (index > -1) {
      this.mockConnections[index] = entity;
    } else {
      entity.id = (this.mockConnections?.length + 1).toString();
      this.mockConnections.push(entity);
    }
    return of(entity);
  }

  public deleteConnection(entity: Connection, selectedConnection: Connection, config?: RequestConfig) {
    const activeConnectionId = selectedConnection.id;
    const index = this.mockConnections.findIndex(el => el.id === entity.id);
    if (index > -1) {
      this.mockConnections.splice(index, 1);
    }
    if (entity.id === activeConnectionId) {
      if (this.mockConnections.length) {
        this.selectConnection(this.mockConnections[0]);
      }
    }
  }

  public getSubscriptions(connectionId: any,config?: RequestConfig): Observable<PageData<any>> {
    // return this.http.get<PageData<any>>(`/api/`, defaultHttpOptionsFromConfig(config));
    const allData = [
      {
        id: 1,
        data: [
          {
            name: 'testtopic88',
            topic: 'testtopic88',
            color: 'blue',
            qos: 1
          },
          {
            name: 'testtopic2',
            topic: 'testtopic2',
            color: 'green',
            qos: 1
          },
          {
            name: 'testtopic3',
            topic: 'testtopic3',
            color: 'green',
            qos: 1
          },
          {
            name: 'testtopic4',
            topic: 'testtopic4',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic5',
            topic: 'testtopic5',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic6',
            topic: 'testtopic6',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic7',
            topic: 'testtopic7',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic8',
            topic: 'testtopic8',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic9',
            topic: 'testtopic9',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic10',
            topic: 'testtopic10',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic11',
            topic: 'testtopic11',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic12',
            topic: 'testtopic12',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic13',
            topic: 'testtopic13',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic14',
            topic: 'testtopic14',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic15',
            topic: 'testtopic15',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic16',
            topic: 'testtopic16',
            color: 'orange',
            qos: 1
          }
        ]
      },
      {
        id: 2,
        data: [
          {
            name: '2testtopic',
            topic: '2testtopic',
            color: 'pink',
            qos: 1
          }
        ]
      },
      {
        id: 3,
        data: [
          {
            name: '3testtopic1',
            topic: '3testtopic1',
            color: 'blue',
            qos: 1
          },
          {
            name: '3testtopic2',
            topic: '3testtopic2',
            color: 'yellow',
            qos: 1
          }
        ]
      },
      {
        id: 4,
        data: []
      },
      {
        id: 5,
        data: []
      },
      {
        id: 6,
        data: []
      }
    ];
    const target = allData.find(el => el.id == connectionId);
    const result = {
      totalPages: 1,
      totalElements: 4,
      hasNext: false,
      data: target?.data
    };
    return of(result);
  }

  public getSubscriptionsV2(connectionId: any,config?: RequestConfig): Observable<any[]> {
    // return this.http.get<PageData<any>>(`/api/`, defaultHttpOptionsFromConfig(config));
    const allData = [
      {
        id: 1,
        data: [
          {
            name: 'testtopic88',
            topic: 'testtopic88/testtopic88/#/testtopic88#/testtopic88',
            color: 'blue',
            qos: 1
          },
          {
            name: 'testtopic2',
            topic: 'testtopic2',
            color: 'green',
            qos: 1
          },
          {
            name: 'testtopic3',
            topic: 'testtopic3',
            color: 'green',
            qos: 1
          },
          {
            name: 'testtopic4',
            topic: 'testtopic4',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic5',
            topic: 'testtopic5',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic6',
            topic: 'testtopic6',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic7',
            topic: 'testtopic7',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic8',
            topic: 'testtopic8',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic9',
            topic: 'testtopic9',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic10',
            topic: 'testtopic10',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic11',
            topic: 'testtopic11',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic12',
            topic: 'testtopic12',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic13',
            topic: 'testtopic13',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic14',
            topic: 'testtopic14',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic15',
            topic: 'testtopic15',
            color: 'orange',
            qos: 1
          },
          {
            name: 'testtopic16',
            topic: 'testtopic16',
            color: 'orange',
            qos: 1
          }
        ]
      },
      {
        id: 2,
        data: [
          {
            name: '2testtopic',
            topic: '2testtopic',
            color: 'pink',
            qos: 1
          }
        ]
      },
      {
        id: 3,
        data: [
          {
            name: '3testtopic1',
            topic: '3testtopic1',
            color: 'blue',
            qos: 1
          },
          {
            name: '3testtopic2',
            topic: '3testtopic2',
            color: 'yellow',
            qos: 1
          }
        ]
      }
    ];
    const target = allData.find(el => el.id == connectionId);
    return of(target?.data);
  }

  public getSubscriptionsV3(connectionId: any, config?: RequestConfig): Observable<WsSubscription[]> {
    const index = this.mockSubscriptions.findIndex(el => el.id == connectionId);
    if (index > -1) {
      return of(this.mockSubscriptions[index].data as WsSubscription[]);
    } else {
      return of([]);
    }
  }

  public saveSubscriptionV3(connectionId: string, subscription: WsSubscription = null, isEdit:boolean, config?: RequestConfig): Observable<WsSubscription> {
    // return this.http.post<WsSubscription>(`/api/ws/${connectionId}/subscription`, config);
    const index = this.mockSubscriptions.findIndex(el => el.id === connectionId);
    const subscriptions: any = this.mockSubscriptions[index].data;
    if (isEdit) {
      const subIndex = subscriptions.findIndex(el => el.topic === subscription.topic);
      // @ts-ignore
      this.mockSubscriptions[index].data[subIndex] = subscription;
    } else {
      // @ts-ignore
      this.mockSubscriptions[index].data.push(subscription);
    }
    return of(subscription);
  }

  public deleteSubscriptionV3(connectionId: string, subscription: WsSubscription = null) {
    const index = this.mockSubscriptions.findIndex(el => el.id === connectionId);
    const subscriptions: any = this.mockSubscriptions[index].data;
    const subIndex = subscriptions.findIndex(el => el.topic === subscription.topic);
    // @ts-ignore
    this.mockSubscriptions[index].data.splice(subIndex, 1);
    return of(null);
  }


  public saveSubscription(connectionId: string): Observable<any> {
    const result = {
        topic: 'testtopic',
        color: 'pink',
        qos: 1,
        nl: false,
        rap: true,
        rh: true,
        subscriptionIdentifier: 1
      };
    return of(result);
  }

  public clearHistory(id) {
    return of(null);
  }

}
