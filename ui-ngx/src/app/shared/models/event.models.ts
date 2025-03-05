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
import { EntityId } from '@shared/models/id/entity-id';

export enum EventType {
  LC_EVENT = 'LC_EVENT',
  STATS = 'STATS',
  ERROR = 'ERROR'
}

export const eventTypeTranslations = new Map<EventType, string>(
  [
    [EventType.ERROR, 'event.type-error'],
    [EventType.LC_EVENT, 'event.type-lc-event'],
    [EventType.STATS, 'event.type-stats'],
  ]
);

export interface BaseEventBody {
  server: string;
}

export interface ErrorEventBody extends BaseEventBody {
  method: string;
  error: string;
}

export interface LcEventEventBody extends BaseEventBody {
  event: string;
  success: boolean;
  error: string;
}

export interface StatsEventBody extends BaseEventBody {
  messagesProcessed: number;
  errorsOccurred: number;
}

export type EventBody = ErrorEventBody & LcEventEventBody & StatsEventBody;

export interface Event extends BaseData {
  entityId: EntityId;
  type: string;
  uid: string;
  body: EventBody;
}

export interface BaseFilterEventBody {
  server?: string;
}

export interface ErrorFilterEventBody extends BaseFilterEventBody {
  method?: string;
  errorStr?: string;
}

export interface LcFilterEventEventBody extends BaseFilterEventBody {
  event?: string;
  status?: string;
  errorStr?: string;
}

export interface StatsFilterEventBody extends BaseFilterEventBody {
  messagesProcessed?: number;
  errorsOccurred?: number;
}

export type FilterEventBody = ErrorFilterEventBody & LcFilterEventEventBody & StatsFilterEventBody;
