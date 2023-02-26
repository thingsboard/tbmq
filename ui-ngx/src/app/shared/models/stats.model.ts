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

import { Authority } from "@shared/models/authority.enum";

export interface TimeseriesData {
  [key: string]: Array<TsValue>;
}

export interface TsValue {
  ts: number;
  value: string;
  count?: number;
}

export enum DataSortOrder {
  ASC = 'ASC',
  DESC = 'DESC'
}

export enum AggregationType {
  MIN = 'MIN',
  MAX = 'MAX',
  AVG = 'AVG',
  SUM = 'SUM',
  COUNT = 'COUNT',
  NONE = 'NONE'
}

export interface StatChartData {
  incomingMessages?: Array<TsValue>;
  outgoingMessages?: Array<TsValue>;
  droppedMessages?: Array<TsValue>;
  sessions?: Array<TsValue>;
  subscriptions?: Array<TsValue>;
  topics?: Array<TsValue>;
}

export enum StatChartType {
  INCOMING_MESSAGES = 'INCOMING_MESSAGES',
  OUTGOING_MESSAGES = 'OUTGOING_MESSAGES',
  DROPPED_GMESSAGES = 'DROPPED_GMESSAGES',
  SESSIONS = 'SESSIONS',
  SUBSCRIPTIONS = 'SUBSCRIPTIONS',
  TOPICS = 'TOPICS'
}

export const StatChartTypeTranslationMap = new Map<StatChartType, string>(
  [
    [StatChartType.INCOMING_MESSAGES, 'stats.incoming-messages'],
    [StatChartType.OUTGOING_MESSAGES, 'stats.outgoing-messages'],
    [StatChartType.DROPPED_GMESSAGES, 'stats.dropped-messages'],
    [StatChartType.SESSIONS, 'stats.sessions'],
    [StatChartType.SUBSCRIPTIONS, 'stats.subscriptions'],
    [StatChartType.TOPICS, 'stats.topics']
  ]
);
