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
import { BehaviorSubject, Observable, of, Subject } from 'rxjs';
import { PageData } from '@shared/models/page/page-data';
import {
  clientUserNameRandom,
  ConnectionStatus,
  ConnectionStatusLog,
  DataSizeUnitType,
  DisconnectReasonCodes,
  MessageCounter, MessageCounterEmpty,
  MessageFilterConfig,
  MessageFilterDefaultConfigAll,
  QoS,
  transformObjectToProps,
  transformPropsToObject,
  WebSocketConnection,
  WebSocketConnectionDto,
  WebSocketSubscription,
  WebSocketTimeUnit,
  WsTableMessage
} from '@shared/models/ws-client.model';
import mqtt, {
  IClientOptions,
  IClientPublishOptions,
  IConnackPacket,
  IPublishPacket,
  MqttClient,
  Packet
} from 'mqtt';
import { ErrorWithReasonCode } from 'mqtt/src/lib/shared';
import { convertDataSizeUnits, convertTimeUnits, guid, isDefinedAndNotNull, isNotEmptyStr, isNumber, isUndefined } from '@core/utils';
import { PageLink } from '@shared/models/page/page-link';
import { Buffer } from 'buffer';
import { WebSocketSubscriptionService } from '@core/http/ws-subscription.service';
import { ClientSessionService } from '@core/http/client-session.service';
import { ConnectionState } from '@shared/models/session.model';
import { WebSocketSettings } from '@shared/models/settings.models';
import { IClientSubscribeOptions } from 'mqtt/src/lib/client';

@Injectable({
  providedIn: 'root'
})
export class MqttJsClientService {

  private messagesList: WsTableMessage[] = [];
  private connectionSubject$ = new BehaviorSubject<WebSocketConnection>(null);
  private connectionsSubject$ = new BehaviorSubject<boolean>(true);
  private connectionStatusSubject$ = new BehaviorSubject<ConnectionStatusLog>({status: ConnectionStatus.DISCONNECTED, details: null});
  private messagesSubject$ = new BehaviorSubject<WsTableMessage[]>(this.messagesList);
  private messageCounterSubject$ = new BehaviorSubject<MessageCounter>(MessageCounterEmpty);
  private clientConnectingSubject$ = new Subject<void>();
  private logsUpdatedSubject$ = new Subject<void>();

  public connection$ = this.connectionSubject$.asObservable();
  public connections$ = this.connectionsSubject$.asObservable();
  public connectionStatus$ = this.connectionStatusSubject$.asObservable();
  public messages$ = this.messagesSubject$.asObservable();
  public messageCounter = this.messageCounterSubject$.asObservable();
  public clientConnecting = this.clientConnectingSubject$.asObservable();
  public logsUpdated = this.logsUpdatedSubject$.asObservable();

  public connectionStatusLogMap = new Map<string, ConnectionStatusLog[]>();

  private connectionMqttClientMap = new Map<string, MqttClient>();
  private connectionStatusMap = new Map<string, ConnectionStatusLog>();
  private mqttClientIdSubscriptionsMap = new Map<string, any[]>();
  private mqttClientConnectionMap = new Map<string, WebSocketConnection>();
  private connectionMessagesMap = new Map<string, WsTableMessage[]>();
  private connectionMessageCounterMap = new Map<string, MessageCounter>();

  private publishMsgDelay = 200;
  private publishMsgStartTs = null;
  private publishMsgTimeout = null;

  private messagesFilter: MessageFilterConfig = MessageFilterDefaultConfigAll;
  private webSocketSettings: WebSocketSettings;

  constructor(private webSocketSubscriptionService: WebSocketSubscriptionService,
              private clientSessionService: ClientSessionService) {
  }

  public onConnectionsUpdated(selectFirst: boolean = true) {
    this.connectionsSubject$.next(selectFirst);
  }

  public connectClient(connection: WebSocketConnection, password: string = null, notifyClientConnecting: boolean = true) {
    if (notifyClientConnecting) {
      this.notifyClientConnecting();
    }
    const connectionWithSameClientId = this.mqttClientConnectionMap.get(connection.configuration.clientId);
    this.clientSessionService.getDetailedClientSessionInfo(connection.configuration.clientId, {ignoreErrors: true}).subscribe(
      (session) => {
        if (session?.connectionState === ConnectionState.CONNECTED && connection.id !== connectionWithSameClientId?.id) {
          this.updateConnectionStatusLog(connection, ConnectionStatus.CONNECTION_FAILED, 'Client with such ID is already connected');
        } else {
          this.addMqttClient(connection, password);
        }
      },
      () => {
        this.addMqttClient(connection, password);
      }
    );
  }

  public notifyClientConnecting() {
    this.clientConnectingSubject$.next();
  }

  public disconnectClient(connection: WebSocketConnectionDto = null) {
    const mqttClient = isDefinedAndNotNull(connection)
      ? this.connectionMqttClientMap.get(connection.id)
      : this.getSelectedMqttJsClient();
    const clientConnection = isDefinedAndNotNull(connection)
      ? connection
      : this.mqttClientConnectionMap.get(mqttClient?.options?.clientId);
    if (isDefinedAndNotNull(mqttClient)) {
      this.updateConnectionStatusLog(clientConnection, ConnectionStatus.DISCONNECTED);
      this.endMqttClient(mqttClient);
    }
  }

  public isConnectionConnected(connectionId: string): boolean {
    return this.connectionStatusMap.get(connectionId)?.status === ConnectionStatus.CONNECTED;
  }

  public filterMessages(filter: MessageFilterConfig) {
    this.messagesFilter = {...this.messagesFilter, ...filter};
    this.updateMessages();
  }

  public subscribeWebSocketSubscription(subscription: WebSocketSubscription) {
    const mqttClient: MqttClient = this.getSelectedMqttJsClient();
    if (mqttClient) {
      const topic = subscription.configuration.topicFilter;
      const options = this.subscriptionOptions(subscription);
      if (!this.mqttClientIdSubscriptionsMap.has(mqttClient.options.clientId)) {
        this.mqttClientIdSubscriptionsMap.set(mqttClient.options.clientId, []);
      }
      const currentSubscription = this.mqttClientIdSubscriptionsMap.get(mqttClient.options.clientId);
      currentSubscription.push(subscription);
      this.subscribeMqttClient(mqttClient, topic, options);
    }
  }

  public unsubscribeWebSocketSubscription(initSubscription: WebSocketSubscription, currentSubscription: WebSocketSubscription = null) {
    const mqttClient: MqttClient = this.getSelectedMqttJsClient();
    if (mqttClient) {
      const topic = initSubscription.configuration.topicFilter;
      const clientSubscriptions = this.mqttClientIdSubscriptionsMap.get(mqttClient.options.clientId);
      const prevSubscriptionIndex = clientSubscriptions.findIndex(el => el.id === initSubscription.id);
      if (currentSubscription && clientSubscriptions[prevSubscriptionIndex].configuration.color !== currentSubscription.configuration.color) {
        this.changeSubscriptionColor(currentSubscription);
      }
      clientSubscriptions.splice(prevSubscriptionIndex, 1);
      this.unsubscribeMqttClient(mqttClient, topic);
    }
  }

  public publishMessage(topic: string, payload: string, options: IClientPublishOptions) {
    let properties;
    // @ts-ignore
    let color = options.color;
    if (isDefinedAndNotNull(options?.properties)) properties = JSON.parse(JSON.stringify(options?.properties));
    const message: any = {
      topic,
      payload,
      qos: options.qos,
      retain: options.retain,
      color,
      properties,
      id: guid(),
      createdTime: this.nowTs(),
      type: 'published'
    };

    if (isDefinedAndNotNull(options?.properties?.correlationData)) options.properties.correlationData = Buffer.from(options.properties.correlationData);
    if (isDefinedAndNotNull(options?.properties?.userProperties)) {
      // @ts-ignore
      options.properties.userProperties = transformPropsToObject(options.properties.userProperties);
    }
    // @ts-ignore
    if (isDefinedAndNotNull(options?.properties?.messageExpiryInterval)) options.properties.messageExpiryInterval = convertTimeUnits(options.properties.messageExpiryInterval, options.properties.messageExpiryIntervalUnit, WebSocketTimeUnit.SECONDS)
    // @ts-ignore
    if (isDefinedAndNotNull(options?.properties?.messageExpiryIntervalUnit)) delete options.properties.messageExpiryIntervalUnit;
    const logUnknownPubErrorTimeout = setTimeout(function() {
      console.log(`Unknown error on publish message with topic '${topic}' \n${JSON.stringify(options)}`);
    }, 3000);
    this.getSelectedMqttJsClient().publish(topic, payload, options, (error, packet) => {
      if (!error || !error?.message?.length) {
        clearTimeout(logUnknownPubErrorTimeout);
        this.addMessage(message, this.getSelectedConnectionId());
      }
      if (error?.message?.length) {
        console.log(`Unexpected error on publish message \nTopic: '${topic}' \nError: ${error.message}`);
      }
    });
  }

  public clearMessages() {
    this.clearHistory(this.getSelectedConnectionId());
  }

  public clearAllMessages() {
    for (const clientId of this.connectionMqttClientMap.keys()) {
      console.log('clientId', clientId)
      this.clearHistory(clientId);
    }
  }

  private clearHistory(connectionId: string) {
    this.connectionMessagesMap.set(connectionId, []);
    this.connectionMessageCounterMap.delete(connectionId);
    this.updateMessages();
    this.updateMessageCounter();
  }

  public selectConnection(connection: WebSocketConnection) {
    this.connectionSubject$.next(connection);
    const connectionStatus = this.connectionStatusMap.get(this.getSelectedConnectionId());
    this.connectionStatusSubject$.next(connectionStatus);
    this.updateMessages();
    this.updateMessageCounter();
  }

  public getMessages(pageLink: PageLink): Observable<PageData<WsTableMessage>> {
    const sortOrder = pageLink.sortOrder;
    const data = this.connectionMessagesMap.get(this.getSelectedConnectionId());
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
    const pageData = pageLink.filterData(filteredMessages);
    this.updateMessageCounter();
    return of(pageData);
  }

  public setWebSocketSettings(settings: WebSocketSettings) {
    this.webSocketSettings = settings;
  }

  private addMqttClient(connection: WebSocketConnection, password: string) {
    const options: IClientOptions = {
      clientId: connection.configuration.clientId,
      username: connection.configuration.username || clientUserNameRandom(),
      password,
      protocolVersion: connection.configuration.mqttVersion,
      protocolId: connection.configuration.mqttVersion === 3 ? 'MQIsdp' : 'MQTT',
      clean: connection.configuration.cleanStart,
      keepalive: convertTimeUnits(connection.configuration.keepAlive, connection.configuration.keepAliveUnit, WebSocketTimeUnit.SECONDS),
      connectTimeout: convertTimeUnits(connection.configuration.connectTimeout, connection.configuration.connectTimeoutUnit, WebSocketTimeUnit.MILLISECONDS),
      reconnectPeriod: convertTimeUnits(connection.configuration.reconnectPeriod, connection.configuration.reconnectPeriodUnit, WebSocketTimeUnit.MILLISECONDS),
      rejectUnauthorized: connection.configuration.rejectUnauthorized
    };
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
          correlationData: Buffer.from(connection.configuration.lastWillMsg.correlationData || '')
        };
        if (isDefinedAndNotNull(connection.configuration.userProperties)) {
          // @ts-ignore
          options.will.properties.userProperties = transformPropsToObject(connection.configuration.userProperties);
        }
      }
    }
    const mqttClient: MqttClient = mqtt.connect(connection.configuration.url, options);
    this.subscribeForWebSocketSubscriptions(mqttClient, connection);
  }

  private manageMqttClientCallbacks(mqttClient: MqttClient, connection: WebSocketConnection) {
    this.connectionMqttClientMap.set(connection.id, mqttClient);
    this.mqttClientConnectionMap.set(mqttClient.options.clientId, connection);
    mqttClient.on('connect', (packet: IConnackPacket) => {
      this.updateConnectionStatusLog(connection, ConnectionStatus.CONNECTED);
      this.logMqttClienEvent('connect', [packet, mqttClient]);
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
        topic,
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
      this.logMqttClienEvent('message', [packet, topic, payload, mqttClient]);
    });
    mqttClient.on('error', (error: Error | ErrorWithReasonCode) => {
      const reason = error.message.split(':')[1] || error.message;
      this.updateConnectionStatusLog(connection, ConnectionStatus.CONNECTION_FAILED, reason);
      this.endMqttClient(mqttClient);
      this.logMqttClienEvent('error', [error, mqttClient]);
    });
    mqttClient.on('reconnect', () => {
      this.updateConnectionStatusLog(connection, ConnectionStatus.RECONNECTING);
      this.logMqttClienEvent('reconnect', [mqttClient]);
    });
    mqttClient.on('end', () => {
      const connectionId = this.mqttClientConnectionMap.get(mqttClient.options.clientId)?.id;
      if (this.connectionMqttClientMap.has(connectionId)) {
        this.connectionMqttClientMap.delete(connectionId);
      }
      this.logMqttClienEvent('end', [mqttClient]);
    });
    mqttClient.on('packetreceive', (packet: Packet) => {
      if (packet.cmd == 'disconnect') {
        const reason = DisconnectReasonCodes[packet.reasonCode];
        this.updateConnectionStatusLog(connection, ConnectionStatus.CONNECTION_FAILED, reason);
      }
      this.logMqttClienEvent('packetreceive', [packet, mqttClient]);
    });
    mqttClient.on('packetsend', (packet: IConnackPacket) => {
      this.logMqttClienEvent('packetsend', [packet, mqttClient]);
    });
    mqttClient.on('close', () => {
      this.logMqttClienEvent('close', [mqttClient]);
    });
    mqttClient.on('disconnect', () => {
      this.logMqttClienEvent('disconnect', [mqttClient]);
    });
    mqttClient.on('offline', () => {
      this.logMqttClienEvent('offline', [mqttClient]);
    });
    mqttClient.on('outgoingEmpty', () => {
      this.logMqttClienEvent('outgoingEmpty', [mqttClient]);
    });
  }

  private logMqttClienEvent(type: string, details?: any) {
    if (this.webSocketSettings?.isLoggingEnabled) {
      console.log(`MQTT log on event '${type}'`, details?.length ? [...details] : null);
    }
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

  private updateConnectionStatusLog(connection: WebSocketConnection | WebSocketConnectionDto, status: ConnectionStatus, details: string = null) {
    this.updateConnectionStatus(connection, status, details);
    this.updateConnectionLog(connection, status, details);
  }

  private updateConnectionStatus(connection: WebSocketConnection | WebSocketConnectionDto, status: ConnectionStatus, details: string = null) {
    this.connectionStatusMap.set(connection.id, {
      status,
      details
    });
    if (this.getSelectedConnectionId() === connection.id) {
      this.connectionStatusSubject$.next(this.connectionStatusMap.get(connection.id));
    }
  }

  private updateConnectionLog(connection: WebSocketConnection | WebSocketConnectionDto, status: ConnectionStatus, details: string = null) {
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
    this.logsUpdatedSubject$.next();
  }

  private nowTs() {
    return Date.now();
  }

  private subscribeForWebSocketSubscriptions(mqttClient: MqttClient, connection: WebSocketConnection) {
    this.webSocketSubscriptionService.getWebSocketSubscriptions(connection.id).subscribe(
      webSocketSubscriptions => {
        const subscriptions = [];
        for (let i = 0; i < webSocketSubscriptions?.length; i++) {
          const subscription = webSocketSubscriptions[i];
          const topic = subscription.configuration.topicFilter;
          const options = this.subscriptionOptions(subscription);
          subscriptions.push(subscription);
          this.subscribeMqttClient(mqttClient, topic, options);
        }
        this.mqttClientIdSubscriptionsMap.set(mqttClient.options.clientId, subscriptions);
        this.manageMqttClientCallbacks(mqttClient, connection);
      }
    );
  }

  private changeSubscriptionColor(subscription: WebSocketSubscription) {
    const clientMessages = this.connectionMessagesMap.get(this.getSelectedConnectionId());
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
    const messageType = message.type;
    const clientMessages = this.connectionMessagesMap.get(clientId);
    clientMessages.unshift(message);
    const maxMessages = this.webSocketSettings?.maxMessages;
    if (clientMessages.length > maxMessages) {
      clientMessages.pop();
    }
    if (!this.connectionMessageCounterMap.has(clientId)) {
      this.connectionMessageCounterMap.set(clientId, MessageCounterEmpty);
    }
    const counter = this.connectionMessageCounterMap.get(clientId);
    const newCounter = {
      ...counter,
      ...{
        all: counter.all + 1,
        [messageType]: counter[messageType] + 1
      }
    };
    this.connectionMessageCounterMap.set(clientId, newCounter);
    if (messageType === 'received') {
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

  private subscribeMqttClient(mqttClient: MqttClient, topic: string, options: IClientSubscribeOptions) {
    mqttClient.subscribe(topic, options);
  }

  private unsubscribeMqttClient(mqttClient: MqttClient, topic: string) {
    mqttClient.unsubscribe(topic);
  }

  private endMqttClient(mqttClient: MqttClient) {
    if (mqttClient) {
      mqttClient.end();
    }
  }

  private getSelectedConnectionId(): string {
    return this.connectionSubject$?.value?.id;
  }

  private getSelectedMqttJsClient(): MqttClient {
    return this.connectionMqttClientMap.get(this.getSelectedConnectionId());
  }

  private updateMessageCounter() {
    const connectionId = this.getSelectedConnectionId();
    if (!this.connectionMessageCounterMap.has(connectionId)) {
      this.connectionMessageCounterMap.set(connectionId, MessageCounterEmpty);
    }
    const counter = this.connectionMessageCounterMap.get(connectionId);
    this.messageCounterSubject$.next(counter);
  }

  private subscriptionOptions(subscription: WebSocketSubscription): IClientSubscribeOptions {
    const options = {} as IClientSubscribeOptions;
    options.qos = subscription.configuration.qos as QoS;
    options.nl = subscription.configuration?.options?.noLocal;
    options.rap = subscription.configuration?.options?.retainAsPublish;
    options.rh = subscription.configuration?.options?.retainHandling;
    if (isDefinedAndNotNull(subscription.configuration?.subscriptionId)) {
      options.properties = {};
      options.properties.subscriptionIdentifier = subscription.configuration.subscriptionId;
    }
    return options;
  }
}
