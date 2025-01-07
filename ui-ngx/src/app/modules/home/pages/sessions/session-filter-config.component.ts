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
import { ControlValueAccessor, NG_VALUE_ACCESSOR, UntypedFormBuilder, UntypedFormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { coerceBoolean } from '@shared/decorators/coercion';
import { Overlay, OverlayConfig, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';

import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { deepClone, isDefinedAndNotNull } from '@core/utils';
import { EntityType } from '@shared/models/entity-type.models';
import { fromEvent, Subject, Subscription } from 'rxjs';
import {
  ConnectionState,
  connectionStateTranslationMap,
  SessionFilterConfig,
  sessionFilterConfigEquals
} from '@shared/models/session.model';
import { POSITION_MAP } from '@app/shared/models/overlay.models';
import { ClientType, clientTypeIcon, clientTypeTranslationMap } from '@shared/models/client.model';
import { NumericOperation, numericOperationTranslationMap } from '@shared/models/query/query.models';
import { takeUntil } from 'rxjs/operators';
import { NgIf, NgTemplateOutlet, NgFor } from '@angular/common';
import { MatButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { FlexModule } from '@angular/flex-layout/flex';
import { MatChipListbox, MatChipOption } from '@angular/material/chips';
import { MatFormField } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatSelect } from '@angular/material/select';
import { MatOption } from '@angular/material/core';
import { EntitySubTypeListComponent } from '@shared/components/entity/entity-subtype-list.component';

export const SESSION_FILTER_CONFIG_DATA = new InjectionToken<any>('SessionFilterConfigData');

export interface SessionFilterConfigData {
  panelMode: boolean;
  sessionFilterConfig: SessionFilterConfig;
  initialSessionFilterConfig?: SessionFilterConfig;
}

// @dynamic
@Component({
    selector: 'tb-session-filter-config',
    templateUrl: './session-filter-config.component.html',
    styleUrls: ['./session-filter-config.component.scss'],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => SessionFilterConfigComponent),
            multi: true
        }
    ],
    standalone: true,
    imports: [NgIf, NgTemplateOutlet, MatButton, MatTooltip, MatIcon, FormsModule, FlexModule, ReactiveFormsModule, TranslateModule, MatChipListbox, NgFor, MatChipOption, MatFormField, MatInput, MatSelect, MatOption, EntitySubTypeListComponent]
})
export class SessionFilterConfigComponent implements OnInit, OnDestroy, ControlValueAccessor {

  @ViewChild('sessionFilterPanel')
  sessionFilterPanel: TemplateRef<any>;

  @Input() disabled: boolean;

  @coerceBoolean()
  @Input()
  buttonMode = true;

  @coerceBoolean()
  @Input()
  propagatedFilter = true;

  @Input()
  initialSessionFilterConfig: SessionFilterConfig;

  connectionStates = [ConnectionState.CONNECTED, ConnectionState.DISCONNECTED];
  connectionStateTranslationMap= connectionStateTranslationMap;
  ClientType = ClientType;
  clientTypes = [ClientType.APPLICATION, ClientType.DEVICE];
  clientTypeTranslationMap = clientTypeTranslationMap;
  clientTypeIcon = clientTypeIcon;
  cleanStartList = [true, false];
  panelMode = false;
  buttonDisplayValue = this.translate.instant('mqtt-client-session.filter-title');
  buttonDisplayTooltip: string;
  sessionFilterConfigForm: UntypedFormGroup;
  sessionFilterOverlayRef: OverlayRef;
  panelResult: SessionFilterConfig = null;
  entityType = EntityType;
  numericOperations = Object.keys(NumericOperation);
  numericOperationEnum = NumericOperation;
  numericOperationTranslations = numericOperationTranslationMap;

  private sessionFilterConfig: SessionFilterConfig;
  private resizeWindows: Subscription;
  private destroy$ = new Subject<void>();
  private propagateChange = (_: any) => {};

  constructor(@Optional() @Inject(SESSION_FILTER_CONFIG_DATA)
              private data: SessionFilterConfigData | undefined,
              @Optional() private overlayRef: OverlayRef,
              private fb: UntypedFormBuilder,
              private translate: TranslateService,
              private overlay: Overlay,
              private nativeElement: ElementRef,
              private viewContainerRef: ViewContainerRef) {
  }

  ngOnInit(): void {
    if (this.data) {
      this.panelMode = this.data.panelMode;
      this.sessionFilterConfig = this.data.sessionFilterConfig;
      this.initialSessionFilterConfig = this.data.initialSessionFilterConfig;
      if (this.panelMode && !this.initialSessionFilterConfig) {
        this.initialSessionFilterConfig = deepClone(this.sessionFilterConfig);
      }
    }
    this.sessionFilterConfigForm = this.fb.group({
      connectedStatusList: [null, []],
      clientTypeList: [null, []],
      cleanStartList: [null, []],
      nodeIdList: [null, []],
      clientId: [null, []],
      subscriptions: [null, []],
      subscriptionOperation: [null, []],
      clientIpAddress: [null, []],
    });
    this.sessionFilterConfigForm.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        if (!this.buttonMode) {
          this.sessionConfigUpdated(this.sessionFilterConfigForm.value);
        }
      });
    if (this.panelMode) {
      this.updateSessionConfigForm(this.sessionFilterConfig);
    }
    this.initialSessionFilterConfig = this.sessionFilterConfigForm.getRawValue();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();}

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.sessionFilterConfigForm.disable({emitEvent: false});
    } else {
      this.sessionFilterConfigForm.enable({emitEvent: false});
    }
  }

  writeValue(sessionFilterConfig?: SessionFilterConfig): void {
    this.sessionFilterConfig = sessionFilterConfig;
    if (!this.initialSessionFilterConfig && sessionFilterConfig) {
      this.initialSessionFilterConfig = deepClone(sessionFilterConfig);
    }
    this.updateButtonDisplayValue();
    this.updateSessionConfigForm(sessionFilterConfig);
  }

  toggleSessionFilterPanel($event: Event) {
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

    this.sessionFilterOverlayRef = this.overlay.create(config);
    this.sessionFilterOverlayRef.backdropClick().subscribe(() => {
      this.sessionFilterOverlayRef.dispose();
    });
    this.sessionFilterOverlayRef.attach(new TemplatePortal(this.sessionFilterPanel,
      this.viewContainerRef));
    this.resizeWindows = fromEvent(window, 'resize').subscribe(() => {
      this.sessionFilterOverlayRef.updatePosition();
    });
  }

  cancel() {
    this.updateSessionConfigForm(this.sessionFilterConfig);
    if (this.overlayRef) {
      this.overlayRef.dispose();
    } else {
      this.resizeWindows.unsubscribe();
      this.sessionFilterOverlayRef.dispose();
    }
  }

  update() {
    this.sessionConfigUpdated(this.sessionFilterConfigForm.value);
    this.sessionFilterConfigForm.markAsPristine();
    if (this.panelMode) {
      this.panelResult = this.sessionFilterConfig;
    }
    if (this.overlayRef) {
      this.overlayRef.dispose();
    } else {
      this.resizeWindows.unsubscribe();
      this.sessionFilterOverlayRef.dispose();
    }
  }

  reset() {
    if (this.initialSessionFilterConfig) {
      if (this.buttonMode || this.panelMode) {
        const sessionFilterConfig = this.sessionFilterConfigFromFormValue(this.sessionFilterConfigForm.value);
        if (!sessionFilterConfigEquals(sessionFilterConfig, this.initialSessionFilterConfig)) {
          this.updateSessionConfigForm(this.initialSessionFilterConfig);
          this.sessionFilterConfigForm.markAsDirty();
        }
      } else {
        if (!sessionFilterConfigEquals(this.sessionFilterConfig, this.initialSessionFilterConfig)) {
          this.sessionFilterConfig = this.initialSessionFilterConfig;
          this.updateButtonDisplayValue();
          this.updateSessionConfigForm(this.sessionFilterConfig);
          this.propagateChange(this.sessionFilterConfig);
        }
      }
    }
  }

  private updateSessionConfigForm(sessiohFilterConfig?: SessionFilterConfig) {
    this.sessionFilterConfigForm.patchValue({
      connectedStatusList: sessiohFilterConfig?.connectedStatusList,
      clientTypeList: sessiohFilterConfig?.clientTypeList,
      cleanStartList: sessiohFilterConfig?.cleanStartList,
      nodeIdList: sessiohFilterConfig?.nodeIdList,
      clientId: sessiohFilterConfig?.clientId,
      subscriptions: sessiohFilterConfig?.subscriptions,
      subscriptionOperation: sessiohFilterConfig?.subscriptionOperation || NumericOperation.EQUAL,
      clientIpAddress: sessiohFilterConfig?.clientIpAddress,
    }, {emitEvent: false});
  }

  private sessionConfigUpdated(formValue: any) {
    this.sessionFilterConfig = this.sessionFilterConfigFromFormValue(formValue);
    this.updateButtonDisplayValue();
    this.propagateChange(this.sessionFilterConfig);
  }

  private sessionFilterConfigFromFormValue(formValue: any): SessionFilterConfig {
    return {
      connectedStatusList: formValue.connectedStatusList,
      clientTypeList: formValue.clientTypeList,
      cleanStartList: formValue.cleanStartList,
      nodeIdList: formValue.nodeIdList,
      clientId: formValue.clientId,
      subscriptions: formValue.subscriptions,
      subscriptionOperation: formValue.subscriptionOperation,
      clientIpAddress: formValue.clientIpAddress,};
  }

  private updateButtonDisplayValue() {
    if (this.buttonMode) {
      const filterTextParts: string[] = [];
      const filterTooltipParts: string[] = [];
      if (this.sessionFilterConfig?.connectedStatusList?.length) {
        const connectedStatusList = this.sessionFilterConfig.connectedStatusList.map(s =>
          this.translate.instant(connectionStateTranslationMap.get(s))).join(', ');
        filterTextParts.push(connectedStatusList);
        filterTooltipParts.push(`${this.translate.instant('mqtt-client-session.connected-status')}: ${connectedStatusList}`);
      }
      if (this.sessionFilterConfig?.clientTypeList?.length) {
        const clientTypeList = this.sessionFilterConfig.clientTypeList.map(s =>
          this.translate.instant(clientTypeTranslationMap.get(s))).join(', ');
        filterTextParts.push(clientTypeList);
        filterTooltipParts.push(`${this.translate.instant('mqtt-client.client-type')}: ${clientTypeList}`);
      }
      if (this.sessionFilterConfig?.cleanStartList?.length) {
        const cleanStartList = `${this.translate.instant('mqtt-client-session.clean-start')}: ${this.sessionFilterConfig.cleanStartList.join(', ')}`;
        filterTextParts.push(cleanStartList);
        filterTooltipParts.push(cleanStartList);
      }
      if (this.sessionFilterConfig?.clientId?.length) {
        const clientId = this.sessionFilterConfig.clientId;
        filterTextParts.push(clientId);
        filterTooltipParts.push(`${this.translate.instant('mqtt-client.client-id')}: ${clientId}`);
      }
      if (this.sessionFilterConfig?.clientIpAddress?.length) {
        const clientIpAddress = this.sessionFilterConfig.clientIpAddress;
        filterTextParts.push(clientIpAddress);
        filterTooltipParts.push(`${this.translate.instant('mqtt-client-session.client-ip')}: ${clientIpAddress}`);
      }
      if (isDefinedAndNotNull(this.sessionFilterConfig?.subscriptions)) {
        const subscriptions = this.sessionFilterConfig.subscriptions;
        filterTextParts.push(`SUBS: ${subscriptions}`);
        filterTooltipParts.push(`${this.translate.instant('mqtt-client-session.subscriptions')}: ${subscriptions}`);
      }
      if (this.sessionFilterConfig?.nodeIdList?.length) {
        const nodeIdList = this.sessionFilterConfig.nodeIdList.join(', ');
        filterTextParts.push(nodeIdList);
        filterTooltipParts.push(`${this.translate.instant('mqtt-client-session.node-id')}: ${nodeIdList}`);
      }
      if (!filterTextParts.length) {
        this.buttonDisplayValue = this.translate.instant('mqtt-client-session.filter-title');
        this.buttonDisplayTooltip = null;
      } else {
        this.buttonDisplayValue = this.translate.instant('mqtt-client-session.filter-title') + `: ${filterTextParts.join('; ')}`;
        this.buttonDisplayTooltip = filterTooltipParts.join('; ');
      }
    }
  }

}
