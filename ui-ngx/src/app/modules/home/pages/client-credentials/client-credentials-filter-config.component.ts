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
import { deepClone, isNotEmptyStr } from '@core/utils';
import { EntityType } from '@shared/models/entity-type.models';
import { fromEvent, Subscription } from 'rxjs';
import { POSITION_MAP } from '@app/shared/models/overlay.models';
import { ClientType, clientTypeIcon, clientTypeTranslationMap } from '@shared/models/client.model';
import {
  ClientCredentialsFilterConfig,
  clientCredentialsFilterConfigEquals,
  credentialsTypeTranslationMap,
  CredentialsType
} from '@shared/models/credentials.model';

export const CLIENT_CREDENTIALS_FILTER_CONFIG_DATA = new InjectionToken<any>('ClientCredentialsFilterConfigData');

export interface ClientCredentialsFilterConfigData {
  panelMode: boolean;
  clientCredentialsFilterConfig: ClientCredentialsFilterConfig;
  initialClientCredentialsFilterConfig?: ClientCredentialsFilterConfig;
}

// @dynamic
@Component({
  selector: 'tb-client-credentials-filter-config',
  templateUrl: './client-credentials-filter-config.component.html',
  styleUrls: ['./client-credentials-filter-config.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => ClientCredentialsFilterConfigComponent),
      multi: true
    }
  ]
})
export class ClientCredentialsFilterConfigComponent implements OnInit, OnDestroy, ControlValueAccessor {

  @ViewChild('clientCredentialsFilterPanel')
  clientCredentialsFilterPanel: TemplateRef<any>;

  @Input() disabled: boolean;

  @coerceBoolean()
  @Input()
  buttonMode = true;

  @coerceBoolean()
  @Input()
  propagatedFilter = true;

  @Input()
  initialClientCredentialsFilterConfig: ClientCredentialsFilterConfig;

  clientTypes = Object.values(ClientType);
  clientTypeTranslationMap = clientTypeTranslationMap;
  clientTypeIcon = clientTypeIcon;
  clientCredentialsTypes = Object.values(CredentialsType);
  clientCredentialsTypeTranslationMap = credentialsTypeTranslationMap;
  panelMode = false;
  buttonDisplayValue = this.translate.instant('mqtt-client-credentials.filter-title');
  buttonDisplayTooltip: string;
  clientCredentialsFilterConfigForm: UntypedFormGroup;
  clientCredentialsFilterOverlayRef: OverlayRef;
  panelResult: ClientCredentialsFilterConfig = null;
  entityType = EntityType;

  private clientCredentialsFilterConfig: ClientCredentialsFilterConfig;
  private resizeWindows: Subscription;
  private propagateChange = (_: any) => {};

  constructor(@Optional() @Inject(CLIENT_CREDENTIALS_FILTER_CONFIG_DATA)
              private data: ClientCredentialsFilterConfigData | undefined,
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
      this.clientCredentialsFilterConfig = this.data.clientCredentialsFilterConfig;
      this.initialClientCredentialsFilterConfig = this.data.initialClientCredentialsFilterConfig;
      if (this.panelMode && !this.initialClientCredentialsFilterConfig) {
        this.initialClientCredentialsFilterConfig = deepClone(this.clientCredentialsFilterConfig);
      }
    }
    this.clientCredentialsFilterConfigForm = this.fb.group({
      clientTypeList: [null, []],
      credentialsTypeList: [null, []],
      name: [null, []],
      clientId: [null, []],
      username: [null, []],
      certificateCn: [null, []]
    });
    this.clientCredentialsFilterConfigForm.valueChanges.subscribe(
      () => {
        if (!this.buttonMode) {
          this.clientCredentialsConfigUpdated(this.clientCredentialsFilterConfigForm.value);
        }
      }
    );
    if (this.panelMode) {
      this.updateClientCredentialsConfigForm(this.clientCredentialsFilterConfig);
    }
    this.initialClientCredentialsFilterConfig = this.clientCredentialsFilterConfigForm.getRawValue();
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
      this.clientCredentialsFilterConfigForm.disable({emitEvent: false});
    } else {
      this.clientCredentialsFilterConfigForm.enable({emitEvent: false});
    }
  }

  writeValue(clientCredentialsFilterConfig?: ClientCredentialsFilterConfig): void {
    this.clientCredentialsFilterConfig = clientCredentialsFilterConfig;
    if (!this.initialClientCredentialsFilterConfig && clientCredentialsFilterConfig) {
      this.initialClientCredentialsFilterConfig = deepClone(clientCredentialsFilterConfig);
    }
    this.updateButtonDisplayValue();
    this.updateClientCredentialsConfigForm(clientCredentialsFilterConfig);
  }

  toggleClientCredentialsFilterPanel($event: Event) {
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

    this.clientCredentialsFilterOverlayRef = this.overlay.create(config);
    this.clientCredentialsFilterOverlayRef.backdropClick().subscribe(() => {
      this.clientCredentialsFilterOverlayRef.dispose();
    });
    this.clientCredentialsFilterOverlayRef.attach(new TemplatePortal(this.clientCredentialsFilterPanel,
      this.viewContainerRef));
    this.resizeWindows = fromEvent(window, 'resize').subscribe(() => {
      this.clientCredentialsFilterOverlayRef.updatePosition();
    });
  }

  cancel() {
    this.updateClientCredentialsConfigForm(this.clientCredentialsFilterConfig);
    if (this.overlayRef) {
      this.overlayRef.dispose();
    } else {
      this.resizeWindows.unsubscribe();
      this.clientCredentialsFilterOverlayRef.dispose();
    }
  }

  update() {
    this.clientCredentialsConfigUpdated(this.clientCredentialsFilterConfigForm.value);
    this.clientCredentialsFilterConfigForm.markAsPristine();
    if (this.panelMode) {
      this.panelResult = this.clientCredentialsFilterConfig;
    }
    if (this.overlayRef) {
      this.overlayRef.dispose();
    } else {
      this.resizeWindows.unsubscribe();
      this.clientCredentialsFilterOverlayRef.dispose();
    }
  }

  reset() {
    if (this.initialClientCredentialsFilterConfig) {
      if (this.buttonMode || this.panelMode) {
        const clientCredentialsFilterConfig = this.clientCredentialsFilterConfigFromFormValue(this.clientCredentialsFilterConfigForm.value);
        if (!clientCredentialsFilterConfigEquals(clientCredentialsFilterConfig, this.initialClientCredentialsFilterConfig)) {
          this.updateClientCredentialsConfigForm(this.initialClientCredentialsFilterConfig);
          this.clientCredentialsFilterConfigForm.markAsDirty();
        }
      } else {
        if (!clientCredentialsFilterConfigEquals(this.clientCredentialsFilterConfig, this.initialClientCredentialsFilterConfig)) {
          this.clientCredentialsFilterConfig = this.initialClientCredentialsFilterConfig;
          this.updateButtonDisplayValue();
          this.updateClientCredentialsConfigForm(this.clientCredentialsFilterConfig);
          this.propagateChange(this.clientCredentialsFilterConfig);
        }
      }
    }
  }

  private updateClientCredentialsConfigForm(clientCredentialsFilterConfig?: ClientCredentialsFilterConfig) {
    this.clientCredentialsFilterConfigForm.patchValue({
      clientTypeList: clientCredentialsFilterConfig?.clientTypeList,
      credentialsTypeList: clientCredentialsFilterConfig?.credentialsTypeList,
      name: clientCredentialsFilterConfig?.name,
      clientId: clientCredentialsFilterConfig?.clientId,
      username: clientCredentialsFilterConfig?.username,
      certificateCn: clientCredentialsFilterConfig?.certificateCn
    }, {emitEvent: false});
  }

  private clientCredentialsConfigUpdated(formValue: any) {
    this.clientCredentialsFilterConfig = this.clientCredentialsFilterConfigFromFormValue(formValue);
    this.updateButtonDisplayValue();
    this.propagateChange(this.clientCredentialsFilterConfig);
  }

  private clientCredentialsFilterConfigFromFormValue(formValue: ClientCredentialsFilterConfig): ClientCredentialsFilterConfig {
    return {
      clientTypeList: formValue.clientTypeList,
      credentialsTypeList: formValue.credentialsTypeList,
      name: formValue.name,
      clientId: formValue.clientId,
      username: formValue.username,
      certificateCn: formValue.certificateCn,
    };
  }

  private updateButtonDisplayValue() {
    if (this.buttonMode) {
      const filterTextParts: string[] = [];
      const filterTooltipParts: string[] = [];
      if (this.clientCredentialsFilterConfig?.clientTypeList?.length) {
        const clientTypeList = this.clientCredentialsFilterConfig.clientTypeList.map(s =>
          this.translate.instant(clientTypeTranslationMap.get(s))).join(', ');
        filterTextParts.push(clientTypeList);
        filterTooltipParts.push(`${this.translate.instant('mqtt-client.client-type')}: ${clientTypeList}`)
      }
      if (this.clientCredentialsFilterConfig?.credentialsTypeList?.length) {
        const credentialsTypeList = this.clientCredentialsFilterConfig.credentialsTypeList.map(s =>
          this.translate.instant(this.clientCredentialsTypeTranslationMap.get(s))).join(', ');
        filterTextParts.push(credentialsTypeList);
        filterTooltipParts.push(`${this.translate.instant('mqtt-client-credentials.client-credentials')}: ${credentialsTypeList}`);
      }
      if (isNotEmptyStr(this.clientCredentialsFilterConfig?.name)) {
        const name = this.clientCredentialsFilterConfig.name;
        filterTextParts.push(name);
        filterTooltipParts.push(`${this.translate.instant('mqtt-client-credentials.name')}: ${name}`);
      }
      if (isNotEmptyStr(this.clientCredentialsFilterConfig?.clientId)) {
        const clientId = this.clientCredentialsFilterConfig.clientId;
        filterTextParts.push(clientId);
        filterTooltipParts.push(`${this.translate.instant('mqtt-client.client-id')}: ${clientId}`);
      }
      if (isNotEmptyStr(this.clientCredentialsFilterConfig?.username)) {
        const username = this.clientCredentialsFilterConfig.username;
        filterTextParts.push(username);
        filterTooltipParts.push(`${this.translate.instant('mqtt-client-credentials.username')}: ${username}`);
      }
      if (isNotEmptyStr(this.clientCredentialsFilterConfig?.certificateCn)) {
        const certificateCn = this.clientCredentialsFilterConfig.certificateCn;
        filterTextParts.push(certificateCn);
        filterTooltipParts.push(`${this.translate.instant('mqtt-client-credentials.certificate-common-name-filter')}: ${certificateCn}`);
      }
      if (!filterTextParts.length) {
        this.buttonDisplayValue = this.translate.instant('mqtt-client-credentials.filter-title');
        this.buttonDisplayTooltip = null;
      } else {
        this.buttonDisplayValue = this.translate.instant('mqtt-client-credentials.filter-title') + `: ${filterTextParts.join(', ')}`;
        this.buttonDisplayTooltip = filterTooltipParts.join('; ');
      }
    }
  }

}
