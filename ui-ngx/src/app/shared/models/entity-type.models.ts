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

import { BaseData } from '@shared/models/base-data';

export enum EntityType {
  USER = 'USER',
  MQTT_CLIENT_CREDENTIALS = 'MQTT_CLIENT_CREDENTIALS',
  MQTT_SESSION = 'MQTT_SESSION',
  SHARED_SUBSCRIPTION = 'SHARED_SUBSCRIPTION',
  SHARED_SUBSCRIPTION_GROUP = 'SHARED_SUBSCRIPTION_GROUP',
  RETAINED_MESSAGE = 'RETAINED_MESSAGE',
  KAFKA_TOPIC = 'KAFKA_TOPIC',
  KAFKA_CONSUMER_GROUP = 'KAFKA_CONSUMER_GROUP',
  KAFKA_BROKER = 'KAFKA_BROKER',
  WS_CONNECTION = 'WS_CONNECTION',
  SUBSCRIPTION = 'SUBSCRIPTION',
  WS_MESSAGE = 'WS_MESSAGE',
  UNAUTHORIZED_CLIENT = 'UNAUTHORIZED_CLIENT',
}

export interface EntityTypeTranslation {
  type?: string;
  typePlural?: string;
  list?: string;
  nameStartsWith?: string;
  details?: string;
  add?: string;
  noEntities?: string;
  selectedEntities?: string;
  search?: string;
}

export interface EntityTypeResource<T> {
  helpLinkId: string;

  helpLinkIdForEntity?(entity: T): string;
}

export const entityTypeTranslations = new Map<EntityType, EntityTypeTranslation>(
  [
    [
      EntityType.USER,
      {
        type: 'user.type-user',
        typePlural: 'user.type-users',
        list: 'user.list-of-users',
        nameStartsWith: 'user.user-name-starts-with',
        details: 'user.user-details',
        add: 'user.add',
        noEntities: 'user.no-users-text',
        search: 'user.search',
        selectedEntities: 'user.selected-users'
      }
    ],
    [
      EntityType.MQTT_CLIENT_CREDENTIALS,
      {
        type: 'mqtt-client-credentials.type-client-credentials',
        typePlural: 'mqtt-client-credentials.type-clients-credentials',
        list: 'mqtt-client-credentials.list-of-client-credentials',
        nameStartsWith: 'mqtt-client-credentials.client-credentials-name-starts-with',
        details: 'mqtt-client-credentials.client-credentials-details',
        add: 'mqtt-client-credentials.add-client-credentials',
        noEntities: 'mqtt-client-credentials.no-client-credentials-text',
        search: 'mqtt-client-credentials.search',
        selectedEntities: 'mqtt-client-credentials.selected-client-credentials'
      }
    ],
    [
      EntityType.MQTT_SESSION,
      {
        type: 'mqtt-client-session.type-session',
        typePlural: 'mqtt-client-session.type-sessions',
        list: 'mqtt-client-session.list-of-sessions',
        nameStartsWith: 'mqtt-client-session.session-name-starts-with',
        details: 'mqtt-client-session.session-details',
        add: 'mqtt-client-session.add',
        noEntities: 'mqtt-client-session.no-session-text',
        search: 'mqtt-client-session.search',
        selectedEntities: 'mqtt-client-session.selected-sessions'
      }
    ],
    [
      EntityType.SHARED_SUBSCRIPTION,
      {
        type: 'shared-subscription.type-shared-subscription',
        typePlural: 'shared-subscription.type-shared-subscriptions',
        list: 'shared-subscription.list-of-shared-subscriptions',
        nameStartsWith: 'shared-subscription.shared-subscription-name-starts-with',
        details: 'shared-subscription.shared-subscription-details',
        add: 'shared-subscription.add',
        noEntities: 'shared-subscription.no-shared-subscription-text',
        search: 'shared-subscription.search-by-name',
        selectedEntities: 'shared-subscription.selected-shared-subscriptions'
      }
    ],
    [
      EntityType.SHARED_SUBSCRIPTION_GROUP,
      {
        type: 'shared-subscription.type-shared-subscription',
        typePlural: 'shared-subscription.type-shared-subscriptions',
        list: 'shared-subscription.list-of-shared-subscriptions',
        nameStartsWith: 'shared-subscription.shared-subscription-name-starts-with',
        details: 'shared-subscription.shared-subscription-details',
        add: 'shared-subscription.add',
        noEntities: 'shared-subscription.no-shared-subscription-group-text',
        search: 'shared-subscription.search',
        selectedEntities: 'shared-subscription.selected-shared-subscriptions'
      }
    ],
    [
      EntityType.RETAINED_MESSAGE,
      {
        type: 'retained-message.type-retained-message',
        typePlural: 'retained-message.type-retained-messages',
        list: 'retained-message.list-of-retained-messages',
        nameStartsWith: 'retained-message.retained-message-name-starts-with',
        details: 'retained-message.details',
        add: 'retained-message.add',
        noEntities: 'retained-message.no-retained-messages-text',
        search: 'retained-message.search',
        selectedEntities: 'retained-message.selected-retained-messages'
      }
    ],
    [
      EntityType.KAFKA_TOPIC,
      {
        type: 'kafka.topic',
        typePlural: 'kafka.topics',
        list: 'kafka.list-of-topics',
        details: 'details.details',
        noEntities: 'kafka.no-kafka-topic-text',
        search: 'kafka.search-topic',
        selectedEntities: 'kafka.selected-kafka-topic'
      }
    ],
    [
      EntityType.KAFKA_CONSUMER_GROUP,
      {
        type: 'kafka.consumer-group',
        typePlural: 'kafka.consumer-groups',
        list: 'kafka.list-of-consumer-groups',
        details: 'details.details',
        noEntities: 'kafka.no-consumer-groups-text',
        search: 'kafka.search-consumer-group',
        selectedEntities: 'kafka.selected-consumer-groups'
      }
    ],
    [
      EntityType.KAFKA_BROKER,
      {
        type: 'kafka.broker',
        typePlural: 'kafka.brokers',
        list: 'kafka.list-of-brokers',
        nameStartsWith: 'kafka.topic-starts-with',
        details: 'details.details',
        add: 'kafka.add-broker',
        noEntities: 'kafka.no-brokers-text',
        search: 'kafka.search-broker',
        selectedEntities: 'kafka.selected-brokers'
      }
    ],
    [
      EntityType.WS_CONNECTION,
      {
        type: 'ws-client.connections.connection',
        typePlural: 'ws-client.connections.connections',
        list: 'ws-client.connections.list-of-connections',
        nameStartsWith: 'ws-client.connections.connection-starts-with',
        details: 'ws-client.connections.details',
        add: 'ws-client.connections.add-connection',
        noEntities: 'ws-client.connections.no-connections-text',
        search: 'ws-client.connections.search-connection',
        selectedEntities: 'ws-client.connections.selected-connections'
      }
    ],
    [
      EntityType.SUBSCRIPTION,
      {
        type: 'subscription.subscription',
        typePlural: 'subscription.subscription',
        list: 'subscription.list-of-subscriptions',
        nameStartsWith: 'subscription.subscription-starts-with',
        details: 'subscription.details',
        add: 'subscription.add-subscription',
        noEntities: 'subscription.no-subscriptions-text',
        search: 'subscription.search-subscription',
        selectedEntities: 'subscription.selected-subscriptions'
      }
    ],
    [
      EntityType.WS_MESSAGE,
      {
        type: 'ws-client.messages.message',
        typePlural: 'ws-client.messages.messages',
        list: 'ws-client.messages.list-of-messages',
        nameStartsWith: 'ws-client.messages.message-starts-with',
        details: 'ws-client.messages.details',
        add: 'ws-client.messages.add-message',
        noEntities: 'ws-client.messages.no-messages-text',
        search: 'ws-client.messages.search-message',
        selectedEntities: 'ws-client.messages.selected-messages'
      }
    ],
    [
      EntityType.UNAUTHORIZED_CLIENT,
      {
        type: 'unauthorized-client.unauthorized-client',
        typePlural: 'unauthorized-client.unauthorized-clients',
        list: 'unauthorized-client.list-of-unauthorized-clients',
        nameStartsWith: 'unauthorized-client.unauthorized-client-name-starts-with',
        details: 'unauthorized-client.details',
        noEntities: 'unauthorized-client.no-unauthorized-clients-text',
        search: 'unauthorized-client.search',
        selectedEntities: 'unauthorized-client.selected-unauthorized-clients'
      }
    ],
  ]
);

export const entityTypeResources = new Map<EntityType, EntityTypeResource<BaseData>>(
  [
    [
      EntityType.USER,
      {
        helpLinkId: 'users'
      }
    ],
    [
      EntityType.MQTT_CLIENT_CREDENTIALS,
      {
        helpLinkId: 'clientCredentials'
      }
    ],
    [
      EntityType.MQTT_SESSION,
      {
        helpLinkId: 'sessions'
      }
    ],
    [
      EntityType.SHARED_SUBSCRIPTION,
      {
        helpLinkId: 'sharedSubscriptions'
      }
    ],
    [
      EntityType.SHARED_SUBSCRIPTION_GROUP,
      {
        helpLinkId: 'sharedSubscriptions'
      }
    ],
    [
      EntityType.RETAINED_MESSAGE,
      {
        helpLinkId: 'retainedMessages'
      }
    ],
    [
      EntityType.WS_MESSAGE,
      {
        helpLinkId: 'wsMessage'
      }
    ],
    [
      EntityType.UNAUTHORIZED_CLIENT,
      {
        helpLinkId: 'unauthorizedClient'
      }
    ]
  ]
);
