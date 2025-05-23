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

import { Component, Inject, InjectionToken, OnInit, ViewContainerRef } from '@angular/core';
import {
  aggregationTranslations,
  AggregationType,
  DAY,
  HistoryWindowType,
  quickTimeIntervalPeriod,
  RealtimeWindowType,
  Timewindow,
  TimewindowType
} from '@shared/models/time/time.models';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { UntypedFormBuilder, UntypedFormGroup, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TimeService } from '@core/services/time.service';
import { isDefined } from '@core/utils';
import { OverlayRef } from '@angular/cdk/overlay';
import { MatTabGroup, MatTab } from '@angular/material/tabs';
import { NgTemplateOutlet, AsyncPipe } from '@angular/common';
import { MatRadioGroup, MatRadioButton } from '@angular/material/radio';
import { TranslateModule } from '@ngx-translate/core';
import { MatCheckbox } from '@angular/material/checkbox';
import { TimeintervalComponent } from './timeinterval.component';
import { QuickTimeIntervalComponent } from './quick-time-interval.component';
import { DatetimePeriodComponent } from './datetime-period.component';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatSelect } from '@angular/material/select';
import { MatOption } from '@angular/material/core';
import { MatButton } from '@angular/material/button';

export interface TimewindowPanelData {
  historyOnly: boolean;
  forAllTimeEnabled: boolean;
  quickIntervalOnly: boolean;
  timewindow: Timewindow;
  aggregation: boolean;
  timezone: boolean;
  isEdit: boolean;
}

export const TIMEWINDOW_PANEL_DATA = new InjectionToken<any>('TimewindowPanelData');

@Component({
    selector: 'tb-timewindow-panel',
    templateUrl: './timewindow-panel.component.html',
    styleUrls: ['./timewindow-panel.component.scss'],
    imports: [FormsModule, ReactiveFormsModule, MatTabGroup, MatTab, MatRadioGroup, MatRadioButton, TranslateModule, MatCheckbox, TimeintervalComponent, QuickTimeIntervalComponent, DatetimePeriodComponent, NgTemplateOutlet, MatFormField, MatLabel, MatSelect, MatOption, MatButton, AsyncPipe]
})
export class TimewindowPanelComponent extends PageComponent implements OnInit {

  historyOnly = false;
  forAllTimeEnabled = false;
  quickIntervalOnly = false;
  aggregation = true;
  timezone = false;
  isEdit = false;
  timewindow: Timewindow;
  timewindowForm: UntypedFormGroup;
  historyTypes = HistoryWindowType;
  realtimeTypes = RealtimeWindowType;
  timewindowTypes = TimewindowType;
  aggregationTypes = AggregationType;
  aggregations = Object.keys(AggregationType);
  aggregationTypesTranslations = aggregationTranslations;
  result: Timewindow;

  constructor(@Inject(TIMEWINDOW_PANEL_DATA) public data: TimewindowPanelData,
              public overlayRef: OverlayRef,
              protected store: Store<AppState>,
              public fb: UntypedFormBuilder,
              private timeService: TimeService,
              public viewContainerRef: ViewContainerRef) {
    super(store);
    this.historyOnly = data.historyOnly;
    this.forAllTimeEnabled = data.forAllTimeEnabled;
    this.quickIntervalOnly = data.quickIntervalOnly;
    this.timewindow = data.timewindow;
    this.aggregation = data.aggregation;
    this.timezone = data.timezone;
    this.isEdit = data.isEdit;
  }

  ngOnInit(): void {
    const hideInterval = this.timewindow.hideInterval || false;
    const hideLastInterval = this.timewindow.hideLastInterval || false;
    const hideQuickInterval = this.timewindow.hideQuickInterval || false;
    const hideAggregation = this.timewindow.hideAggregation || false;
    const hideAggInterval = this.timewindow.hideAggInterval || false;
    const hideTimezone = this.timewindow.hideTimezone || false;

    const realtime = this.timewindow.realtime;
    const history = this.timewindow.history;
    const aggregation = this.timewindow.aggregation;

    this.timewindowForm = this.fb.group({
      realtime: this.fb.group({
        realtimeType: [{
          value: isDefined(realtime?.realtimeType) ? this.timewindow.realtime.realtimeType : RealtimeWindowType.LAST_INTERVAL,
          disabled: hideInterval
        }],
        timewindowMs: [{
          value: isDefined(realtime?.timewindowMs) ? this.timewindow.realtime.timewindowMs : null,
          disabled: hideInterval || hideLastInterval
        }],
        interval: [isDefined(realtime?.interval) ? this.timewindow.realtime.interval : null],
        quickInterval: [{
          value: isDefined(realtime?.quickInterval) ? this.timewindow.realtime.quickInterval : null,
          disabled: hideInterval || hideQuickInterval
        }]
      }),
      history: this.fb.group({
        historyType: [{
          value: isDefined(history?.historyType) ? this.timewindow.history.historyType : HistoryWindowType.LAST_INTERVAL,
          disabled: hideInterval
        }],
        timewindowMs: [{
          value: isDefined(history?.timewindowMs) ? this.timewindow.history.timewindowMs : null,
          disabled: hideInterval
        }],
        interval: [ isDefined(history?.interval) ? this.timewindow.history.interval : null
        ],
        fixedTimewindow: [{
          value: isDefined(history?.fixedTimewindow) ? this.timewindow.history.fixedTimewindow : null,
          disabled: hideInterval
        }],
        quickInterval: [{
          value: isDefined(history?.quickInterval) ? this.timewindow.history.quickInterval : null,
          disabled: hideInterval
        }]
      }),
      aggregation: this.fb.group({
        type: [{
          value: isDefined(aggregation?.type) ? this.timewindow.aggregation.type : null,
          disabled: hideAggregation
        }],
        limit: [{
          value: isDefined(aggregation?.limit) ? this.checkLimit(this.timewindow.aggregation.limit) : null,
          disabled: hideAggInterval
        }, []]
      }),
      timezone: [{
        value: isDefined(this.timewindow.timezone) ? this.timewindow.timezone : null,
        disabled: hideTimezone
      }]
    });
    this.updateValidators(this.timewindowForm.get('aggregation.type').value);
    this.timewindowForm.get('aggregation.type').valueChanges.subscribe((aggregationType: AggregationType) => {
      this.updateValidators(aggregationType);
    });
  }

  private checkLimit(limit?: number): number {
    if (!limit || limit < this.minDatapointsLimit()) {
      return this.minDatapointsLimit();
    } else if (limit > this.maxDatapointsLimit()) {
      return this.maxDatapointsLimit();
    }
    return limit;
  }

  private updateValidators(aggType: AggregationType) {
    if (aggType !== AggregationType.NONE) {
      this.timewindowForm.get('aggregation.limit').clearValidators();
    } else {
      this.timewindowForm.get('aggregation.limit').setValidators([Validators.min(this.minDatapointsLimit()),
        Validators.max(this.maxDatapointsLimit())]);
    }
    this.timewindowForm.get('aggregation.limit').updateValueAndValidity({emitEvent: false});
  }

  onTimewindowTypeChange() {
    this.timewindowForm.markAsDirty();
    const timewindowFormValue = this.timewindowForm.getRawValue();
    if (this.timewindow.selectedTab === TimewindowType.REALTIME) {
      if (timewindowFormValue.history.historyType !== HistoryWindowType.FIXED) {
        this.timewindowForm.get('realtime').patchValue({
          realtimeType: Object.keys(RealtimeWindowType).includes(HistoryWindowType[timewindowFormValue.history.historyType]) ?
            RealtimeWindowType[HistoryWindowType[timewindowFormValue.history.historyType]] :
            timewindowFormValue.realtime.realtimeType,
          timewindowMs: timewindowFormValue.history.timewindowMs,
          quickInterval: timewindowFormValue.history.quickInterval.startsWith('CURRENT') ?
            timewindowFormValue.history.quickInterval : timewindowFormValue.realtime.quickInterval
        });
        setTimeout(() => this.timewindowForm.get('realtime.interval').patchValue(timewindowFormValue.history.interval));
      }
    } else {
      this.timewindowForm.get('history').patchValue({
        historyType: HistoryWindowType[RealtimeWindowType[timewindowFormValue.realtime.realtimeType]],
        timewindowMs: timewindowFormValue.realtime.timewindowMs,
        quickInterval: timewindowFormValue.realtime.quickInterval
      });
      setTimeout(() => this.timewindowForm.get('history.interval').patchValue(timewindowFormValue.realtime.interval));
    }
    this.timewindowForm.patchValue({
      aggregation: {
        type: timewindowFormValue.aggregation.type,
        limit: timewindowFormValue.aggregation.limit
      },
      timezone: timewindowFormValue.timezone
    });
  }

  update() {
    const timewindowFormValue = this.timewindowForm.getRawValue();
    this.timewindow.realtime = {
      realtimeType: timewindowFormValue.realtime.realtimeType,
      timewindowMs: timewindowFormValue.realtime.timewindowMs,
      quickInterval: timewindowFormValue.realtime.quickInterval,
      interval: timewindowFormValue.realtime.interval
    };
    this.timewindow.history = {
      historyType: timewindowFormValue.history.historyType,
      timewindowMs: timewindowFormValue.history.timewindowMs,
      interval: timewindowFormValue.history.interval,
      fixedTimewindow: timewindowFormValue.history.fixedTimewindow,
      quickInterval: timewindowFormValue.history.quickInterval,
    };
    if (this.aggregation) {
      this.timewindow.aggregation = {
        type: timewindowFormValue.aggregation.type,
        limit: timewindowFormValue.aggregation.limit
      };
    }
    if (this.timezone) {
      this.timewindow.timezone = timewindowFormValue.timezone;
    }
    this.result = this.timewindow;
    this.overlayRef.dispose();
  }

  cancel() {
    this.overlayRef.dispose();
  }

  minDatapointsLimit() {
    return this.timeService.getMinDatapointsLimit();
  }

  maxDatapointsLimit() {
    return this.timeService.getMaxDatapointsLimit();
  }

  minRealtimeAggInterval() {
    return this.timeService.minIntervalLimit(this.currentRealtimeTimewindow());
  }

  maxRealtimeAggInterval() {
    return this.timeService.maxIntervalLimit(this.currentRealtimeTimewindow());
  }

  currentRealtimeTimewindow(): number {
    const timeWindowFormValue = this.timewindowForm.getRawValue();
    switch (timeWindowFormValue.realtime.realtimeType) {
      case RealtimeWindowType.LAST_INTERVAL:
        return timeWindowFormValue.realtime.timewindowMs;
      case RealtimeWindowType.INTERVAL:
        return quickTimeIntervalPeriod(timeWindowFormValue.realtime.quickInterval);
      default:
        return DAY;
    }
  }

  minHistoryAggInterval() {
    return this.timeService.minIntervalLimit(this.currentHistoryTimewindow());
  }

  maxHistoryAggInterval() {
    return this.timeService.maxIntervalLimit(this.currentHistoryTimewindow());
  }

  currentHistoryTimewindow() {
    const timewindowFormValue = this.timewindowForm.getRawValue();
    if (timewindowFormValue.history.historyType === HistoryWindowType.LAST_INTERVAL) {
      return timewindowFormValue.history.timewindowMs;
    } else if (timewindowFormValue.history.historyType === HistoryWindowType.INTERVAL) {
      return quickTimeIntervalPeriod(timewindowFormValue.history.quickInterval);
    } else if (timewindowFormValue.history.fixedTimewindow) {
      return timewindowFormValue.history.fixedTimewindow.endTimeMs -
          timewindowFormValue.history.fixedTimewindow.startTimeMs;
    } else {
      return DAY;
    }
  }

  onHideIntervalChanged() {
    if (this.timewindow.hideInterval) {
      this.timewindowForm.get('history.historyType').disable({emitEvent: false});
      this.timewindowForm.get('history.timewindowMs').disable({emitEvent: false});
      this.timewindowForm.get('history.fixedTimewindow').disable({emitEvent: false});
      this.timewindowForm.get('history.quickInterval').disable({emitEvent: false});
      this.timewindowForm.get('realtime.realtimeType').disable({emitEvent: false});
      this.timewindowForm.get('realtime.timewindowMs').disable({emitEvent: false});
      this.timewindowForm.get('realtime.quickInterval').disable({emitEvent: false});
    } else {
      this.timewindowForm.get('history.historyType').enable({emitEvent: false});
      this.timewindowForm.get('history.timewindowMs').enable({emitEvent: false});
      this.timewindowForm.get('history.fixedTimewindow').enable({emitEvent: false});
      this.timewindowForm.get('history.quickInterval').enable({emitEvent: false});
      this.timewindowForm.get('realtime.realtimeType').enable({emitEvent: false});
      if (!this.timewindow.hideLastInterval) {
        this.timewindowForm.get('realtime.timewindowMs').enable({emitEvent: false});
      }
      if (!this.timewindow.hideQuickInterval) {
        this.timewindowForm.get('realtime.quickInterval').enable({emitEvent: false});
      }
    }
    this.timewindowForm.markAsDirty();
  }

  onHideLastIntervalChanged() {
    if (this.timewindow.hideLastInterval) {
      this.timewindowForm.get('realtime.timewindowMs').disable({emitEvent: false});
      if (!this.timewindow.hideQuickInterval) {
        this.timewindowForm.get('realtime.realtimeType').setValue(RealtimeWindowType.INTERVAL);
      }
    } else {
      if (!this.timewindow.hideInterval) {
        this.timewindowForm.get('realtime.timewindowMs').enable({emitEvent: false});
      }
    }
    this.timewindowForm.markAsDirty();
  }

  onHideQuickIntervalChanged() {
    if (this.timewindow.hideQuickInterval) {
      this.timewindowForm.get('realtime.quickInterval').disable({emitEvent: false});
      if (!this.timewindow.hideLastInterval) {
        this.timewindowForm.get('realtime.realtimeType').setValue(RealtimeWindowType.LAST_INTERVAL);
      }
    } else {
      if (!this.timewindow.hideInterval) {
        this.timewindowForm.get('realtime.quickInterval').enable({emitEvent: false});
      }
    }
    this.timewindowForm.markAsDirty();
  }

  onHideAggregationChanged() {
    if (this.timewindow.hideAggregation) {
      this.timewindowForm.get('aggregation.type').disable({emitEvent: false});
    } else {
      this.timewindowForm.get('aggregation.type').enable({emitEvent: false});
    }
    this.timewindowForm.markAsDirty();
  }

  onHideAggIntervalChanged() {
    if (this.timewindow.hideAggInterval) {
      this.timewindowForm.get('aggregation.limit').disable({emitEvent: false});
    } else {
      this.timewindowForm.get('aggregation.limit').enable({emitEvent: false});
    }
    this.timewindowForm.markAsDirty();
  }

  onHideTimezoneChanged() {
    if (this.timewindow.hideTimezone) {
      this.timewindowForm.get('timezone').disable({emitEvent: false});
    } else {
      this.timewindowForm.get('timezone').enable({emitEvent: false});
    }
    this.timewindowForm.markAsDirty();
  }

}
