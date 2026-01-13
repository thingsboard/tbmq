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

import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  forwardRef,
  HostBinding,
  Injector,
  Input,
  StaticProvider,
  ViewContainerRef,
  input,
  booleanAttribute, model
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { MillisecondsToTimeStringPipe } from '@shared/pipe/milliseconds-to-time-string.pipe';
import {
  cloneSelectedTimewindow,
  HistoryWindowType,
  initModelFromDefaultTimewindow,
  QuickTimeIntervalTranslationMap,
  RealtimeWindowType,
  Timewindow,
  TimewindowType
} from '@shared/models/time/time.models';
import { DatePipe } from '@angular/common';
import {
  TIMEWINDOW_PANEL_DATA,
  TimewindowPanelComponent,
  TimewindowPanelData
} from '@shared/components/time/timewindow-panel.component';
import { TimeService } from '@core/services/time.service';
import { TooltipPosition, MatTooltip } from '@angular/material/tooltip';
import { deepClone } from '@core/utils';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { Overlay, OverlayConfig, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { coerceBoolean } from '@shared/decorators/coercion';
import { DEFAULT_OVERLAY_POSITIONS } from '@shared/models/overlay.models';
import { fromEvent } from 'rxjs';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatNativeDatetimeModule } from '@mat-datetimepicker/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DATE_LOCALE } from '@angular/material/core';

// @dynamic
@Component({
    selector: 'tb-timewindow',
    templateUrl: './timewindow.component.html',
    styleUrls: ['./timewindow.component.scss'],
    providers: [
        DatePipe,
        MillisecondsToTimeStringPipe,
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => TimewindowComponent),
            multi: true
        },
        {
          provide: MAT_DATE_LOCALE,
          useValue: 'en-GB'
        }
    ],
    imports: [MatButton, MatIcon, MatTooltip, TranslateModule, MatDatepickerModule, MatNativeDatetimeModule]
})
export class TimewindowComponent implements ControlValueAccessor {

  historyOnlyValue = false;

  @Input()
  set historyOnly(val) {
    const newHistoryOnlyValue = coerceBooleanProperty(val);
    if (this.historyOnlyValue !== newHistoryOnlyValue) {
      this.historyOnlyValue = newHistoryOnlyValue;
    }
  }

  get historyOnly() {
    return this.historyOnlyValue;
  }

  @HostBinding('class.no-margin')
  @coerceBoolean()
  readonly noMargin = input(false);

  readonly noPadding = input(false, {transform: booleanAttribute});
  readonly disablePanel = input(false, {transform: booleanAttribute});
  readonly forAllTimeEnabled = input(false, {transform: booleanAttribute});
  readonly alwaysDisplayTypePrefix = input(false, {transform: booleanAttribute});
  readonly quickIntervalOnly = input(false, {transform: booleanAttribute});
  readonly aggregation = input(false, {transform: booleanAttribute});
  readonly timezone = input(false, {transform: booleanAttribute});
  readonly isToolbar = input(false, {transform: booleanAttribute});
  readonly asButton = input(false, {transform: booleanAttribute});
  readonly strokedButton = input(false, {transform: booleanAttribute});
  readonly flatButton = input(false, {transform: booleanAttribute});
  readonly displayTimewindowValue = input(true, {transform: booleanAttribute});
  readonly hideLabel = input(false, {transform: booleanAttribute});

  isEditValue = false;

  @Input()
  set isEdit(val) {
    this.isEditValue = coerceBooleanProperty(val);
    this.timewindowDisabled = this.isTimewindowDisabled();
  }

  get isEdit() {
    return this.isEditValue;
  }

  readonly tooltipPosition = input<TooltipPosition>('above');

  disabled = model<boolean>();

  innerValue: Timewindow;

  timewindowDisabled: boolean;

  private propagateChange = (_: any) => {};

  constructor(private overlay: Overlay,
              private translate: TranslateService,
              private timeService: TimeService,
              private millisecondsToTimeStringPipe: MillisecondsToTimeStringPipe,
              private datePipe: DatePipe,
              private cd: ChangeDetectorRef,
              private nativeElement: ElementRef,
              public viewContainerRef: ViewContainerRef) {
  }

  toggleTimewindow($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    if (this.disablePanel()) {
      return;
    }
    const config = new OverlayConfig({
      panelClass: 'tb-timewindow-panel',
      backdropClass: 'cdk-overlay-transparent-backdrop',
      hasBackdrop: true,
      maxHeight: '80vh',
      height: 'min-content'
    });

    config.positionStrategy = this.overlay.position()
      .flexibleConnectedTo(this.nativeElement)
      .withPositions(DEFAULT_OVERLAY_POSITIONS);

    const overlayRef = this.overlay.create(config);
    overlayRef.backdropClick().subscribe(() => {
      overlayRef.dispose();
    });
    const providers: StaticProvider[] = [
      {
        provide: TIMEWINDOW_PANEL_DATA,
        useValue: {
          timewindow: deepClone(this.innerValue),
          historyOnly: this.historyOnly,
          forAllTimeEnabled: this.forAllTimeEnabled(),
          quickIntervalOnly: this.quickIntervalOnly(),
          aggregation: this.aggregation(),
          timezone: this.timezone(),
          isEdit: this.isEdit
        } as TimewindowPanelData
      },
      {
        provide: OverlayRef,
        useValue: overlayRef
      }
    ];
    const injector = Injector.create({parent: this.viewContainerRef.injector, providers});
    const componentRef = overlayRef.attach(new ComponentPortal(TimewindowPanelComponent,
      this.viewContainerRef, injector));
    const resizeWindows$ = fromEvent(window, 'resize').subscribe(() => {
      overlayRef.updatePosition();
    });
    componentRef.onDestroy(() => {
      resizeWindows$.unsubscribe();
      if (componentRef.instance.result) {
        this.innerValue = componentRef.instance.result;
        this.timewindowDisabled = this.isTimewindowDisabled();
        this.updateDisplayValue();
        this.notifyChanged();
      }
    });
    this.cd.detectChanges();
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
    this.timewindowDisabled = this.isTimewindowDisabled();
  }

  writeValue(obj: Timewindow): void {
    this.innerValue = initModelFromDefaultTimewindow(obj, this.quickIntervalOnly(), this.historyOnly, this.timeService);
    this.timewindowDisabled = this.isTimewindowDisabled();
    this.updateDisplayValue();
  }

  notifyChanged() {
    this.propagateChange(cloneSelectedTimewindow(this.innerValue));
  }

  displayValue(): string {
    return this.displayTimewindowValue() ? this.innerValue?.displayValue : this.translate.instant('timewindow.timewindow');
  }

  updateDisplayValue() {
    if (this.innerValue.selectedTab === TimewindowType.REALTIME && !this.historyOnly) {
      this.innerValue.displayValue = '';
      if (this.innerValue.realtime.realtimeType === RealtimeWindowType.INTERVAL) {
        this.innerValue.displayValue += this.translate.instant(QuickTimeIntervalTranslationMap.get(this.innerValue.realtime.quickInterval));
      } else {
        this.innerValue.displayValue +=  this.translate.instant('timewindow.last-prefix') + ' ' +
          this.millisecondsToTimeStringPipe.transform(this.innerValue.realtime.timewindowMs);
      }
    } else {
      this.innerValue.displayValue = (!this.historyOnly || this.alwaysDisplayTypePrefix()) ?
        (this.translate.instant('timewindow.history') + ' - ') : '';
      if (this.innerValue.history.historyType === HistoryWindowType.LAST_INTERVAL) {
        this.innerValue.displayValue += this.translate.instant('timewindow.last-prefix') + ' ' +
          this.millisecondsToTimeStringPipe.transform(this.innerValue.history.timewindowMs);
      } else if (this.innerValue.history.historyType === HistoryWindowType.INTERVAL) {
        this.innerValue.displayValue += this.translate.instant(QuickTimeIntervalTranslationMap.get(this.innerValue.history.quickInterval));
      } else if (this.innerValue.history.historyType === HistoryWindowType.FOR_ALL_TIME) {
        this.innerValue.displayValue += this.translate.instant('timewindow.for-all-time');
      } else {
        const startString = this.datePipe.transform(this.innerValue.history.fixedTimewindow.startTimeMs, 'yyyy-MM-dd HH:mm:ss');
        const endString = this.datePipe.transform(this.innerValue.history.fixedTimewindow.endTimeMs, 'yyyy-MM-dd HH:mm:ss');
        this.innerValue.displayValue += this.translate.instant('timewindow.period', {startTime: startString, endTime: endString});
      }
    }
    this.innerValue.displayTimezoneAbbr = '';
    this.cd.detectChanges();
  }

  private isTimewindowDisabled(): boolean {
    return this.disabled() ||
      (!this.isEdit && (!this.innerValue || this.innerValue.hideInterval &&
        (!this.aggregation() || this.innerValue.hideAggregation && this.innerValue.hideAggInterval)));
  }

}
