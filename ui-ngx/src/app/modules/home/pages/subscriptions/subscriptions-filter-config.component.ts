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

import {
  Component,
  ElementRef,
  forwardRef,
  Inject,
  InjectionToken,
  Input,
  OnDestroy,
  OnInit,
  Optional,
  TemplateRef,
  ViewChild,
  ViewContainerRef
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, UntypedFormBuilder, UntypedFormGroup } from '@angular/forms';
import { coerceBoolean } from '@shared/decorators/coercion';
import { Overlay, OverlayConfig, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';

import { TranslateService } from '@ngx-translate/core';
import { deepClone } from '@core/utils';
import { EntityType } from '@shared/models/entity-type.models';
import { fromEvent, Subscription } from 'rxjs';
import { POSITION_MAP } from '@shared/models/overlay.models';
import { ClientSubscriptionFilterConfig, subscriptionsFilterConfigEquals } from '@shared/models/subscription.model';
import { mqttQoSTypes, mqttQoSValuesMap } from '@shared/models/session.model';
import { RhOptions } from '@shared/models/ws-client.model';

export const SUBSCRIPTIONS_FILTER_CONFIG_DATA = new InjectionToken<any>('SubscriptionsFilterConfigData');

export interface SubscriptionsFilterConfigData {
  panelMode: boolean;
  subscriptionsFilterConfig: ClientSubscriptionFilterConfig;
  initialClientSubscriptionFilterConfig?: ClientSubscriptionFilterConfig;
}

// @dynamic
@Component({
  selector: 'tb-subscriptions-filter-config',
  templateUrl: './subscriptions-filter-config.component.html',
  styleUrls: ['./subscriptions-filter-config.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => SubscriptionsFilterConfigComponent),
      multi: true
    }
  ]
})
export class SubscriptionsFilterConfigComponent implements OnInit, OnDestroy, ControlValueAccessor {

  @ViewChild('subscriptionsPanel')
  subscriptionsFilterPanel: TemplateRef<any>;

  @Input() disabled: boolean;

  @coerceBoolean()
  @Input()
  buttonMode = true;

  @coerceBoolean()
  @Input()
  propagatedFilter = true;

  @Input()
  initialClientSubscriptionFilterConfig: ClientSubscriptionFilterConfig;

  booleanList = [true, false];
  qosList = mqttQoSTypes;
  qoSValuesMap = mqttQoSValuesMap;
  rhOptions = RhOptions;
  panelMode = false;
  buttonDisplayValue = this.translate.instant('mqtt-client-session.filter-title');
  subscriptionsFilterConfigForm: UntypedFormGroup;
  subscriptionsFilterOverlayRef: OverlayRef;
  panelResult: ClientSubscriptionFilterConfig = null;
  entityType = EntityType;

  private subscriptionsFilterConfig: ClientSubscriptionFilterConfig;
  private resizeWindows: Subscription;
  private propagateChange = (_: any) => {};

  constructor(@Optional() @Inject(SUBSCRIPTIONS_FILTER_CONFIG_DATA)
              private data: SubscriptionsFilterConfigData | undefined,
              @Optional()
              private overlayRef: OverlayRef,
              private fb: UntypedFormBuilder,
              private translate: TranslateService,
              private overlay: Overlay,
              private nativeElement: ElementRef,
              private viewContainerRef: ViewContainerRef) {
  }

  ngOnInit(): void {
    if (this.data) {
      this.panelMode = this.data.panelMode;
      this.subscriptionsFilterConfig = this.data.subscriptionsFilterConfig;
      this.initialClientSubscriptionFilterConfig = this.data.initialClientSubscriptionFilterConfig;
      if (this.panelMode && !this.initialClientSubscriptionFilterConfig) {
        this.initialClientSubscriptionFilterConfig = deepClone(this.subscriptionsFilterConfig);
      }
    }
    this.subscriptionsFilterConfigForm = this.fb.group({
      clientId: [null, []],
      topicFilter: [null, []],
      qosList: [null, []],
      noLocalList: [null, []],
      retainAsPublishList: [null, []],
      retainHandlingList: [null, []],
      subscriptionId: [null, []],
    });
    this.subscriptionsFilterConfigForm.valueChanges.subscribe(
      () => {
        if (!this.buttonMode) {
          this.subscriptionsConfigUpdated(this.subscriptionsFilterConfigForm.value);
        }
      }
    );
    if (this.panelMode) {
      this.updateSubscriptionsConfigForm(this.subscriptionsFilterConfig);
    }
    this.initialClientSubscriptionFilterConfig = this.subscriptionsFilterConfigForm.getRawValue();
  }

  ngOnDestroy(): void {
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.subscriptionsFilterConfigForm.disable({emitEvent: false});
    } else {
      this.subscriptionsFilterConfigForm.enable({emitEvent: false});
    }
  }

  writeValue(subscriptionsFilterConfig?: ClientSubscriptionFilterConfig): void {
    this.subscriptionsFilterConfig = subscriptionsFilterConfig;
    if (!this.initialClientSubscriptionFilterConfig && subscriptionsFilterConfig) {
      this.initialClientSubscriptionFilterConfig = deepClone(subscriptionsFilterConfig);
    }
    this.updateButtonDisplayValue();
    this.updateSubscriptionsConfigForm(subscriptionsFilterConfig);
  }

  toggleFilterPanel($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    const config = new OverlayConfig({
      panelClass: 'tb-filter-panel',
      backdropClass: 'cdk-overlay-transparent-backdrop',
      hasBackdrop: true,
      maxHeight: '80vh',
      height: 'min-content',
      minWidth: ''
    });
    config.hasBackdrop = true;
    config.positionStrategy = this.overlay.position()
      .flexibleConnectedTo(this.nativeElement)
      .withPositions([POSITION_MAP.bottomLeft]);

    this.subscriptionsFilterOverlayRef = this.overlay.create(config);
    this.subscriptionsFilterOverlayRef.backdropClick().subscribe(() => {
      this.subscriptionsFilterOverlayRef.dispose();
    });
    this.subscriptionsFilterOverlayRef.attach(new TemplatePortal(this.subscriptionsFilterPanel,
      this.viewContainerRef));
    this.resizeWindows = fromEvent(window, 'resize').subscribe(() => {
      this.subscriptionsFilterOverlayRef.updatePosition();
    });
  }

  cancel() {
    this.updateSubscriptionsConfigForm(this.subscriptionsFilterConfig);
    if (this.overlayRef) {
      this.overlayRef.dispose();
    } else {
      this.resizeWindows.unsubscribe();
      this.subscriptionsFilterOverlayRef.dispose();
    }
  }

  update() {
    this.subscriptionsConfigUpdated(this.subscriptionsFilterConfigForm.value);
    this.subscriptionsFilterConfigForm.markAsPristine();
    if (this.panelMode) {
      this.panelResult = this.subscriptionsFilterConfig;
    }
    if (this.overlayRef) {
      this.overlayRef.dispose();
    } else {
      this.resizeWindows.unsubscribe();
      this.subscriptionsFilterOverlayRef.dispose();
    }
  }

  reset() {
    if (this.initialClientSubscriptionFilterConfig) {
      if (this.buttonMode || this.panelMode) {
        const subscriptionFilterConfig = this.subscriptionsFilterConfigFromFormValue(this.subscriptionsFilterConfigForm.value);
        if (!subscriptionsFilterConfigEquals(subscriptionFilterConfig, this.initialClientSubscriptionFilterConfig)) {
          this.updateSubscriptionsConfigForm(this.initialClientSubscriptionFilterConfig);
          this.subscriptionsFilterConfigForm.markAsDirty();
        }
      } else {
        if (!subscriptionsFilterConfigEquals(this.subscriptionsFilterConfig, this.initialClientSubscriptionFilterConfig)) {
          this.subscriptionsFilterConfig = this.initialClientSubscriptionFilterConfig;
          this.updateButtonDisplayValue();
          this.updateSubscriptionsConfigForm(this.subscriptionsFilterConfig);
          this.propagateChange(this.subscriptionsFilterConfig);
        }
      }
    }
  }

  private updateSubscriptionsConfigForm(filter?: ClientSubscriptionFilterConfig) {
    this.subscriptionsFilterConfigForm.patchValue({
      clientId: filter?.clientId,
      topicFilter: filter?.topicFilter,
      qosList: filter?.qosList,
      noLocalList: filter?.noLocalList,
      retainAsPublishList: filter?.retainAsPublishList,
      retainHandlingList: filter?.retainHandlingList,
      subscriptionId: filter?.subscriptionId
    }, {emitEvent: false});
  }

  private subscriptionsConfigUpdated(formValue: any) {
    this.subscriptionsFilterConfig = this.subscriptionsFilterConfigFromFormValue(formValue);
    this.updateButtonDisplayValue();
    this.propagateChange(this.subscriptionsFilterConfig);
  }

  private subscriptionsFilterConfigFromFormValue(formValue: ClientSubscriptionFilterConfig): ClientSubscriptionFilterConfig {
    return {
      clientId: formValue.clientId,
      topicFilter: formValue.topicFilter,
      qosList: formValue.qosList,
      noLocalList: formValue.noLocalList,
      retainAsPublishList: formValue.retainAsPublishList,
      retainHandlingList: formValue.retainHandlingList,
      subscriptionId: formValue.subscriptionId
    };
  }

  private updateButtonDisplayValue() {
    if (this.buttonMode) {
      const filterTextParts: string[] = [];
      if (this.subscriptionsFilterConfig?.clientId?.length) {
        filterTextParts.push(this.subscriptionsFilterConfig.clientId);
      }
      if (this.subscriptionsFilterConfig?.topicFilter?.length) {
        filterTextParts.push(this.subscriptionsFilterConfig.topicFilter);
      }
      if (this.subscriptionsFilterConfig?.qosList?.length) {
        filterTextParts.push(`${this.translate.instant('mqtt-client-session.qos')}:${this.subscriptionsFilterConfig.qosList.join(', ')}`);
      }
      if (this.subscriptionsFilterConfig?.noLocalList?.length) {
        filterTextParts.push(`nl:${this.subscriptionsFilterConfig.noLocalList.join(', ')}`);
      }
      if (this.subscriptionsFilterConfig?.retainAsPublishList?.length) {
        filterTextParts.push(`rap:${this.subscriptionsFilterConfig.retainAsPublishList.join(', ')}`);
      }
      if (this.subscriptionsFilterConfig?.retainHandlingList?.length) {
        filterTextParts.push(`rh:${this.subscriptionsFilterConfig.retainHandlingList.join(', ')}`);
      }
      if (this.subscriptionsFilterConfig?.subscriptionId) {
        filterTextParts.push(`ID:${this.subscriptionsFilterConfig.subscriptionId.toString()}`);
      }
      if (!filterTextParts.length) {
        this.buttonDisplayValue = this.translate.instant('mqtt-client-session.filter-title');
      } else {
        this.buttonDisplayValue = this.translate.instant('mqtt-client-session.filter-title') + `: ${filterTextParts.join('; ')}`;
      }
    }
  }

}
