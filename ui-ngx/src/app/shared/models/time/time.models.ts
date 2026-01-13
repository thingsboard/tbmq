///
/// Copyright © 2016-2026 The Thingsboard Authors
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

import { TimeService } from '@core/services/time.service';
import { deepClone, isDefined, isNumeric, isUndefined } from '@app/core/utils';
import moment_ from 'moment';

const moment = moment_;

export const SECOND = 1000;
export const MINUTE = 60 * SECOND;
export const HOUR = 60 * MINUTE;
export const DAY = 24 * HOUR;
export const WEEK = 7 * DAY;
export const AVG_MONTH = Math.floor(30.44 * DAY);
export const AVG_QUARTER = Math.floor(DAY * 365.2425 / 4);
export const YEAR = DAY * 365;

export enum TimewindowType {
  REALTIME,
  HISTORY
}

export enum RealtimeWindowType {
  LAST_INTERVAL,
  INTERVAL
}

export enum HistoryWindowType {
  LAST_INTERVAL,
  FIXED,
  INTERVAL,
  FOR_ALL_TIME
}

export enum IntervalType {
  MILLISECONDS = 'MILLISECONDS',
  WEEK = 'WEEK',
  WEEK_ISO = 'WEEK_ISO',
  MONTH = 'MONTH',
  QUARTER = 'QUARTER'
}

export type Interval = number | IntervalType;

export class IntervalMath {
  public static max(...values: Interval[]): Interval {
    const numberArr = values.map(v => IntervalMath.numberValue(v));
    const index = numberArr.indexOf(Math.max(...numberArr));
    return values[index];
  }

  public static min(...values: Interval[]): Interval {
    const numberArr = values.map(v => IntervalMath.numberValue(v));
    const index = numberArr.indexOf(Math.min(...numberArr));
    return values[index];
  }

  public static numberValue(value: Interval): number {
    return typeof value === 'number' ? value : IntervalTypeValuesMap.get(value);
  }
}

export interface IntervalWindow {
  interval?: Interval;
  timewindowMs?: number;
  quickInterval?: QuickTimeInterval;
}

export interface RealtimeWindow extends IntervalWindow{
  realtimeType?: RealtimeWindowType;
}

export interface FixedWindow {
  startTimeMs: number;
  endTimeMs: number;
}

export interface HistoryWindow extends IntervalWindow {
  historyType?: HistoryWindowType;
  fixedTimewindow?: FixedWindow;
}

export enum AggregationType {
  NONE = 'NONE',
  MIN = 'MIN',
  MAX = 'MAX',
  AVG = 'AVG',
  SUM = 'SUM',
  COUNT = 'COUNT'
}

export const aggregationTranslations = new Map<AggregationType, string>(
  [
    [AggregationType.MIN, 'aggregation.min'],
    [AggregationType.MAX, 'aggregation.max'],
    [AggregationType.AVG, 'aggregation.avg'],
    [AggregationType.SUM, 'aggregation.sum'],
    [AggregationType.COUNT, 'aggregation.count'],
    [AggregationType.NONE, 'aggregation.none'],
  ]
);

export interface Aggregation {
  interval?: Interval;
  type: AggregationType;
  limit: number;
}

export interface Timewindow {
  displayValue?: string;
  displayTimezoneAbbr?: string;
  hideInterval?: boolean;
  hideQuickInterval?: boolean;
  hideLastInterval?: boolean;
  hideAggregation?: boolean;
  hideAggInterval?: boolean;
  hideTimezone?: boolean;
  selectedTab?: TimewindowType;
  realtime?: RealtimeWindow;
  history?: HistoryWindow;
  aggregation?: Aggregation;
  timezone?: string;
  fixedTimewindow?: FixedWindow;
}

export enum QuickTimeInterval {
  YESTERDAY = 'YESTERDAY',
  DAY_BEFORE_YESTERDAY = 'DAY_BEFORE_YESTERDAY',
  THIS_DAY_LAST_WEEK = 'THIS_DAY_LAST_WEEK',
  PREVIOUS_WEEK = 'PREVIOUS_WEEK',
  PREVIOUS_WEEK_ISO = 'PREVIOUS_WEEK_ISO',
  PREVIOUS_MONTH = 'PREVIOUS_MONTH',
  PREVIOUS_QUARTER = 'PREVIOUS_QUARTER',
  PREVIOUS_HALF_YEAR = 'PREVIOUS_HALF_YEAR',
  PREVIOUS_YEAR = 'PREVIOUS_YEAR',
  CURRENT_HOUR = 'CURRENT_HOUR',
  CURRENT_DAY = 'CURRENT_DAY',
  CURRENT_DAY_SO_FAR = 'CURRENT_DAY_SO_FAR',
  CURRENT_WEEK = 'CURRENT_WEEK',
  CURRENT_WEEK_ISO = 'CURRENT_WEEK_ISO',
  CURRENT_WEEK_SO_FAR = 'CURRENT_WEEK_SO_FAR',
  CURRENT_WEEK_ISO_SO_FAR = 'CURRENT_WEEK_ISO_SO_FAR',
  CURRENT_MONTH = 'CURRENT_MONTH',
  CURRENT_MONTH_SO_FAR = 'CURRENT_MONTH_SO_FAR',
  CURRENT_QUARTER = 'CURRENT_QUARTER',
  CURRENT_QUARTER_SO_FAR = 'CURRENT_QUARTER_SO_FAR',
  CURRENT_HALF_YEAR = 'CURRENT_HALF_YEAR',
  CURRENT_HALF_YEAR_SO_FAR = 'CURRENT_HALF_YEAR_SO_FAR',
  CURRENT_YEAR = 'CURRENT_YEAR',
  CURRENT_YEAR_SO_FAR = 'CURRENT_YEAR_SO_FAR'
}

export const QuickTimeIntervalTranslationMap = new Map<QuickTimeInterval, string>([
  [QuickTimeInterval.YESTERDAY, 'timeinterval.predefined.yesterday'],
  [QuickTimeInterval.DAY_BEFORE_YESTERDAY, 'timeinterval.predefined.day-before-yesterday'],
  [QuickTimeInterval.THIS_DAY_LAST_WEEK, 'timeinterval.predefined.this-day-last-week'],
  [QuickTimeInterval.PREVIOUS_WEEK, 'timeinterval.predefined.previous-week'],
  [QuickTimeInterval.PREVIOUS_WEEK_ISO, 'timeinterval.predefined.previous-week-iso'],
  [QuickTimeInterval.PREVIOUS_MONTH, 'timeinterval.predefined.previous-month'],
  [QuickTimeInterval.PREVIOUS_QUARTER, 'timeinterval.predefined.previous-quarter'],
  [QuickTimeInterval.PREVIOUS_HALF_YEAR, 'timeinterval.predefined.previous-half-year'],
  [QuickTimeInterval.PREVIOUS_YEAR, 'timeinterval.predefined.previous-year'],
  [QuickTimeInterval.CURRENT_HOUR, 'timeinterval.predefined.current-hour'],
  [QuickTimeInterval.CURRENT_DAY, 'timeinterval.predefined.current-day'],
  [QuickTimeInterval.CURRENT_DAY_SO_FAR, 'timeinterval.predefined.current-day-so-far'],
  [QuickTimeInterval.CURRENT_WEEK, 'timeinterval.predefined.current-week'],
  [QuickTimeInterval.CURRENT_WEEK_ISO, 'timeinterval.predefined.current-week-iso'],
  [QuickTimeInterval.CURRENT_WEEK_SO_FAR, 'timeinterval.predefined.current-week-so-far'],
  [QuickTimeInterval.CURRENT_WEEK_ISO_SO_FAR, 'timeinterval.predefined.current-week-iso-so-far'],
  [QuickTimeInterval.CURRENT_MONTH, 'timeinterval.predefined.current-month'],
  [QuickTimeInterval.CURRENT_MONTH_SO_FAR, 'timeinterval.predefined.current-month-so-far'],
  [QuickTimeInterval.CURRENT_QUARTER, 'timeinterval.predefined.current-quarter'],
  [QuickTimeInterval.CURRENT_QUARTER_SO_FAR, 'timeinterval.predefined.current-quarter-so-far'],
  [QuickTimeInterval.CURRENT_HALF_YEAR, 'timeinterval.predefined.current-half-year'],
  [QuickTimeInterval.CURRENT_HALF_YEAR_SO_FAR, 'timeinterval.predefined.current-half-year-so-far'],
  [QuickTimeInterval.CURRENT_YEAR, 'timeinterval.predefined.current-year'],
  [QuickTimeInterval.CURRENT_YEAR_SO_FAR, 'timeinterval.predefined.current-year-so-far']
]);

export const IntervalTypeValuesMap = new Map<IntervalType, number>([
  [IntervalType.WEEK, WEEK],
  [IntervalType.WEEK_ISO, WEEK],
  [IntervalType.MONTH, AVG_MONTH],
  [IntervalType.QUARTER, AVG_QUARTER]
]);

export const forAllTimeInterval = (): Timewindow => ({
  selectedTab: TimewindowType.HISTORY,
  history: {
    historyType: HistoryWindowType.FOR_ALL_TIME
  }
});

export const historyInterval = (timewindowMs: number): Timewindow => ({
  selectedTab: TimewindowType.HISTORY,
  history: {
    historyType: HistoryWindowType.LAST_INTERVAL,
    timewindowMs
  }
});

export const defaultTimewindow = (timeService: TimeService): Timewindow => {
  const currentTime = moment().valueOf();
  return {
    displayValue: '',
    hideInterval: false,
    hideLastInterval: false,
    hideQuickInterval: false,
    hideAggregation: false,
    hideAggInterval: false,
    hideTimezone: false,
    selectedTab: TimewindowType.REALTIME,
    realtime: {
      realtimeType: RealtimeWindowType.LAST_INTERVAL,
      interval: HOUR,
      timewindowMs: HOUR,
      quickInterval: QuickTimeInterval.CURRENT_DAY
    },
    history: {
      historyType: HistoryWindowType.LAST_INTERVAL,
      interval: HOUR,
      timewindowMs: HOUR,
      fixedTimewindow: {
        startTimeMs: currentTime - DAY,
        endTimeMs: currentTime
      },
      quickInterval: QuickTimeInterval.CURRENT_DAY
    },
    aggregation: {
      type: AggregationType.NONE,
      limit: Math.floor(timeService.getMaxDatapointsLimit() / 2)
    }
  };
};

const getTimewindowType = (timewindow: Timewindow): TimewindowType => {
  if (isUndefined(timewindow.selectedTab)) {
    return isDefined(timewindow.realtime) ? TimewindowType.REALTIME : TimewindowType.HISTORY;
  } else {
    return timewindow.selectedTab;
  }
};

export const initModelFromDefaultTimewindow = (value: Timewindow, quickIntervalOnly: boolean,
                                               historyOnly: boolean, timeService: TimeService): Timewindow => {
  const model = defaultTimewindow(timeService);
  if (value) {
    model.hideInterval = value.hideInterval;
    model.hideLastInterval = value.hideLastInterval;
    model.hideQuickInterval = value.hideQuickInterval;
    model.hideAggregation = value.hideAggregation;
    model.hideAggInterval = value.hideAggInterval;
    model.hideTimezone = value.hideTimezone;
    model.selectedTab = getTimewindowType(value);
    if (isDefined(value.realtime)) {
      if (isDefined(value.realtime.interval)) {
        model.realtime.interval = value.realtime.interval;
      }
      if (isUndefined(value.realtime.realtimeType)) {
        if (isDefined(value.realtime.quickInterval)) {
          model.realtime.realtimeType = RealtimeWindowType.INTERVAL;
        } else {
          model.realtime.realtimeType = RealtimeWindowType.LAST_INTERVAL;
        }
      } else {
        model.realtime.realtimeType = value.realtime.realtimeType;
      }
      if (isDefined(value.realtime.quickInterval)) {
        model.realtime.quickInterval = value.realtime.quickInterval;
      }
      if (isDefined(value.realtime.timewindowMs)) {
        model.realtime.timewindowMs = value.realtime.timewindowMs;
      }
    }
    if (isDefined(value.history)) {
      if (isDefined(value.history.interval)) {
        model.history.interval = value.history.interval;
      }
      if (isUndefined(value.history.historyType)) {
        if (isDefined(value.history.timewindowMs)) {
          model.history.historyType = HistoryWindowType.LAST_INTERVAL;
        } else if (isDefined(value.history.quickInterval)) {
          model.history.historyType = HistoryWindowType.INTERVAL;
        } else {
          model.history.historyType = HistoryWindowType.FIXED;
        }
      } else {
        model.history.historyType = value.history.historyType;
      }
      if (isDefined(value.history.timewindowMs)) {
        model.history.timewindowMs = value.history.timewindowMs;
      }
      if (isDefined(value.history.quickInterval)) {
        model.history.quickInterval = value.history.quickInterval;
      }
      if (isDefined(value.history.fixedTimewindow)) {
        if (isDefined(value.history.fixedTimewindow.startTimeMs)) {
          model.history.fixedTimewindow.startTimeMs = value.history.fixedTimewindow.startTimeMs;
        }
        if (isDefined(value.history.fixedTimewindow.endTimeMs)) {
          model.history.fixedTimewindow.endTimeMs = value.history.fixedTimewindow.endTimeMs;
        }
      }
    }
    if (value.aggregation) {
      if (value.aggregation.type) {
        model.aggregation.type = value.aggregation.type;
      }
      model.aggregation.limit = value.aggregation.limit || Math.floor(timeService.getMaxDatapointsLimit() / 2);
    }
    model.timezone = value.timezone;
  }
  if (quickIntervalOnly) {
    model.realtime.realtimeType = RealtimeWindowType.INTERVAL;
  }
  if (historyOnly) {
    model.selectedTab = TimewindowType.HISTORY;
  }
  return model;
};

export const getCurrentTime = (tz?: string): moment_.Moment => {
  return moment();
};

export const calculateIntervalStartTime = (interval: QuickTimeInterval, currentDate: moment_.Moment): moment_.Moment => {
  switch (interval) {
    case QuickTimeInterval.YESTERDAY:
      currentDate.subtract(1, 'days');
      return currentDate.startOf('day');
    case QuickTimeInterval.DAY_BEFORE_YESTERDAY:
      currentDate.subtract(2, 'days');
      return currentDate.startOf('day');
    case QuickTimeInterval.THIS_DAY_LAST_WEEK:
      currentDate.subtract(1, 'weeks');
      return currentDate.startOf('day');
    case QuickTimeInterval.PREVIOUS_WEEK:
      currentDate.subtract(1, 'weeks');
      return currentDate.startOf('week');
    case QuickTimeInterval.PREVIOUS_WEEK_ISO:
      currentDate.subtract(1, 'weeks');
      return currentDate.startOf('isoWeek');
    case QuickTimeInterval.PREVIOUS_MONTH:
      currentDate.subtract(1, 'months');
      return currentDate.startOf('month');
    case QuickTimeInterval.PREVIOUS_QUARTER:
      currentDate.subtract(1, 'quarter');
      return currentDate.startOf('quarter');
    case QuickTimeInterval.PREVIOUS_HALF_YEAR:
      if (currentDate.get('quarter') < 3) {
        return currentDate.startOf('year').subtract(2, 'quarters');
      } else {
        return currentDate.startOf('year');
      }
    case QuickTimeInterval.PREVIOUS_YEAR:
      currentDate.subtract(1, 'years');
      return currentDate.startOf('year');
    case QuickTimeInterval.CURRENT_HOUR:
      return currentDate.startOf('hour');
    case QuickTimeInterval.CURRENT_DAY:
    case QuickTimeInterval.CURRENT_DAY_SO_FAR:
      return currentDate.startOf('day');
    case QuickTimeInterval.CURRENT_WEEK:
    case QuickTimeInterval.CURRENT_WEEK_SO_FAR:
      return currentDate.startOf('week');
    case QuickTimeInterval.CURRENT_WEEK_ISO:
    case QuickTimeInterval.CURRENT_WEEK_ISO_SO_FAR:
      return currentDate.startOf('isoWeek');
    case QuickTimeInterval.CURRENT_MONTH:
    case QuickTimeInterval.CURRENT_MONTH_SO_FAR:
      return currentDate.startOf('month');
    case QuickTimeInterval.CURRENT_QUARTER:
    case QuickTimeInterval.CURRENT_QUARTER_SO_FAR:
      return currentDate.startOf('quarter');
    case QuickTimeInterval.CURRENT_HALF_YEAR:
    case QuickTimeInterval.CURRENT_HALF_YEAR_SO_FAR:
      if (currentDate.get('quarter') < 3) {
        return currentDate.startOf('year');
      } else {
        return currentDate.clone().set('quarter', 3).startOf('quarter');
      }
    case QuickTimeInterval.CURRENT_YEAR:
    case QuickTimeInterval.CURRENT_YEAR_SO_FAR:
      return currentDate.startOf('year');
  }
};

export const calculateIntervalEndTime = (interval: QuickTimeInterval, startDate: moment_.Moment, tz?: string): number => {
  switch (interval) {
    case QuickTimeInterval.YESTERDAY:
    case QuickTimeInterval.DAY_BEFORE_YESTERDAY:
    case QuickTimeInterval.THIS_DAY_LAST_WEEK:
    case QuickTimeInterval.CURRENT_DAY:
      return startDate.add(1, 'day').valueOf();
    case QuickTimeInterval.PREVIOUS_WEEK:
    case QuickTimeInterval.PREVIOUS_WEEK_ISO:
    case QuickTimeInterval.CURRENT_WEEK:
    case QuickTimeInterval.CURRENT_WEEK_ISO:
      return startDate.add(1, 'week').valueOf();
    case QuickTimeInterval.PREVIOUS_MONTH:
    case QuickTimeInterval.CURRENT_MONTH:
      return startDate.add(1, 'month').valueOf();
    case QuickTimeInterval.PREVIOUS_QUARTER:
    case QuickTimeInterval.CURRENT_QUARTER:
      return startDate.add(1, 'quarter').valueOf();
    case QuickTimeInterval.PREVIOUS_HALF_YEAR:
    case QuickTimeInterval.CURRENT_HALF_YEAR:
      return startDate.add(2, 'quarters').valueOf();
    case QuickTimeInterval.PREVIOUS_YEAR:
    case QuickTimeInterval.CURRENT_YEAR:
      return startDate.add(1, 'year').valueOf();
    case QuickTimeInterval.CURRENT_HOUR:
      return startDate.add(1, 'hour').valueOf();
    case QuickTimeInterval.CURRENT_DAY_SO_FAR:
    case QuickTimeInterval.CURRENT_WEEK_SO_FAR:
    case QuickTimeInterval.CURRENT_WEEK_ISO_SO_FAR:
    case QuickTimeInterval.CURRENT_MONTH_SO_FAR:
    case QuickTimeInterval.CURRENT_QUARTER_SO_FAR:
    case QuickTimeInterval.CURRENT_HALF_YEAR_SO_FAR:
    case QuickTimeInterval.CURRENT_YEAR_SO_FAR:
      return getCurrentTime(tz).valueOf();
  }
};

export const calculateIntervalStartEndTime = (interval: QuickTimeInterval, tz?: string): [number, number] => {
  const startEndTs: [number, number] = [0, 0];
  const currentDate = getCurrentTime(tz);
  const startDate = calculateIntervalStartTime(interval, currentDate);
  startEndTs[0] = startDate.valueOf();
  const endDate = calculateIntervalEndTime(interval, startDate, tz);
  startEndTs[1] = endDate.valueOf();
  return startEndTs;
};

export const quickTimeIntervalPeriod = (interval: QuickTimeInterval): number => {
  switch (interval) {
    case QuickTimeInterval.CURRENT_HOUR:
      return HOUR;
    case QuickTimeInterval.YESTERDAY:
    case QuickTimeInterval.DAY_BEFORE_YESTERDAY:
    case QuickTimeInterval.THIS_DAY_LAST_WEEK:
    case QuickTimeInterval.CURRENT_DAY:
    case QuickTimeInterval.CURRENT_DAY_SO_FAR:
      return DAY;
    case QuickTimeInterval.PREVIOUS_WEEK:
    case QuickTimeInterval.PREVIOUS_WEEK_ISO:
    case QuickTimeInterval.CURRENT_WEEK:
    case QuickTimeInterval.CURRENT_WEEK_ISO:
    case QuickTimeInterval.CURRENT_WEEK_SO_FAR:
    case QuickTimeInterval.CURRENT_WEEK_ISO_SO_FAR:
      return WEEK;
    case QuickTimeInterval.PREVIOUS_MONTH:
    case QuickTimeInterval.CURRENT_MONTH:
    case QuickTimeInterval.CURRENT_MONTH_SO_FAR:
      return DAY * 30;
    case QuickTimeInterval.PREVIOUS_QUARTER:
    case QuickTimeInterval.CURRENT_QUARTER:
    case QuickTimeInterval.CURRENT_QUARTER_SO_FAR:
      return DAY * 30 * 3;
    case QuickTimeInterval.PREVIOUS_HALF_YEAR:
    case QuickTimeInterval.CURRENT_HALF_YEAR:
    case QuickTimeInterval.CURRENT_HALF_YEAR_SO_FAR:
      return DAY * 30 * 6;
    case QuickTimeInterval.PREVIOUS_YEAR:
    case QuickTimeInterval.CURRENT_YEAR:
    case QuickTimeInterval.CURRENT_YEAR_SO_FAR:
      return YEAR;
  }
};

export const cloneSelectedTimewindow = (timewindow: Timewindow): Timewindow => {
  const cloned: Timewindow = {};
  cloned.hideInterval = timewindow.hideInterval || false;
  cloned.hideLastInterval = timewindow.hideLastInterval || false;
  cloned.hideQuickInterval = timewindow.hideQuickInterval || false;
  cloned.hideAggregation = timewindow.hideAggregation || false;
  cloned.hideAggInterval = timewindow.hideAggInterval || false;
  cloned.hideTimezone = timewindow.hideTimezone || false;
  if (isDefined(timewindow.selectedTab)) {
    cloned.selectedTab = timewindow.selectedTab;
    if (timewindow.selectedTab === TimewindowType.REALTIME) {
      cloned.realtime = deepClone(timewindow.realtime);
    } else if (timewindow.selectedTab === TimewindowType.HISTORY) {
      cloned.history = deepClone(timewindow.history);
    }
  }
  cloned.aggregation = deepClone(timewindow.aggregation);
  cloned.timezone = timewindow.timezone;
  return cloned;
};

export interface TimeInterval {
  name: string;
  translateParams: {[key: string]: any};
  value: Interval;
}

export const defaultTimeIntervals = new Array<TimeInterval>(
  {
    name: 'timeinterval.seconds-interval',
    translateParams: {seconds: 1},
    value: SECOND
  },
  {
    name: 'timeinterval.seconds-interval',
    translateParams: {seconds: 5},
    value: 5 * SECOND
  },
  {
    name: 'timeinterval.seconds-interval',
    translateParams: {seconds: 10},
    value: 10 * SECOND
  },
  {
    name: 'timeinterval.seconds-interval',
    translateParams: {seconds: 15},
    value: 15 * SECOND
  },
  {
    name: 'timeinterval.seconds-interval',
    translateParams: {seconds: 30},
    value: 30 * SECOND
  },
  {
    name: 'timeinterval.minutes-interval',
    translateParams: {minutes: 1},
    value: MINUTE
  },
  {
    name: 'timeinterval.minutes-interval',
    translateParams: {minutes: 2},
    value: 2 * MINUTE
  },
  {
    name: 'timeinterval.minutes-interval',
    translateParams: {minutes: 5},
    value: 5 * MINUTE
  },
  {
    name: 'timeinterval.minutes-interval',
    translateParams: {minutes: 10},
    value: 10 * MINUTE
  },
  {
    name: 'timeinterval.minutes-interval',
    translateParams: {minutes: 15},
    value: 15 * MINUTE
  },
  {
    name: 'timeinterval.minutes-interval',
    translateParams: {minutes: 30},
    value: 30 * MINUTE
  },
  {
    name: 'timeinterval.hours-interval',
    translateParams: {hours: 1},
    value: HOUR
  },
  {
    name: 'timeinterval.hours-interval',
    translateParams: {hours: 2},
    value: 2 * HOUR
  },
  {
    name: 'timeinterval.hours-interval',
    translateParams: {hours: 5},
    value: 5 * HOUR
  },
  {
    name: 'timeinterval.hours-interval',
    translateParams: {hours: 10},
    value: 10 * HOUR
  },
  {
    name: 'timeinterval.hours-interval',
    translateParams: {hours: 12},
    value: 12 * HOUR
  },
  {
    name: 'timeinterval.days-interval',
    translateParams: {days: 1},
    value: DAY
  },
  {
    name: 'timeinterval.days-interval',
    translateParams: {days: 7},
    value: 7 * DAY
  },
  {
    name: 'timeinterval.type.week',
    translateParams: {},
    value: IntervalType.WEEK
  },
  {
    name: 'timeinterval.type.week-iso',
    translateParams: {},
    value: IntervalType.WEEK_ISO
  },
  {
    name: 'timeinterval.days-interval',
    translateParams: {days: 30},
    value: 30 * DAY
  },
  {
    name: 'timeinterval.type.month',
    translateParams: {},
    value: IntervalType.MONTH
  },
  {
    name: 'timeinterval.type.quarter',
    translateParams: {},
    value: IntervalType.QUARTER
  }
);

export interface TimezoneInfo {
  id: string;
  name: string;
  offset: string;
  nOffset: number;
  abbr: string;
}

export const calculateFixedWindowTimeMs = (timewindow: Timewindow): FixedWindow => {
  const currentTime = Date.now();
  let startTimeMs: number = 0;
  let endTimeMs: number = 0;
  if (timewindow.selectedTab === TimewindowType.HISTORY) {
    if (timewindow.history?.historyType === HistoryWindowType.LAST_INTERVAL) {
      startTimeMs = currentTime - timewindow.history.timewindowMs;
      endTimeMs = currentTime;
    } else if (timewindow.history?.historyType === HistoryWindowType.INTERVAL) {
      const startEndTime = calculateIntervalStartEndTime(timewindow.history.quickInterval);
      startTimeMs = startEndTime[0];
      endTimeMs = startEndTime[1];
    } else {
      startTimeMs = timewindow.history.fixedTimewindow.startTimeMs;
      endTimeMs = timewindow.history.fixedTimewindow.endTimeMs;
    }
  } else if (timewindow.selectedTab === TimewindowType.REALTIME) {
    if (timewindow.realtime.realtimeType === RealtimeWindowType.INTERVAL) {
      const startEndTime = calculateIntervalStartEndTime(timewindow.realtime.quickInterval);
      startTimeMs = startEndTime[0];
      endTimeMs = startEndTime[1];
    } else if (timewindow.realtime.realtimeType === RealtimeWindowType.LAST_INTERVAL) {
      startTimeMs = currentTime - timewindow.realtime.timewindowMs;
      endTimeMs = currentTime;
    }
  }
  return { startTimeMs, endTimeMs };
}
