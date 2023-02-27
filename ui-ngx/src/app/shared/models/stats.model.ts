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

export enum StatsChartType {
  INCOMING_MESSAGES = 'INCOMING_MESSAGES',
  OUTGOING_MESSAGES = 'OUTGOING_MESSAGES',
  DROPPED_MESSAGES = 'DROPPED_MESSAGES',
  SESSIONS = 'SESSIONS',
  SUBSCRIPTIONS = 'SUBSCRIPTIONS',
  TOPICS = 'TOPICS'
}

export const StatsChartTypeTranslationMap = new Map<StatsChartType, string>(
  [
    [StatsChartType.INCOMING_MESSAGES, 'overview.incoming-messages'],
    [StatsChartType.OUTGOING_MESSAGES, 'overview.outgoing-messages'],
    [StatsChartType.DROPPED_MESSAGES, 'overview.dropped-messages'],
    [StatsChartType.SESSIONS, 'overview.sessions'],
    [StatsChartType.SUBSCRIPTIONS, 'overview.subscriptions'],
    [StatsChartType.TOPICS, 'overview.topics']
  ]
);
