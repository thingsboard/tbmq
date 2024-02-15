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
import { BehaviorSubject, Observable, of } from 'rxjs';
import { emptyPageData, PageData } from '@shared/models/page/page-data';
import {
  ConnectionStatus,
  ConnectionStatusLog,
  DataSizeUnitType, DisconnectReasonCodes, MessageFilterConfig, MessageFilterDefaultConfig,
  transformObjectToProps,
  transformPropsToObject,
  WebSocketConnection, WebSocketConnectionDto,
  WebSocketSubscription,
  WebSocketTimeUnit,
  WsTableMessage
} from '@shared/models/ws-client.model';
import mqtt, { IClientOptions, IClientPublishOptions, IConnackPacket, IDisconnectPacket, IPublishPacket, MqttClient } from 'mqtt';
import { ErrorWithReasonCode } from 'mqtt/src/lib/shared';
import { convertDataSizeUnits, convertTimeUnits, guid, isDefinedAndNotNull, isNotEmptyStr, isNumber } from '@core/utils';
import { PageLink } from '@shared/models/page/page-link';
import { Buffer } from 'buffer';
import { WebSocketSubscriptionService } from '@core/http/ws-subscription.service';
import { TranslateService } from '@ngx-translate/core';

@Injectable({
  providedIn: 'root'
})
export class MqttJsClientService {

  private messagesList: WsTableMessage[] = [];
  private connectionSubject$ = new BehaviorSubject<WebSocketConnection>(null);
  private connectionsSubject$ = new BehaviorSubject<boolean>(true);
  private connectionStatusSubject$ = new BehaviorSubject<ConnectionStatusLog>({status: ConnectionStatus.DISCONNECTED, details: null});
  private messagesSubject$ = new BehaviorSubject<WsTableMessage[]>(this.messagesList);
  private messageCounterSubject$ = new BehaviorSubject<number>(0);

  public connection$ = this.connectionSubject$.asObservable();
  public connections$ = this.connectionsSubject$.asObservable();
  public connectionStatus$ = this.connectionStatusSubject$.asObservable();
  public messages$ = this.messagesSubject$.asObservable();
  public messageCounter = this.messageCounterSubject$.asObservable();
  public connectionStatusLogMap = new Map<string, ConnectionStatusLog[]>();

  private connectionMqttClientMap = new Map<string, MqttClient>();
  private connectionStatusMap = new Map<string, ConnectionStatusLog>();
  private mqttClientIdSubscriptionsMap = new Map<string, any[]>();
  private mqttClientConnectionMap = new Map<string, WebSocketConnection>();
  private connectionMessagesMap = new Map<string, WsTableMessage[]>();

  private publishMsgStartTs = null;
  private publishMsgDelay = 1000;
  private publishMsgTimeout = null;

  private messagesFilter: MessageFilterConfig = MessageFilterDefaultConfig;

  constructor(private webSocketSubscriptionService: WebSocketSubscriptionService,
              private translate: TranslateService) {
  }

  public onConnectionsUpdated(selectFirst: boolean = true) {
    this.connectionsSubject$.next(selectFirst);
  }

  public connectClient(connection: WebSocketConnection, password: string = null) {
    this.addMqttClient(connection, password);
  }

  public disconnectActiveConnectedClient() {
    const mqttClient = this.getActiveMqttJsClient();
    if (mqttClient.connected) {
      const connection = this.mqttClientConnectionMap.get(mqttClient.options.clientId);
      this.setDisconnectedStatusEndSession(connection, mqttClient);
    }
  }

  public findAndDisconnectClientByConnection(connection: WebSocketConnectionDto) {
    const mqttClient = this.connectionMqttClientMap.get(connection.id);
    if (mqttClient?.connected) {
      this.setDisconnectedStatusEndSession(connection, mqttClient);
    }
  }

  public isConnectionConnected(connectionId: string): boolean {
    return this.connectionStatusMap.get(connectionId)?.status === ConnectionStatus.CONNECTED;
  }

  public filterMessages(filter: MessageFilterConfig) {
    this.messagesFilter = {...this.messagesFilter, ...filter};
    this.updateMessages();
  }

  public subscribeForTopicActiveMqttJsClient(subscription: WebSocketSubscription) {
    const mqttClient: MqttClient = this.getActiveMqttJsClient();
    if (mqttClient) {
      const topic = subscription.configuration.topicFilter;
      const qos = subscription.configuration.qos;
      const nl = subscription.configuration?.options?.noLocal;
      const rap = subscription.configuration?.options?.retainAsPublish;
      const rh = subscription.configuration?.options?.retainHandling;
      const topicObject = {
        [topic]: {qos, nl, rap, rh}
      };
      if (!this.mqttClientIdSubscriptionsMap.has(mqttClient.options.clientId)) {
        this.mqttClientIdSubscriptionsMap.set(mqttClient.options.clientId, []);
      }
      const currentSubscription = this.mqttClientIdSubscriptionsMap.get(mqttClient.options.clientId);
      currentSubscription.push(subscription);
      this.subscribeForTopic(mqttClient, topicObject);
    }
  }

  public unsubscribeForTopicActiveMqttJsClient(prevSubscription: WebSocketSubscription, currentSubscription: WebSocketSubscription = null) {
    const mqttClient: MqttClient = this.getActiveMqttJsClient();
    if (mqttClient) {
      const topic = prevSubscription.configuration.topicFilter;
      const clientSubscriptions = this.mqttClientIdSubscriptionsMap.get(mqttClient.options.clientId);
      const prevSubscriptionIndex = clientSubscriptions.findIndex(el => el.id === prevSubscription.id);
      if (currentSubscription && clientSubscriptions[prevSubscriptionIndex].configuration.color !== currentSubscription.configuration.color) {
        this.changeSubscriptionColor(currentSubscription);
      }
      clientSubscriptions.splice(prevSubscriptionIndex, 1);
      mqttClient.unsubscribe(topic);
    }
  }

  public publishMessage(topic: string, payload: string, options: IClientPublishOptions) {
    let properties;
    // @ts-ignore
    let color = options.color;
    if (isDefinedAndNotNull(options?.properties)) properties = JSON.parse(JSON.stringify(options?.properties));
    const message: any = {
      topic: topic,
      payload,
      qos: options.qos,
      retain: options.retain,
      color,
      properties,
      id: guid(),
      createdTime: this.nowTs(),
      type: 'published'
    };

    this.addMessage(message, this.getActiveConnectionId());

    if (isDefinedAndNotNull(options?.properties?.correlationData)) options.properties.correlationData = Buffer.from(options.properties.correlationData);
    if (isDefinedAndNotNull(options?.properties?.userProperties)) {
      // @ts-ignore
      options.properties.userProperties = transformPropsToObject(options.properties.userProperties);
    }
    // @ts-ignore
    if (isDefinedAndNotNull(options?.properties?.messageExpiryInterval)) options.properties.messageExpiryInterval = convertTimeUnits(options.properties.messageExpiryInterval, options.properties.messageExpiryIntervalUnit, WebSocketTimeUnit.SECONDS)
    // @ts-ignore
    if (isDefinedAndNotNull(options?.properties?.messageExpiryIntervalUnit)) delete options.properties.messageExpiryIntervalUnit;
    this.getActiveMqttJsClient().publish(topic, payload, options);
  }

  public clearHistory() {
    const connectionId = this.getActiveConnectionId();
    this.connectionMessagesMap.set(connectionId, []);
    this.updateMessages();
  }

  public selectConnection(connection: WebSocketConnection) {
    this.connectionSubject$.next(connection);
    const connectionStatus = this.connectionStatusMap.get(this.getActiveConnectionId());
    this.connectionStatusSubject$.next(connectionStatus);
    this.updateMessages();
  }

  public disconnectAllConnectedClients() {
    for (let mqttClient of this.connectionMqttClientMap.values()) {
      if (mqttClient?.connected) {
        const connection = this.mqttClientConnectionMap.get(mqttClient.options.clientId);
        this.setDisconnectedStatusEndSession(connection, mqttClient);
      }
    }
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
        const propA = objA[sortKey].toLowerCase();
        const propB = objB[sortKey].toLowerCase();
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
    this.messageCounterSubject$.next(pageData?.data?.length);
    return of(pageData);
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
      // rejectUnauthorized: false
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
        if (isDefinedAndNotNull(connection.configuration.userProperties)) {
          // @ts-ignore
          options.will.properties.userProperties = transformPropsToObject(connection.configuration.userProperties);
        }
      }
    }
    const mqttClient: MqttClient = mqtt.connect(connection.configuration.url, options);
    this.manageMqttClientCallbacks(mqttClient, connection);
  }

  private setDisconnectedStatusEndSession(connection: WebSocketConnection | WebSocketConnectionDto, mqttClient: MqttClient) {
    this.setConnectionStatus(connection, ConnectionStatus.DISCONNECTED);
    this.setConnectionLog(connection, ConnectionStatus.DISCONNECTED);
    mqttClient.end();
  }

  private manageMqttClientCallbacks(mqttClient: MqttClient, connection: WebSocketConnection) {
    mqttClient.on('connect', (packet: IConnackPacket) => {
      this.connectionMqttClientMap.set(connection.id, mqttClient);
      this.mqttClientConnectionMap.set(mqttClient.options.clientId, connection);
      this.subscribeForTopicsOnConnect(mqttClient, connection);
      this.setConnectionStatus(connection, ConnectionStatus.CONNECTED);
      this.setConnectionLog(connection, ConnectionStatus.CONNECTED);
      console.log(`Client connected!`, packet, mqttClient);
    });
    mqttClient.on('message', (topic: string, payload: Buffer, packet: IPublishPacket) => {
      const subscriptions = this.mqttClientIdSubscriptionsMap.get(mqttClient.options.clientId);
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
        topic: topic,
        qos: packet.qos,
        createdTime: this.nowTs(),
        retain: packet.retain,
        color,
        type: 'received'
      };
      if (isDefinedAndNotNull(packet?.properties)) {
        const properties = JSON.parse(JSON.stringify(packet?.properties));
        if (isDefinedAndNotNull(properties.userProperties)) {
          properties.userProperties = transformObjectToProps(properties.userProperties);
        }
        if (isDefinedAndNotNull(properties.correlationData)) {
          properties.correlationData = Buffer.from(properties.correlationData.data).toString();
        }
        message.properties = properties;
      }
      const connectionId = this.mqttClientConnectionMap.get(mqttClient.options.clientId)?.id;
      this.addMessage(message, connectionId);
      console.log(`Received Message: ${payload} On topic: ${topic}`, packet);
    });
    mqttClient.on('error', (error: Error | ErrorWithReasonCode) => {
      const details = error.message.split(':')[1] || error.message;
      this.setConnectionStatus(connection, ConnectionStatus.CONNECTION_FAILED, details);
      this.setConnectionLog(connection, ConnectionStatus.CONNECTION_FAILED, details);
      mqttClient.end();
      console.log('Connection error: ', error);
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
      console.log('Disconnecting...', mqttClient);
    });
    mqttClient.on('offline', () => {
      console.log('Offline...', mqttClient);
    });
    mqttClient.on('end', () => {
      const connectionId = this.mqttClientConnectionMap.get(mqttClient.options.clientId)?.id;
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
    mqttClient.on('packetreceive', (packet: IDisconnectPacket) => {
      if (packet.cmd == 'disconnect') {
        const reason = DisconnectReasonCodes[packet.reasonCode];
        this.setConnectionStatus(connection, ConnectionStatus.CONNECTION_FAILED, reason);
        this.setConnectionLog(connection, ConnectionStatus.CONNECTION_FAILED, reason);
      }
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

  private setConnectionStatus(connection: WebSocketConnection | WebSocketConnectionDto, status: ConnectionStatus, details: string = null) {
    this.connectionStatusMap.set(connection.id, {
      status,
      details
    });
    this.connectionStatusSubject$.next(this.connectionStatusMap.get(connection.id));
  }

  private setConnectionLog(connection: WebSocketConnection | WebSocketConnectionDto, status: ConnectionStatus, details: string = null) {
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

  private subscribeForTopicsOnConnect(mqttClient: MqttClient, connection: WebSocketConnection) {
    this.webSocketSubscriptionService.getWebSocketSubscriptions(connection.id).subscribe(
      webSocketSubscriptions => {
        const subscriptions = [];
        const topicObject: any = {};
        for (let i = 0; i < webSocketSubscriptions?.length; i++) {
          const subscription = webSocketSubscriptions[i];
          const topic = subscription.configuration.topicFilter;
          const qos = subscription.configuration.qos;
          const nl = subscription.configuration?.options?.noLocal;
          const rap = subscription.configuration?.options?.retainAsPublish;
          const rh = subscription.configuration?.options?.retainHandling;
          topicObject[topic] = {qos, nl, rap, rh};
          subscriptions.push(subscription);
        }
        this.mqttClientIdSubscriptionsMap.set(mqttClient.options.clientId, subscriptions);
        this.subscribeForTopic(mqttClient, topicObject);
      }
    );
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

  private addMessage(message: WsTableMessage, clientId: string) {
    if (!this.connectionMessagesMap.has(clientId)) {
      this.connectionMessagesMap.set(clientId, []);
    }
    const clientMessages = this.connectionMessagesMap.get(clientId);
    if (clientMessages.length >= 50) {
      clientMessages.pop();
    }
    clientMessages.unshift(message);
    if (message.type === 'received') {
      if (!this.publishMsgStartTs) {
        clearTimeout(this.publishMsgTimeout);
        this.publishMsgStartTs = this.nowTs();
        this.publishMsgTimeout = null;
        this.updateMessages();
      } else if (message.createdTime >= this.publishMsgStartTs + this.publishMsgDelay) {
        clearTimeout(this.publishMsgTimeout);
        this.publishMsgStartTs = null;
        this.publishMsgTimeout = null;
        this.updateMessages();
      } else {
        if (!isDefinedAndNotNull(this.publishMsgTimeout)) {
          this.publishMsgTimeout = setTimeout(() => {
            this.updateMessages();
          }, this.publishMsgDelay);
        }
      }
    } else {
      this.updateMessages();
    }
  }

  private updateMessages() {
    this.messagesSubject$.next(null);
  }

  private subscribeForTopic(mqttClient: MqttClient, topicObject: any) {
    mqttClient.subscribe(topicObject);
  }

  private getActiveConnectionId(): string {
    return this.connectionSubject$?.value?.id;
  }

  private getActiveMqttJsClient(): MqttClient {
    return this.connectionMqttClientMap.get(this.getActiveConnectionId());
  }

}
