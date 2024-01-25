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

// @ts-nocheck

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, mergeMap, Observable, of } from 'rxjs';
import { RequestConfig } from '@core/http/http-utils';
import { PageData } from '@shared/models/page/page-data';
import {
  Connection,
  ConnectionShortInfo,
  ConnectionStatus,
  DataSizeUnitType,
  TimeUnitType,
  WsPublishMessage,
  WsSubscription, WsSubscriptionShortInfo,
  WsTableMessage
} from '@shared/models/ws-client.model';
import mqtt, { IClientOptions, IPublishPacket, MqttClient } from 'mqtt';
import { DateAgoPipe } from '@shared/pipe/date-ago.pipe';
import { DatePipe } from '@angular/common';
import { ErrorWithReasonCode } from 'mqtt/src/lib/shared';
import { randomColor } from '@core/utils';

export interface StatusLog {
  createdTime: number;
  state: ConnectionStatus;
  details?: string;
}

@Injectable({
  providedIn: 'root'
})
export class WsClientService {

  private connections = [];
  private subscriptions = [];
  private messages: WsTableMessage[] = [];
  private filteredMessages: WsTableMessage[] = [];

  private connection$ = new BehaviorSubject<Connection>(null);
  private mqttClient$ = new BehaviorSubject<MqttClient>(null);
  private message$ = new BehaviorSubject<WsTableMessage>(null);
  private messages$ = new BehaviorSubject<WsTableMessage[]>(this.messages);
  private connections$ = new BehaviorSubject<any>(this.connections);
  private connectionStatus$ = new BehaviorSubject<ConnectionStatus>(ConnectionStatus.DISCONNECTED);
  private connectionFailedError$ = new BehaviorSubject<string>(null);

  private subscription$ = new BehaviorSubject<any>(null);
  private subscriptions$ = new BehaviorSubject<any>(this.subscriptions);

  selectedConnection$ = this.connection$.asObservable();
  allConnections$ = this.connections$.asObservable();
  selectedSubscription$ = this.subscription$.asObservable();
  allSubscriptions$ = this.subscriptions$.asObservable();
  selectedConnectionState$ = this.connectionStatus$.asObservable();
  selectedConnectionFailedError$ = this.connectionFailedError$.asObservable();
  newMessage$ = this.message$.asObservable();
  clientMessages$ = this.messages$.asObservable();

  mqttClients: any[] = [];
  mqttClient: any;

  mockConnections: Connection[] = [{
    id: '1',
    name: 'WebSocket Connection 1',
    protocol: 'ws://',
    host: 'localhost',
    port: 8084,
    url: 'ws://localhost:8084/mqtt',
    path: '/mqtt',
    protocolId: 'MQIsdp', // Or 'MQIsdp' in MQTT 3.1 and 5.0
    protocolVersion: 5, // Or 3 in MQTT 3.1, or 5 in MQTT 5.0
    clean: false, // Can also be false
    clientId: 'tbmq_dev',
    keepalive: 0, // Seconds which can be any positive number, with 0 as the default setting
    keepaliveUnit: TimeUnitType.SECONDS,
    connectTimeout: 0,
    connectTimeoutUnit: TimeUnitType.MILLISECONDS,
    reconnectPeriod: 0,
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
      sessionExpiryInterval: 666,
      receiveMaximum: 100,
      maximumPacketSize: 100000,
      topicAliasMaximum: null,
      requestResponseInformation: false,
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
  },
    {
      id: '2',
      name: 'WebSocket Connection 2',
      protocol: 'ws://',
      host: 'localhost',
      port: 8084,
      url: 'ws://localhost:8084/mqtt',
      path: '/mqtt',
      protocolId: 'MQIsdp', // Or 'MQIsdp' in MQTT 3.1 and 5.0
      protocolVersion: 3, // Or 3 in MQTT 3.1, or 5 in MQTT 5.0
      clean: false, // Can also be false
      clientId: 'tbmq_app',
      keepalive: 0, // Seconds which can be any positive number, with 0 as the default setting
      keepaliveUnit: TimeUnitType.SECONDS,
      connectTimeout: 0,
      connectTimeoutUnit: TimeUnitType.MILLISECONDS,
      reconnectPeriod: 0,
      reconnectPeriodUnit: TimeUnitType.MILLISECONDS,
      username: null,
      password: null, // Passwords are buffers
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
          id: '1',
          topic: 'sensors/temperature',
          color: randomColor(),
          options: {
              qos: 1,
              rh: null,
              nl: null,
              rap: null,
              properties: {
                subscriptionIdentifier: null
              }
            }
        },
        {
          id: '2',
          topic: 'sensors/#',
          color: randomColor(),
          options: {
              qos: 2,
              rh: null,
              nl: null,
              rap: null,
              properties: {
                subscriptionIdentifier: null
              }
            }
        }
      ]
    },
    {
      id: '2',
      data: []
    }
  ];

  mockSubscriptionsShortInfo = [
    {
      id: '1',
      data: [
        {
          id: '1',
          topic: 'sensors/temperature',
          color: randomColor()
        },
        {
          id: '2',
          topic: 'sensors/#',
          color: randomColor()
        }
      ]
    },
    {
      id: '2',
      data: []
    }
  ];

  connectionMqttClientMap = new Map<string, MqttClient>();
  connectionStatusMap = new Map<string, ConnectionStatus>();
  connectionStatusLogMap = new Map<string, StatusLog[]>();
  clientIdSubscriptionsMap = new Map<string, any[]>();

  constructor(private http: HttpClient,
              private datePipe: DatePipe,
              private dateAgoPipe: DateAgoPipe) {
  }

  connectClient(connection: Connection, password: string = null) {
    this.createMqttClient(this.connection$.value, password);
  }

  disconnectClient(force: boolean = false) {
    this.getMqttClientConnection().end(force);
  }

  public isConnectionConnected(connectionId: string): boolean {
    return this.connectionStatusMap.get(connectionId) === ConnectionStatus.CONNECTED;
  }

  private createMqttClient(connection: Connection, password: string) {
    const host = connection?.protocol + connection?.host + ":" + connection?.port + connection?.path;
    /*const options = {};
    for (let [key, value] of Object.entries(connection)) {
      options[key] = value;
    }*/
    const options: IClientOptions = {
      clientId: connection.clientId, // this.client?.clientId,
      username: connection.username, // this.client?.username,
      password: connection.password, // this.client?.password,
      protocolId: connection.protocolId,
      protocolVersion: connection.protocolVersion,
      clean: connection.clean,
      keepalive: connection.keepalive,
      connectTimeout: connection.connectTimeout,
      reconnectPeriod: connection.reconnectPeriod,
      properties: {
        sessionExpiryInterval: connection.properties.sessionExpiryInterval,
        receiveMaximum: connection.properties.receiveMaximum,
        maximumPacketSize: connection.properties.maximumPacketSize,
        topicAliasMaximum: connection.properties.topicAliasMaximum,
        requestResponseInformation: connection.properties.requestResponseInformation,
        userProperties: connection.properties.userProperties
      },
      will: {
        topic: connection.will.topic,
        qos: connection.will.qos,
        retain: connection.will.retain,
        payload: connection.will.payload,
        properties: {
          willDelayInterval: connection.will.properties.willDelayInterval,
          payloadFormatIndicator: connection.will.properties.payloadFormatIndicator,
          messageExpiryInterval: connection.will.properties.messageExpiryInterval,
          contentType: connection.will.properties.contentType,
          responseTopic: connection.will.properties.responseTopic,
          correlationData: connection.will.properties.correlationData
        }
      }
    };
    const options2: IClientOptions = {
      clientId: connection.clientId,
      username: connection.username,
      password: password, // password,
      properties: {
        sessionExpiryInterval: connection.properties.sessionExpiryInterval
      },
    };
    console.log('options2', options2);
    const mqttClient: MqttClient = mqtt.connect(connection.url, options2);
    this.setMqttClient(mqttClient, connection)
    this.manageMqttClientCallbacks(mqttClient, connection)
  }

  private setMqttClient(mqttClient: MqttClient, connection: Connection) {
    this.connectionMqttClientMap.set(connection.id, mqttClient);
    this.mqttClient = mqttClient;
    this.mqttClients.push(mqttClient);
  }

  private manageMqttClientCallbacks(mqttClient: MqttClient, connection: Connection) {
   mqttClient.on('connect', (packet: IConnackPacket) => {
      this.subscribeForTopics(mqttClient, connection);
      this.setConnectionStatus(connection, ConnectionStatus.CONNECTED);
      this.setConnectionLog(connection, ConnectionStatus.CONNECTED);
      this.setConnectionFailedError();
      console.log(`Client connected: ${connection.clientId}`, packet, mqttClient); // TODO check logic maybe use packet info for State definition
      /*client.subscribe(this.messangerFormGroup.get('topic').value, { qos: 1 }, (mess) => {
        if (mess) {
          this.displayData.push(mess);
          console.log('message', mess)
        }
      })*/
    });
   mqttClient.on('message', (topic: string, payload: Buffer, packet: IPublishPacket) => {
      const subscriptions = this.clientIdSubscriptionsMap.get(mqttClient.options.clientId);
      const subscription = subscriptions.find(sub => sub.topic === topic);
      let color: string;
      if (subscription) {
        color = subscription.color;
      } else {
        const wildcardSubscription = this.findWildcardSubscription(subscriptions, topic);
        if (wildcardSubscription) {
          color = wildcardSubscription.color;
        } else {
          console.error(`mqttClient ${mqttClient} received message. No matched subscription topic in ${subscriptions} for topic ${topic}`);
        }
      }
      this.addMessage({
        payload: payload.toString(), //ew TextDecoder("utf-8").decode(payload),
        topic,
        qos: packet.qos,
        createdTime: this.nowTs(),
        retain: packet.retain,
        color,
        type: 'received'
      })
      console.log(`Received Message: ${payload} On topic: ${topic}`, packet)
    });
   mqttClient.on('error', (error: Error | ErrorWithReasonCode) => {
      const details = error.message.split(':')[1];
      this.setConnectionStatus(connection, ConnectionStatus.CONNECTION_FAILED);
      this.setConnectionLog(connection, ConnectionStatus.CONNECTION_FAILED, details);
      this.setConnectionFailedError(details);
      this.disconnectClient(true);
      // mqttClient.end(force); // TODO check what to change
      console.log('Connection error: ', error)
    });
   mqttClient.on('reconnect', () => {
      this.setConnectionStatus(connection, ConnectionStatus.RECONNECTING);
      this.setConnectionLog(connection, ConnectionStatus.RECONNECTING);
      console.log('Reconnecting...')
    });
   mqttClient.on('close', () => {
      const status = mqttClient.reconnecting ? ConnectionStatus.RECONNECTING : ConnectionStatus.DISCONNECTED;
      this.setConnectionStatus(connection, status);
      this.setConnectionLog(connection, ConnectionStatus.CLOSE);
      console.log('Closing...', connection, mqttClient)
    });
   mqttClient.on('disconnect', (packet: IConnackPacket) => {
      this.setConnectionStatus(connection, ConnectionStatus.DISCONNECTED);
      this.setConnectionLog(connection, ConnectionStatus.DISCONNECTED);
      console.log('Disconnecting...', packet, connection, mqttClient)
    });
   mqttClient.on('offline', () => {
      this.setConnectionStatus(connection, ConnectionStatus.RECONNECTING);
      this.setConnectionLog(connection, ConnectionStatus.OFFLINE);
      console.log('Offline...', connection, mqttClient)
    });
   mqttClient.on('end', () => {
      this.setConnectionStatus(connection, ConnectionStatus.DISCONNECTED);
      this.setConnectionLog(connection, ConnectionStatus.END);
      console.log('End...', connection, mqttClient)
    });
   mqttClient.on('outgoingEmpty', () => {
      console.log('Ongoing empty')
    });
   mqttClient.on('packetsend', (packet: IConnackPacket) => {
      console.log('Packet Send...', packet, mqttClient)
    });
   mqttClient.on('packetreceive', (packet: IConnackPacket) => {
      console.log('Packet Receive...', packet, mqttClient)
    });

    // TODO remove connection console.log
  }

  private findWildcardSubscription(subscriptions: WsSubscription[], topic: string): WsSubscription {
    function isTopicMatched(subscription: string, topic: string): boolean {
      let subscriptionParts = subscription.split('/');
      let topicParts = topic.split('/');

      for(let i=0; i<subscriptionParts.length; i++) {
        if(subscriptionParts[i] === '#') return true;
        if(subscriptionParts[i] !== '+' && subscriptionParts[i] !== topicParts[i]) return false;
      }

      return subscriptionParts.length === topicParts.length;
    }

    function checkTopicInSubscriptions(subscriptions: WsSubscription[], topic: string): boolean {
      for (let subscriptionTopic of subscriptions.map(el => el.topic)) {
        if (isTopicMatched(subscriptionTopic, topic)) {
          return subscriptions.find(el => el.topic === subscriptionTopic);
        }
      }
      return false;
    }

    return checkTopicInSubscriptions(subscriptions, topic);
  }

  private setConnectionFailedError(error: string = null) {
    this.connectionFailedError$.next(error);
  }

  private setConnectionStatus(connection: Connection, state: ConnectionStatus) {
    this.connectionStatusMap.set(connection.id, state);
    this.connectionStatus$.next(state);
  }

  private setConnectionLog(connection: Connection, state: ConnectionStatus, details: string = null) {
    const log = {
      createdTime: this.nowTs(),
      state,
      details
    };
    if (this.connectionStatusLogMap.has(connection.id)) {
      this.connectionStatusLogMap.get(connection.id)?.push(log);
    } else {
      this.connectionStatusLogMap.set(connection.id, [log]);
    }

  }

  private nowTs() {
    return Date.now();
  }

  filterMessagesByType(type: string) {
    switch (type) {
      case 'published':
      case 'received':
        const newAr = [];
        this.messages.forEach(el => {
          if (el.type === type) {
            newAr.push(el);
          }
        })
        const messagesCopy = this.messages.filter(el => el.type === type);
        console.log(newAr)
        this.messages$.next(newAr);
        break;
      case 'all':
        this.messages$.next(this.messages);
        break;
    }
  }

  private subscribeForTopics(mqttClient: MqttClient, connection: Connection) {
    this.getSubscriptionsShortInfo(connection.id).subscribe(
      subscriptions => {
        const topics = [];
        const topicObject: any = {};
        for (let i = 0; i < subscriptions?.length; i++) {
          console.log('Subscribed for topic', subscriptions[i], mqttClient);
          // this.subscribeForTopic(mqttClient, subscriptions[i]);
          const topic = subscriptions[i].topic;
          const qos = subscriptions[i].options.qos;
          topicObject[topic] = {qos};
          topics.push(subscriptions[i]);
        }
        this.clientIdSubscriptionsMap.set(mqttClient.options.clientId, topics);
        // const topicList = topics.map(el => el.topic);
        this.subscribeForTopic(mqttClient, topicObject);
      }
    );
  }

  publishMessage(message: WsPublishMessage) {
    this.mqttClient.publish(message.topic, message.payload, message.options);
    /*this.messages.unshift({
      createdTime: this.nowTs(),
      topic: message.topic,
      payload: message.payload, //new TextDecoder("utf-8").decode(payload),
      qos: message.options.qos,
      retain: message.options.retain,
      properties: message.options.properties,
      type: 'published'
    });
    this.messages$.next(this.messages);*/
    this.addMessage({
      createdTime: this.nowTs(),
      topic: message.topic,
      payload: message.payload, //new TextDecoder("utf-8").decode(payload),
      qos: message.options.qos,
      retain: message.options.retain,
      properties: message.options.properties,
      type: 'published'
    });
  }

  private addMessage(message: WsPublishMessage) {
    if (this.messages.length >= 1000) {
      this.messages.pop();
    }
    this.messages.unshift(message);
    this.messages$.next(this.messages);
  }

  private subscribeForTopic(mqttClient: MqttClient, topicObject: any) {
    mqttClient.subscribe(topicObject);
  }

  getMqttClientConnection(): MqttClient {
    return this.mqttClient;
  }

  addSubscription(value: any) {
    this.subscription$.next(value);
    this.subscriptions.push(value);
    this.subscriptions$.next(this.subscriptions);
  }

  selectConnection(connection: any) {
    this.connection$.next(connection);
  }

  public getMessages(): Observable<PageData<WsTableMessage>> {
    // @ts-ignore
    return of({
      totalPages: 1,
      totalElements: 1000,
      hasNext: false,
      data: this.messages
    });
  }

  public getConnections(config?: RequestConfig): Observable<ConnectionShortInfo[]> {
    // return this.http.get<PageData<Connection>>(`/api/`, defaultHttpOptionsFromConfig(config));
    return of(this.mockConnections);
  }

  public getConnection(id: string, config?: RequestConfig): Observable<Connection> {
    // return this.http.get<Connection>(`/api/${id}`, defaultHttpOptionsFromConfig(config));
    const target = this.mockConnections.find(el => el.id == id);
    return of(target);
  }

  public setConnectionsInitState(entity: Connection[]) {
    entity.forEach(connection => {
      this.connectionStatusMap.set(connection.id, ConnectionStatus.DISCONNECTED);
    });
  }

  public getConnectionState(entity: Connection): string {
    return this.connectionStatusMap.get(entity.id);
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
      this.connectionStatusMap.delete(entity.id);
    }
    if (entity.id === activeConnectionId) {
      if (this.mockConnections.length) {
        this.selectConnection(this.mockConnections[0]);
      }
    }
  }

  public getSubscriptionsShortInfo(connectionId: any, config?: RequestConfig): Observable<WsSubscriptionShortInfo[]> {
    const index = this.mockSubscriptions.findIndex(el => el.id == connectionId);
    if (index > -1) {
      return of(this.mockSubscriptions[index].data as WsSubscriptionShortInfo[]);
    } else {
      return of([]);
    }
  }

  public getSubscription(connectionId: string, subscriptionId: string, config?: RequestConfig): Observable<WsSubscription> {
    const index = this.mockSubscriptions.findIndex(el => el.id == connectionId);
    if (index > -1) {
      const subscription = this.mockSubscriptions[index].data.find(el => el.id === subscriptionId);
      return of(subscription as WsSubscription[]);
    } else {
      return of([]);
    }
  }

  public saveSubscriptionV3(entityId: string, subscription: WsSubscription = null, isEdit:boolean, config?: RequestConfig): Observable<WsSubscription> {
    // return this.http.post<WsSubscription>(`/api/ws/${connectionId}/subscription`, config);
    const index = this.mockSubscriptions.findIndex(el => el.id === entityId);
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

  public deleteSubscriptionV3(entityId: string, subscription: WsSubscription = null) {
    const index = this.mockSubscriptions.findIndex(el => el.id === entityId);
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

  public clearHistory() {
    this.messages$.next([]);
  }

}
