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

import { Component, OnDestroy, OnInit } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { FormBuilder, FormControl, UntypedFormGroup, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MqttJsClientService } from '@core/http/mqtt-js-client.service';
import { saveTopicsToLocalStorage, filterTopics, isDefinedAndNotNull } from '@core/utils';
import { MatDialog } from '@angular/material/dialog';
import { WsPublishMessagePropertiesDialogComponent, PropertiesDialogComponentData } from '@home/pages/ws-client/messages/ws-publish-message-properties-dialog.component';
import {
  ConnectionStatus,
  defaultPublishTopic,
  MessageFilterConfig,
  MessageFilterDefaultConfig,
  PublishMessageProperties,
  WebSocketConnection,
  WsMessagesTypeFilters,
  WsPayloadFormats,
} from '@shared/models/ws-client.model';
import { MediaBreakpoints, ValueType } from '@shared/models/constants';
import { IClientPublishOptions } from 'mqtt';
import { map, takeUntil } from 'rxjs/operators';
import { BreakpointObserver } from '@angular/cdk/layout';
import { TranslateModule } from '@ngx-translate/core';
import { ToggleSelectComponent } from '@shared/components/toggle-select.component';
import { MessageFilterConfigComponent } from './message-filter-config.component';
import { MatButton, MatIconButton } from '@angular/material/button';
import { AsyncPipe } from '@angular/common';
import { MessagesComponent } from './messages.component';
import { MatFormField, MatLabel, MatError, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { ColorInputComponent } from '@shared/components/color-input.component';
import { MatSelect } from '@angular/material/select';
import { MatOption } from '@angular/material/core';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatTooltip } from '@angular/material/tooltip';
import { WsJsonObjectEditComponent } from './ws-json-object-edit.component';
import { MatIcon } from '@angular/material/icon';
import { DEFAULT_QOS } from '@shared/models/session.model';
import { QosSelectComponent } from '@shared/components/qos-select.component';
import { MatAutocomplete, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { Observable, Subject } from 'rxjs';

@Component({
    selector: 'tb-messanger',
    templateUrl: './messanger.component.html',
    styleUrls: ['./messanger.component.scss'],
    imports: [TranslateModule, ToggleSelectComponent, FormsModule, MessageFilterConfigComponent, MatButton, MessagesComponent, ReactiveFormsModule, MatFormField, MatLabel, MatInput, MatError, ColorInputComponent, MatSuffix, MatSelect, MatOption, MatSlideToggle, MatTooltip, WsJsonObjectEditComponent, MatIconButton, MatIcon, AsyncPipe, QosSelectComponent, MatAutocomplete, MatAutocompleteTrigger]
})
export class MessangerComponent implements OnInit, OnDestroy {

  connection: WebSocketConnection;
  filterConfig: MessageFilterConfig;
  messangerFormGroup: UntypedFormGroup;

  payloadFormats = WsPayloadFormats;
  messagesTypeFilters = WsMessagesTypeFilters;

  isConnected: boolean;
  selectedOption = 'all';
  jsonFormatSelected = true;
  isPayloadValid = true;
  mqttVersion = 5;
  applyTopicAlias = false;

  publishMsgProps: PublishMessageProperties = null;
  isLtLg = this.breakpointObserver.observe(MediaBreakpoints['lt-lg']).pipe(map(({matches}) => !!matches));
  filteredTopics: Observable<string[]>;

  private destroy$ = new Subject<void>();

  constructor(protected store: Store<AppState>,
              private mqttJsClientService: MqttJsClientService,
              private breakpointObserver: BreakpointObserver,
              private fb: FormBuilder,
              private dialog: MatDialog) {
  }

  ngOnInit() {
    this.messangerFormGroup = this.fb.group({
      payload: [{temperature: 25}, []],
      topic: [defaultPublishTopic, [this.topicValidator, Validators.required]],
      qos: [DEFAULT_QOS, []],
      payloadFormat: [ValueType.JSON, []],
      retain: [false, []],
      color: ['#CECECE', []],
      properties: this.fb.group({
        payloadFormatIndicator: [undefined, []],
        messageExpiryInterval: [undefined, []],
        messageExpiryIntervalUnit: [undefined, []],
        topicAlias: [undefined, []],
        responseTopic: [undefined, []],
        correlationData: [undefined, []],
        userProperties: [undefined, []],
        contentType: [undefined, []]
      })
    });

    this.mqttJsClientService.connection$
      .pipe(takeUntil(this.destroy$))
      .subscribe(
      connection => {
        if (connection) {
          this.connection = connection;
          this.mqttVersion = connection.configuration.mqttVersion;
          this.resetFilterConfig();
          this.resetMessangerProps();
        }
      }
    );

    this.mqttJsClientService.connectionStatus$.subscribe(
      status => {
        this.isConnected = status?.status === ConnectionStatus.CONNECTED;
      }
    );

    this.mqttJsClientService.messageCounter$.subscribe(value => {
      this.messagesTypeFilters = WsMessagesTypeFilters.map(filter => ({
        ...filter,
        name: `${filter.name} (${value[filter.value]})`,
      }));
    })

    this.resetFilterConfig();

    this.messangerFormGroup.get('payloadFormat').valueChanges.subscribe(value => {
      this.jsonFormatSelected = value === ValueType.JSON;
      if (!this.jsonFormatSelected) {
        this.isPayloadValid = true;
      }
    });

    this.filteredTopics = this.messangerFormGroup.get('topic').valueChanges.pipe(
      takeUntil(this.destroy$),
      map(value => filterTopics(value || ''))
    );
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  publishMessage(): void {
    const {
      payload,
      payloadFormat,
      topic,
      qos,
      retain,
      color,
      properties
    } = this.messangerFormGroup.value;
    const message = this.transformMessage(payload, payloadFormat);
    const options: IClientPublishOptions = {
      qos,
      retain,
      color
    } as IClientPublishOptions;
    if (this.mqttVersion === 5 && Object.values(properties).some(value => isDefinedAndNotNull(value))) {
      options.properties = {};
      if (isDefinedAndNotNull(properties?.payloadFormatIndicator)) options.properties.payloadFormatIndicator = properties.payloadFormatIndicator;
      if (isDefinedAndNotNull(properties?.messageExpiryInterval)) options.properties.messageExpiryInterval = properties.messageExpiryInterval;
      // @ts-ignore
      if (isDefinedAndNotNull(properties?.messageExpiryIntervalUnit)) options.properties.messageExpiryIntervalUnit = properties.messageExpiryIntervalUnit;
      if (isDefinedAndNotNull(properties?.topicAlias) && this.applyTopicAlias) options.properties.topicAlias = properties.topicAlias;
      if (isDefinedAndNotNull(properties?.userProperties) && properties?.userProperties?.props?.length) options.properties.userProperties = properties.userProperties;
      if (isDefinedAndNotNull(properties?.contentType)) options.properties.contentType = properties.contentType;
      if (isDefinedAndNotNull(properties?.correlationData)) options.properties.correlationData = properties.correlationData;
      if (isDefinedAndNotNull(properties?.responseTopic)) options.properties.responseTopic = properties.responseTopic;
    }
    this.mqttJsClientService.publishMessage(topic, message, options);
    saveTopicsToLocalStorage(topic);
  }

  clearHistory() {
    this.mqttJsClientService.clearMessages();
  }

  filterChanged(value: MessageFilterConfig) {
    this.mqttJsClientService.filterMessages(value);
  }

  messagePropertiesDialog() {
    this.dialog.open<WsPublishMessagePropertiesDialogComponent, PropertiesDialogComponentData, PublishMessageProperties>(WsPublishMessagePropertiesDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        props: this.publishMsgProps,
        connection: this.connection
      }
    }).afterClosed()
      .subscribe((properties) => {
        if (isDefinedAndNotNull(properties)) {
          this.publishMsgProps = properties;
          this.applyTopicAlias = !!properties.topicAlias;
          this.messangerFormGroup.patchValue({
            properties: properties
          });
          if (this.applyTopicAlias) {
            this.messangerFormGroup.get('topic').setValidators([this.topicValidator]);
          } else {
            this.messangerFormGroup.get('topic').setValidators([this.topicValidator, Validators.required]);
          }
          this.messangerFormGroup.updateValueAndValidity();
        }
      });
  }

  onMessageFilterChange(type: string) {
    this.selectedOption = type;
    this.mqttJsClientService.filterMessages({type});
  }

  onJsonValidation(isValid: boolean) {
    this.isPayloadValid = isValid;
  }

  private transformMessage(payload: string, payloadFormat: ValueType) {
    if (payloadFormat === ValueType.JSON) {
      return JSON.stringify(payload) === 'null' ? '' : JSON.stringify(payload);
    }
    return payload;
  }

  private topicValidator(control: FormControl): {[key: string]: boolean} | null {
    const invalidChars = /[+#]/;
    const isValid = !invalidChars.test(control.value) && control.value[0] !== '$';
    return isValid ? null : { invalidTopic: true };
  }

  private resetFilterConfig() {
    this.filterChanged(MessageFilterDefaultConfig);
  }

  private resetMessangerProps() {
    this.publishMsgProps = null;
    this.applyTopicAlias = false;
    if (!this.messangerFormGroup?.get('topic')?.value?.length) {
      this.messangerFormGroup.patchValue({
        topic: defaultPublishTopic
      });
    }
  }
}
