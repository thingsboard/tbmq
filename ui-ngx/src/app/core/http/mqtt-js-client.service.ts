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
import { BehaviorSubject, Observable, of } from 'rxjs';
import { emptyPageData, PageData } from '@shared/models/page/page-data';
import {
  ConnectionStatus,
  ConnectionStatusLog,
  DataSizeUnitType,
  WebSocketConnection,
  WebSocketSubscription,
  WebSocketTimeUnit,
  WsTableMessage
} from '@shared/models/ws-client.model';
import mqtt, { IClientOptions, IClientPublishOptions, IConnackPacket, IPublishPacket, MqttClient } from 'mqtt';
import { ErrorWithReasonCode } from 'mqtt/src/lib/shared';
import { convertDataSizeUnits, convertTimeUnits, guid, isDefinedAndNotNull, isNotEmptyStr, isNumber } from '@core/utils';
import { MessageFilterConfig } from '@home/pages/ws-client/messages/message-filter-config.component';
import { PageLink } from '@shared/models/page/page-link';
import { Buffer } from 'buffer';
import { WebSocketSubscriptionService } from '@core/http/ws-subscription.service';

@Injectable({
  providedIn: 'root'
})
export class MqttJsClientService {

  private messages: WsTableMessage[] = [];

  private connection$ = new BehaviorSubject<WebSocketConnection>(null);
  private messages$ = new BehaviorSubject<WsTableMessage[]>(this.messages);
  private connections$ = new BehaviorSubject<boolean>(true);
  private connectionStatus$ = new BehaviorSubject<ConnectionStatus>(ConnectionStatus.DISCONNECTED);
  private connectionFailedError$ = new BehaviorSubject<string>(null);

  private subscription$ = new BehaviorSubject<any>(null);

  selectedConnection$ = this.connection$.asObservable();
  connectionsUpdated$ = this.connections$.asObservable();
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

  connectionMqttClientMap = new Map<string, MqttClient>();
  connectionStatusMap = new Map<string, ConnectionStatus>();
  connectionStatusLogMap = new Map<string, ConnectionStatusLog[]>();
  clientIdSubscriptionsMap = new Map<string, any[]>();
  mqttClientConnectionMap = new Map<string, string>();

  constructor(private webSocketSubscriptionService: WebSocketSubscriptionService) {
  }

  public onConnectionsUpdated(selectFirst: boolean = true) {
    this.connections$.next(selectFirst);
  }

  connectClient(connection: WebSocketConnection, password: string = null) {
    this.addMqttClient(connection, password);
  }

  disconnectClient(force: boolean = false) {
    if (this.getActiveMqttJsClient().connected) {
      this.getActiveMqttJsClient().end(force);
    }
  }

  public isConnectionConnected(connectionId: string): boolean {
    return this.connectionStatusMap.get(connectionId) === ConnectionStatus.CONNECTED;
  }

  private addMqttClient(connection: WebSocketConnection, password: string) {
    const options: IClientOptions = {
      clientId: connection.configuration.clientId,
      username: connection.configuration.username,
      password,
      protocolVersion: connection.configuration.mqttVersion,
      clean: connection.configuration.cleanStart,
      keepalive: convertTimeUnits(connection.configuration.keepAlive, connection.configuration.keepAliveUnit, WebSocketTimeUnit.SECONDS),
      connectTimeout: convertTimeUnits(connection.configuration.connectTimeout, connection.configuration.connectTimeoutUnit, WebSocketTimeUnit.MILLISECONDS),
      reconnectPeriod: convertTimeUnits(connection.configuration.reconnectPeriod, connection.configuration.reconnectPeriodUnit, WebSocketTimeUnit.MILLISECONDS),
    };
    options.protocolId = options.protocolVersion === 3 ? 'MQIsdp' : 'MQTT';
    if (connection.configuration.mqttVersion === 5) {
      options.properties = {
        sessionExpiryInterval: connection.configuration.sessionExpiryInterval,
        receiveMaximum: connection.configuration.receiveMax,
        maximumPacketSize: convertDataSizeUnits(connection.configuration.maxPacketSize, connection.configuration.maxPacketSizeUnit, DataSizeUnitType.BYTE),
        topicAliasMaximum: connection.configuration.topicAliasMax,
        requestResponseInformation: connection.configuration.requestResponseInfo
      };
      if (isDefinedAndNotNull(connection.configuration.userProperties)) {
        options.properties.userProperties = connection.configuration.userProperties;
      }
    }
    if (isNotEmptyStr(connection.configuration?.lastWillMsg?.topic)) {
      options.will = {
        topic: connection.configuration.lastWillMsg.topic,
        qos: connection.configuration.lastWillMsg.qos,
        retain: connection.configuration.lastWillMsg.retain,
        payload: connection.configuration.lastWillMsg.payload
      };
      if (connection.configuration.mqttVersion === 5) {
        options.will.properties = {
          contentType: connection.configuration.lastWillMsg.contentType || '',
          responseTopic: connection.configuration.lastWillMsg.responseTopic || '',
          willDelayInterval: convertTimeUnits(connection.configuration.lastWillMsg.willDelayInterval, connection.configuration.lastWillMsg.willDelayIntervalUnit, WebSocketTimeUnit.SECONDS),
          messageExpiryInterval: convertTimeUnits(connection.configuration.lastWillMsg.msgExpiryInterval, connection.configuration.lastWillMsg.msgExpiryIntervalUnit, WebSocketTimeUnit.SECONDS),
          payloadFormatIndicator: connection.configuration.lastWillMsg.payloadFormatIndicator,
        };
        // @ts-ignore
        options.will.properties.correlationData = Buffer.from([connection.configuration.lastWillMsg.correlationData]);
      }
    }
    console.log('options', options);
    console.log('connection', connection);
    const mqttClient: MqttClient = mqtt.connect(connection.configuration.url, options);
    this.setMqttClient(mqttClient);
    this.manageMqttClientCallbacks(mqttClient, connection);
  }

  private setMqttClient(mqttClient: MqttClient) {
    this.mqttClient = mqttClient;
    this.mqttClients.push(mqttClient);
  }

  private manageMqttClientCallbacks(mqttClient: MqttClient, connection: WebSocketConnection) {
    mqttClient.on('connect', (packet: IConnackPacket) => {
      this.connectionMqttClientMap.set(connection.id, mqttClient);
      this.mqttClientConnectionMap.set(mqttClient.options.clientId, connection.id);
      this.subscribeForTopicsOnConnect(mqttClient, connection);
      this.setConnectionStatus(connection, ConnectionStatus.CONNECTED);
      this.setConnectionLog(connection, ConnectionStatus.CONNECTED);
      console.log(`Client connected!`, packet, mqttClient);
    });
    mqttClient.on('message', (topic: string, payload: Buffer, packet: IPublishPacket) => {
      const subscriptions = this.clientIdSubscriptionsMap.get(mqttClient.options.clientId);
      const subscription = subscriptions.find(sub => sub.topic === topic);
      let wildcardSubscription;
      let color: string;
      if (subscription) {
        color = subscription.color;
      } else {
        wildcardSubscription = this.findWildcardSubscription(subscriptions, topic);
        if (wildcardSubscription) {
          color = wildcardSubscription.configuration.color;
        } else {
          console.error(`mqttClient ${mqttClient} received message. No matched subscription topic in ${subscriptions} for topic ${topic}`);
        }
      }
      const message: WsTableMessage = {
        id: guid(),
        subscriptionId: subscription?.id || wildcardSubscription?.id,
        payload: payload.toString(),
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
      mqttClient.end();
      console.log('Closing...', mqttClient);
    });
    mqttClient.on('disconnect', () => {
      this.setConnectionStatus(connection, ConnectionStatus.DISCONNECTED);
      this.setConnectionLog(connection, ConnectionStatus.DISCONNECTED);
      console.log('Disconnecting...', mqttClient);
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
      console.log('Packet Send...', packet);
    });
    mqttClient.on('packetreceive', (packet: IConnackPacket) => {
      console.log('Packet Receive...', packet);
    });
  }

  private findWildcardSubscription(subscriptions: WebSocketSubscription[], topic: string): WebSocketSubscription {
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

    function checkTopicInSubscriptions(subscriptions: WebSocketSubscription[], topic: string): WebSocketSubscription {
      for (let subscriptionTopic of subscriptions.map(el => el.configuration.topicFilter)) {
        if (isTopicMatched(subscriptionTopic, topic)) {
          return subscriptions.find(el => el.configuration.topicFilter === subscriptionTopic);
        }
      }
    }

    return checkTopicInSubscriptions(subscriptions, topic);
  }

  private setConnectionFailedError(error: string = null) {
    this.connectionFailedError$.next(error);
  }

  private setConnectionStatus(connection: WebSocketConnection, state: ConnectionStatus) {
    this.connectionStatusMap.set(connection.id, state);
    this.connectionStatus$.next(this.connectionStatusMap.get(connection.id));
  }

  private setConnectionLog(connection: WebSocketConnection, status: ConnectionStatus, details: string = null) {
    const log: ConnectionStatusLog = {
      createdTime: this.nowTs(),
      status,
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

  private subscribeForTopicsOnConnect(mqttClient: MqttClient, connection: WebSocketConnection) {
    this.webSocketSubscriptionService.getWebSocketSubscriptions(connection.id).subscribe(
      webSocketSubscriptions => {
        const subscriptions = [];
        const topicObject: any = {};
        for (let i = 0; i < webSocketSubscriptions?.length; i++) {
          // console.log('Subscribed for topic', subscriptions[i], mqttClient);
          // this.subscribeForTopic(mqttClient, subscriptions[i]);
          const topic = webSocketSubscriptions[i].configuration.topicFilter;
          const qos = webSocketSubscriptions[i].configuration.qos;
          topicObject[topic] = {qos};
          subscriptions.push(webSocketSubscriptions[i]);
        }
        this.clientIdSubscriptionsMap.set(mqttClient.options.clientId, subscriptions);
        this.subscribeForTopic(mqttClient, topicObject);
      }
    );
  }

  subscribeForTopicActiveMqttJsClient(subscription: WebSocketSubscription) {
    const mqttClient: MqttClient = this.getActiveMqttJsClient();
    if (mqttClient) {
      const topic = subscription.configuration.topicFilter;
      const qos = subscription.configuration.qos;
      const topicObject = {
        [topic]: {qos}
      };
      if (!this.clientIdSubscriptionsMap.has(mqttClient.options.clientId)) {
        this.clientIdSubscriptionsMap.set(mqttClient.options.clientId, []);
      }
      const currentSubscription = this.clientIdSubscriptionsMap.get(mqttClient.options.clientId);
      currentSubscription.push(subscription);
      this.subscribeForTopic(mqttClient, topicObject);
    }
  }

  unsubscribeForTopicActiveMqttJsClient(prevSubscription: WebSocketSubscription, currentSubscription: WebSocketSubscription = null) {
    const mqttClient: MqttClient = this.getActiveMqttJsClient();
    if (mqttClient) {
      const topic = prevSubscription.configuration.topicFilter;
      const clientSubscriptions = this.clientIdSubscriptionsMap.get(mqttClient.options.clientId);
      const prevSubscriptionIndex = clientSubscriptions.findIndex(el => el.id === prevSubscription.id);
      if (currentSubscription && clientSubscriptions[prevSubscriptionIndex].configuration.color !== currentSubscription.configuration.color) {
        this.changeSubscriptionColor(currentSubscription);
      }
      clientSubscriptions.splice(prevSubscriptionIndex, 1);
      mqttClient.unsubscribe(topic);
    }
  }

  private changeSubscriptionColor(subscription: WebSocketSubscription) {
    const clientMessages = this.connectionMessagesMap.get(this.getActiveConnectionId());
    if (clientMessages) {
      const subscriptionMessages = clientMessages.filter(el => el?.subscriptionId === subscription.id);
      for (let i = 0; i < subscriptionMessages?.length; i++) {
        const message = subscriptionMessages[i];
        message.color = subscription.configuration.color;
      }
      this.updateMessages();
    }
  }

  publishMessage(topic: string, payload: string, options: IClientPublishOptions) {
    let properties;
    if (isDefinedAndNotNull(options?.properties)) properties = JSON.parse(JSON.stringify(options?.properties));
    const message = {
      topic,
      payload,
      qos: options.qos,
      retain: options.retain,
      properties,
      id: guid(),
      createdTime: this.nowTs(),
      type: 'published'
    };
    this.addMessage(message, this.getActiveConnectionId());

    if (isDefinedAndNotNull(options?.properties?.correlationData)) {
      options.properties.correlationData = Buffer.from(options.properties.correlationData);
    }
    // @ts-ignore
    if (isDefinedAndNotNull(options?.properties?.messageExpiryInterval)) options.properties.messageExpiryInterval = convertTimeUnits(options.properties.messageExpiryInterval, options.properties.messageExpiryIntervalUnit, WebSocketTimeUnit.SECONDS)
    // @ts-ignore
    if (isDefinedAndNotNull(options?.properties?.messageExpiryIntervalUnit)) delete options.properties.messageExpiryIntervalUnit;
    this.mqttClient.publish(topic, payload, options);
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
    this.messages$.next(null);
  }

  private subscribeForTopic(mqttClient: MqttClient, topicObject: any) {
    mqttClient.subscribe(topicObject);
  }

  private getActiveConnectionId() {
    return this.connection$?.value?.id;
  }

  private getActiveMqttJsClient(): MqttClient {
    return this.connectionMqttClientMap.get(this.getActiveConnectionId());
  }

  selectConnection(connection: WebSocketConnection) {
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

  public clearHistory() {
    const connectionId = this.getActiveConnectionId();
    this.connectionMessagesMap.set(connectionId, []);
    this.updateMessages();
  }

}
