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
import { BehaviorSubject, Observable, of } from 'rxjs';
import { RequestConfig } from '@core/http/http-utils';
import { emptyPageData, PageData } from '@shared/models/page/page-data';
import {
  Connection,
  ConnectionShortInfo,
  ConnectionStatus,
  ConnectionStatusLog,
  DataSizeUnitType,
  TimeUnitType,
  WsSubscription,
  WsSubscriptionShortInfo,
  WsTableMessage
} from '@shared/models/ws-client.model';
import mqtt, { IClientOptions, IPublishPacket, MqttClient } from 'mqtt';
import { DateAgoPipe } from '@shared/pipe/date-ago.pipe';
import { DatePipe } from '@angular/common';
import { ErrorWithReasonCode } from 'mqtt/src/lib/shared';
import { convertTimeUnits, guid, isDefinedAndNotNull, isNumber, randomColor } from '@core/utils';
import { MessageFilterConfig } from '@home/pages/ws-client/messages/message-filter-config.component';
import { PageLink } from '@shared/models/page/page-link';
import { Buffer } from 'buffer';

@Injectable({
  providedIn: 'root'
})
export class WsClientService {

  private connections = [];
  private subscriptions = [];
  private messages: WsTableMessage[] = [];

  private connection$ = new BehaviorSubject<Connection>(null);
  private messages$ = new BehaviorSubject<WsTableMessage[]>(this.messages);
  private connections$ = new BehaviorSubject<any>(this.connections);
  private connectionStatus$ = new BehaviorSubject<ConnectionStatus>(ConnectionStatus.DISCONNECTED);
  private connectionFailedError$ = new BehaviorSubject<string>(null);

  private subscription$ = new BehaviorSubject<any>(null);
  private subscriptions$ = new BehaviorSubject<any>(this.subscriptions);

  selectedConnection$ = this.connection$.asObservable();
  allConnections$ = this.connections$.asObservable();
  selectedSubscription$ = this.subscription$.asObservable();
  selectedConnectionStatus$ = this.connectionStatus$.asObservable();
  selectedConnectionFailedError$ = this.connectionFailedError$.asObservable();
  clientMessages$ = this.messages$.asObservable();

  connectionMessagesMap = new Map<string, WsTableMessage[]>();

  mqttClients: MqttClient[] = [];
  mqttClient: MqttClient;

  private messagesFilter: MessageFilterConfig = {
    type: 'all',
    topic: null,
    qosList: null,
    retainList: null
  };

  mockConnections: Connection[] = [
    {
      id: '1',
      name: 'WebSocket Connection 1',
      protocol: 'ws://',
      host: 'localhost',
      port: 8084,
      url: 'ws://localhost:8084/mqtt',
      path: '/mqtt',
      protocolId: 'MQTT', // Or 'MQIsdp' in MQTT 3.1 and 5.0
      protocolVersion: 5, // Or 3 in MQTT 3.1, or 5 in MQTT 5.0
      clean: true, // Can also be false
      clientId: 'tbmq_dev',
      keepalive: 60, // Seconds which can be any positive number, with 0 as the default setting
      keepaliveUnit: TimeUnitType.SECONDS,
      connectTimeout: 30,
      connectTimeoutUnit: TimeUnitType.SECONDS,
      reconnectPeriod: 1,
      reconnectPeriodUnit: TimeUnitType.SECONDS,
      username: 'tbmq_dev',
      password: 'tbmq_dev', // Passwords are buffers
      clientCredentialsId: '62397b5f-d04c-4d2e-957b-29209348cad3',
      will: {
        topic: 'sensors1/temperature',
        qos: 1,
        retain: true,
        payload: 'WILL PAYLOAD TEST', // Payloads are buffers
        properties: { // MQTT 5.0
          willDelayInterval: 0,
          willDelayIntervalUnit: TimeUnitType.SECONDS,
          payloadFormatIndicator: false,
          messageExpiryInterval: 0,
          messageExpiryIntervalUnit: TimeUnitType.SECONDS,
          contentType: '',
          responseTopic: 'sensors1/temperature',
          correlationData: '12345'
        }
      },
      properties: { // MQTT 5.0 properties
        sessionExpiryInterval: 0,
        sessionExpiryIntervalUnit: TimeUnitType.SECONDS,
        receiveMaximum: 65535,
        maximumPacketSize: 100000,
        maximumPacketSizeUnit: DataSizeUnitType.KB,
        topicAliasMaximum: 0,
        requestResponseInformation: false,
        requestProblemInformation: false,
        userProperties: {
          'key_1': 'value 1',
          'key_2': 'value 2'
        },
      }
    }
  ];

  mockSubscriptions = [
    {
      id: '1',
      data: [{
        id: '1',
        topic: 'sensors/temperature',
        color: randomColor(),
        options: {
          qos: 0,
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
      data: [{
        id: '3',
        topic: 'sensors1/temperature',
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
      },]
    }
  ];

  connectionMqttClientMap = new Map<string, string>();
  connectionStatusMap = new Map<string, ConnectionStatus>();
  connectionStatusLogMap = new Map<string, ConnectionStatusLog[]>();
  clientIdSubscriptionsMap = new Map<string, any[]>();
  mqttClientConnectionMap = new Map<string, string>();

  constructor(private http: HttpClient,
              private datePipe: DatePipe,
              private dateAgoPipe: DateAgoPipe) {
  }

  connectClient(connection: Connection, password: string = null) {
    this.addMqttClient(connection, password);
  }

  disconnectClient(force: boolean = false) {
    this.getActiveMqttJsClient().end(force);
  }

  public isConnectionConnected(connectionId: string): boolean {
    return this.connectionStatusMap.get(connectionId) === ConnectionStatus.CONNECTED;
  }

  private addMqttClient(connection: Connection, password: string) {
    const options: IClientOptions = {
      clientId: connection.clientId,
      username: connection.username,
      password: connection.password, // password
      protocolId: connection.protocolId,
      protocolVersion: connection.protocolVersion,
      clean: true,
      keepalive: convertTimeUnits(connection.keepalive, connection.keepaliveUnit, TimeUnitType.SECONDS),
      connectTimeout: convertTimeUnits(connection.connectTimeout, connection.connectTimeoutUnit, TimeUnitType.MILLISECONDS),
      reconnectPeriod: convertTimeUnits(connection.reconnectPeriod, connection.reconnectPeriodUnit, TimeUnitType.MILLISECONDS),
      properties: {
        sessionExpiryInterval: connection.properties.sessionExpiryInterval,
        receiveMaximum: connection.properties.receiveMaximum,
        maximumPacketSize: connection.properties.maximumPacketSize,
        topicAliasMaximum: connection.properties.topicAliasMaximum,
        requestResponseInformation: connection.properties.requestResponseInformation,
        userProperties: connection.properties.userProperties
      }
    };
    if (isDefinedAndNotNull(connection.will)) {
      options.will = {
        topic: connection.will.topic,
        qos: connection.will.qos,
        retain: connection.will.retain,
        payload: connection.will.payload,
        properties: {
          contentType: connection.will.properties.contentType,
          responseTopic: connection.will.properties.responseTopic,
          willDelayInterval: convertTimeUnits(connection.will.properties.willDelayInterval, connection.will.properties.willDelayIntervalUnit, TimeUnitType.SECONDS),
          messageExpiryInterval: convertTimeUnits(connection.will.properties.messageExpiryInterval, connection.will.properties.messageExpiryIntervalUnit, TimeUnitType.SECONDS),
          payloadFormatIndicator: connection.will.properties.payloadFormatIndicator,
          correlationData: Buffer.from(connection.will.properties.correlationData)
        }
      };
    }
    console.log('options', options);
    const mqttClient: MqttClient = mqtt.connect(connection.url, options);
    this.setMqttClient(mqttClient, connection);
    this.manageMqttClientCallbacks(mqttClient, connection);
  }

  private setMqttClient(mqttClient: MqttClient, connection: Connection) {
    this.mqttClient = mqttClient;
    this.mqttClients.push(mqttClient);
  }

  private manageMqttClientCallbacks(mqttClient: MqttClient, connection: Connection) {
    mqttClient.on('connect', (packet: IConnackPacket) => {
      this.connectionMqttClientMap.set(connection.id, mqttClient);
      this.mqttClientConnectionMap.set(mqttClient.options.clientId, connection.id);
      this.subscribeForTopics(mqttClient, connection);
      this.setConnectionStatus(connection, ConnectionStatus.CONNECTED);
      this.setConnectionLog(connection, ConnectionStatus.CONNECTED);
      console.log(`Client connected!`, packet, mqttClient);
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
      const message: WsTableMessage = {
        id: guid(),
        payload: payload.toString(), //ew TextDecoder("utf-8").decode(payload),
        topic,
        qos: packet.qos,
        createdTime: this.nowTs(),
        retain: packet.retain,
        color,
        type: 'received'
      };
      const connectionId = this.mqttClientConnectionMap.get(mqttClient.options.clientId);
      this.addMessage(message, connectionId);
      console.log(`Received Message: ${payload} On topic: ${topic}`, packet, mqttClient);
    });
    mqttClient.on('error', (error: Error | ErrorWithReasonCode) => {
      const details = error.message.split(':')[1];
      this.setConnectionStatus(connection, ConnectionStatus.CONNECTION_FAILED);
      this.setConnectionLog(connection, ConnectionStatus.CONNECTION_FAILED, details);
      this.setConnectionFailedError(details);
      mqttClient.end();
      console.log('Connection error: ', error, mqttClient);
    });
    mqttClient.on('reconnect', () => {
      this.setConnectionStatus(connection, ConnectionStatus.RECONNECTING);
      this.setConnectionLog(connection, ConnectionStatus.RECONNECTING);
      console.log('Reconnecting...', mqttClient);
    });
    mqttClient.on('close', () => {
      // const status = mqttClient.reconnecting ? ConnectionStatus.RECONNECTING : ConnectionStatus.DISCONNECTED;
      // this.setConnectionStatus(connection, status);
      // this.setConnectionLog(connection, status);
      // mqttClient.end();
      console.log('Closing...', mqttClient);
    });
    mqttClient.on('disconnect', (packet: IConnackPacket) => {
      this.setConnectionStatus(connection, ConnectionStatus.DISCONNECTED);
      this.setConnectionLog(connection, ConnectionStatus.DISCONNECTED);
      console.log('Disconnecting...', packet, mqttClient);
    });
    mqttClient.on('offline', () => {
      this.setConnectionStatus(connection, ConnectionStatus.RECONNECTING);
      this.setConnectionLog(connection, ConnectionStatus.RECONNECTING);
      console.log('Offline...', mqttClient);
    });
    mqttClient.on('end', () => {
      this.setConnectionStatus(connection, ConnectionStatus.DISCONNECTED);
      this.setConnectionLog(connection, ConnectionStatus.DISCONNECTED);
      const connectionId = this.mqttClientConnectionMap.get(mqttClient.options.clientId);
      if (this.connectionMqttClientMap.has(connectionId)) {
        this.connectionMqttClientMap.delete(connectionId);
      }
      console.log('End...', mqttClient);
    });
    mqttClient.on('outgoingEmpty', () => {
      console.log('Ongoing empty');
    });
    mqttClient.on('packetsend', (packet: IConnackPacket) => {
      console.log('Packet Send...', packet, mqttClient)
    });
    mqttClient.on('packetreceive', (packet: IConnackPacket) => {
      console.log('Packet Receive...', packet, mqttClient)
    });
  }

  private findWildcardSubscription(subscriptions: WsSubscription[], topic: string): WsSubscription {
    function isTopicMatched(subscription: string, topic: string): boolean {
      let subscriptionParts = subscription.split('/');
      let topicParts = topic.split('/');

      for (let i = 0; i < subscriptionParts.length; i++) {
        if (subscriptionParts[i] === '#') {
          return true;
        }
        if (subscriptionParts[i] !== '+' && subscriptionParts[i] !== topicParts[i]) {
          return false;
        }
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
    this.connectionStatus$.next(this.connectionStatusMap.get(connection.id));
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

  filterMessages(filter: MessageFilterConfig) {
    this.messagesFilter = {...this.messagesFilter, ...filter};
    this.updateMessages();
  }

  private subscribeForTopics(mqttClient: MqttClient, connection: Connection) {
    this.getSubscriptionsShortInfo(connection.id).subscribe(
      subscriptions => {
        const topics = [];
        const topicObject: any = {};
        for (let i = 0; i < subscriptions?.length; i++) {
          // console.log('Subscribed for topic', subscriptions[i], mqttClient);
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

  publishMessage(message: WsTableMessage) {
    this.mqttClient.publish(message.topic, message.payload, message.options);
    const publishMessage = {
      id: guid(),
      createdTime: this.nowTs(),
      topic: message.topic,
      payload: message.payload,
      qos: message.options.qos,
      retain: message.options.retain,
      properties: message.options.properties,
      type: 'published'
    };
    this.addMessage(publishMessage, this.getActiveConnectionId());
  }

  private addMessage(message: WsTableMessage, clientId: string) {
    if (!this.connectionMessagesMap.has(clientId)) {
      this.connectionMessagesMap.set(clientId, []);
    }
    const clientMessages = this.connectionMessagesMap.get(clientId);
    if (clientMessages.length >= 1000) {
      clientMessages.pop();
    }
    clientMessages.unshift(message);
    this.updateMessages();
  }

  private updateMessages() {
    this.messages$.next();
  }

  private subscribeForTopic(mqttClient: MqttClient, topicObject: any) {
    mqttClient.subscribe(topicObject);
  }

  private getActiveConnectionId() {
    return this.connection$.value.id;
  }

  private getActiveMqttJsClient() {
    return this.connectionMqttClientMap.get(this.getActiveConnectionId());
  }

  addSubscription(value: WsSubscription) {
    this.subscription$.next(value);
    this.subscriptions.push(value);
    this.subscriptions$.next(this.subscriptions);
  }

  selectConnection(connection: Connection) {
    this.connection$.next(connection);
    const connectionState = this.connectionStatusMap.get(this.getActiveConnectionId());
    this.connectionStatus$.next(connectionState);
  }

  public getMessages(pageLink: PageLink): Observable<PageData<WsTableMessage>> {
    const sortOrder = pageLink.sortOrder;
    const data = this.connectionMessagesMap.get(this.getActiveConnectionId());
    let filteredMessages = [];
    if (data) {
      filteredMessages = data.filter(item => {
        let typeMatch = this.messagesFilter.type !== 'all' ? this.messagesFilter.type === item.type : true;
        let topicMatch = this.messagesFilter.topic?.length ? item.topic.indexOf(this.messagesFilter.topic) > -1 : true;
        let qosMatch = this.messagesFilter.qosList?.length ? this.messagesFilter.qosList.includes(item.qos) : true;
        let retainMatch = this.messagesFilter.retainList?.length ? this.messagesFilter.retainList.includes(item.retain) : true;
        return typeMatch && topicMatch && qosMatch && retainMatch;
      });
    }
    const pageData = emptyPageData<WsTableMessage>();
    filteredMessages.sort(function(objA, objB) {
      const sortKey = sortOrder.property;
      if (isNumber(objA[sortKey]) || typeof (objA[sortKey]) === 'boolean') {
        if (sortOrder.direction === 'ASC') {
          return objA[sortKey] - objB[sortKey];
        } else {
          return objB[sortKey] - objA[sortKey];
        }
      } else {
        const propA = objA[sortKey].toLowerCase(); // ignore upper and lowercase
        const propB = objB[sortKey].toLowerCase(); // ignore upper and lowercase
        if (sortOrder.direction === 'ASC') {
          if (propA < propB) {
            return -1;
          }
          if (propA > propB) {
            return 1;
          }
          return 0;
        } else {
          if (propA > propB) {
            return -1;
          }
          if (propA < propB) {
            return 1;
          }
          return 0;
        }
      }
    });
    pageData.data = [...filteredMessages];
    return of(pageData);
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

  public saveSubscriptionV3(entityId: string, subscription: WsSubscription = null, isEdit: boolean, config?: RequestConfig): Observable<WsSubscription> {
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
    const connectionId = this.getActiveConnectionId();
    this.connectionMessagesMap.set(connectionId, []);
    this.updateMessages(this.connectionMessagesMap.get(connectionId));
  }

}
