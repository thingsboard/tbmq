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

import { BaseData } from '@shared/models/base-data';

export interface KafkaBroker extends BaseData {
  brokerId: string;
  address: string;
  brokerSize: number;
}

export interface KafkaTopic extends BaseData {
  name: string;
  partitions: number;
  replicationFactor: number;
  size: number;
}

export interface KafkaConsumerGroup extends BaseData {
  state: string;
  groupId: string;
  members: number;
  lag: string;
}

export const KafkaTopicsTooltipMap = {
  'tbmq.msg.all': 'All published messages from MQTT clients to the broker',
  'tbmq.msg.app': 'Messages the APPLICATION client should receive based on its subscriptions',
  'tbmq.msg.app.shared': 'Messages related to the APPLICATION shared subscription',
  'tbmq.msg.persisted': 'Messages that DEVICE clients should receive based on their subscriptions',
  'tbmq.msg.retained': 'Retained messages',
  'tbmq.client.subscriptions': 'Client subscriptions',
  'tbmq.client.session': 'Client sessions',
  'tbmq.client.session.event.request': 'Processing client session events like request session connection,  request session cleanup, notify client disconnected, etc',
  'tbmq.client.session.event.response': 'Responses to client session events of the previous topic sent to specific broker node where target client is connected',
  'tbmq.client.disconnect': 'Force client disconnections (by admin request from UI/API or on sessions conflicts)',
  'tbmq.msg.downlink.basic': 'Send messages from one Broker node to another to which the DEVICE subscriber is connected',
  'tbmq.msg.downlink.persisted': 'Send messages from one Broker node to another to which the DEVICE persistent subscriber is connected',
  'tbmq.sys.app.removed': 'Events to process removal of APPLICATION client topic',
  'tbmq.sys.historical.data': 'Historical data stats published from each broker in the cluster to calculate the total'
};

export enum KafkaTable {
  TOPICS = 'TOPICS',
  CONSUMER_GROUPS = 'CONSUMER_GROUPS'
}

export const KafkaTableTranslationMap = new Map<KafkaTable, string>(
  [
    [KafkaTable.TOPICS, 'kafka.topics'],
    [KafkaTable.CONSUMER_GROUPS, 'kafka.consumer-groups']
  ]
);
