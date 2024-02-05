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

import { Component, OnInit } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { FormBuilder, UntypedFormGroup } from '@angular/forms';
import { WsMqttQoSType, WsQoSTranslationMap, WsQoSTypes } from '@shared/models/session.model';
import { WsClientService } from '@core/http/ws-client.service';
import { isDefinedAndNotNull, isNotEmptyStr } from '@core/utils';
import { MatDialog } from '@angular/material/dialog';
import { PropertiesDialogComponent, PropertiesDialogComponentData } from '@home/pages/ws-client/messages/properties-dialog.component';
import {
  Connection,
  ConnectionStatus,
  PublishMessageProperties,
  WsMessagesTypeFilters,
  WsPayloadFormats,
  WsSubscription, WsTableMessage
} from '@shared/models/ws-client.model';
import { ValueType } from '@shared/models/constants';
import { MessageFilterConfig } from '@home/pages/ws-client/messages/message-filter-config.component';
import { Buffer } from 'buffer';

@Component({
  selector: 'tb-messanger',
  templateUrl: './messanger.component.html',
  styleUrls: ['./messanger.component.scss']
})
export class MessangerComponent implements OnInit {

  connection: Connection;
  connections: Connection[];
  subscriptions: WsSubscription[];
  filterConfig: MessageFilterConfig;
  messangerFormGroup: UntypedFormGroup;

  qoSTypes = WsQoSTypes;
  qoSTranslationMap = WsQoSTranslationMap;
  payloadFormats = WsPayloadFormats;
  messagesTypeFilters = WsMessagesTypeFilters;

  isConnected: boolean;
  selectedOption = 'all';
  jsonFormatSelected = true;
  isPayloadValid = true;
  mqttVersion = 5;

  constructor(protected store: Store<AppState>,
              private wsClientService: WsClientService,
              public fb: FormBuilder,
              private dialog: MatDialog) {

  }

  ngOnInit() {
    this.messangerFormGroup = this.fb.group({
      payload: [{temperature: 25}, []],
      topic: ['sensors/temperature', []],
      qos: [WsMqttQoSType.AT_LEAST_ONCE, []],
      payloadFormat: [ValueType.JSON, []],
      retain: [false, []],
      properties: this.fb.group({
        payloadFormatIndicator: [undefined, []],
        messageExpiryInterval: [undefined, []],
        topicAlias: [undefined, []],
        responseTopic: [undefined, []],
        correlationData: [undefined, []],
        userProperties: [undefined, []],
        subscriptionIdentifier: [undefined, []],
        contentType: [undefined, []]
      })
    });

    this.wsClientService.selectedConnection$.subscribe(
      connection => {
        if (connection) {
          this.mqttVersion = connection.configuration.mqttVersion;
        }
      }
    )

    this.wsClientService.selectedConnectionStatus$.subscribe(
      status => {
        this.isConnected = status === ConnectionStatus.CONNECTED;
      }
    );

    this.wsClientService.filterMessages({
      topic: null,
      qosList: null,
      retainList: null
    });

    this.messangerFormGroup.get('payloadFormat').valueChanges.subscribe(value => {
      this.jsonFormatSelected = value === ValueType.JSON;
      if (!this.jsonFormatSelected) {
        this.isPayloadValid = true;
      }
    });
  }

  publishMessage(): void {
    const value = this.messangerFormGroup.get('payload').value;
    const payloadFormat = this.messangerFormGroup.get('payloadFormat').value;
    const message = (payloadFormat === ValueType.JSON) ? JSON.stringify(value) : value;
    const topic = this.messangerFormGroup.get('topic').value;
    const qos = this.messangerFormGroup.get('qos').value;
    const retain = this.messangerFormGroup.get('retain').value;
    const propertiesForm = this.messangerFormGroup.get('properties').value;
    const options: WsTableMessage = {
      qos,
      retain
    };
    if (this.mqttVersion === 5) {
      options.properties = {};
      if (isDefinedAndNotNull(propertiesForm?.payloadFormatIndicator)) options.properties.payloadFormatIndicator = propertiesForm.payloadFormatIndicator;
      if (isNotEmptyStr(propertiesForm?.messageExpiryInterval)) options.properties.messageExpiryInterval = propertiesForm.messageExpiryInterval;
      if (isNotEmptyStr(propertiesForm?.topicAlias)) options.properties.topicAlias = propertiesForm.topicAlias;
      if (isNotEmptyStr(propertiesForm?.correlationData)) options.properties.correlationData = Buffer.from(propertiesForm.correlationData);
      if (isDefinedAndNotNull(propertiesForm?.userProperties)) options.properties.userProperties = propertiesForm.userProperties;
      if (isNotEmptyStr(propertiesForm?.contentType)) options.properties.contentType = propertiesForm.contentType;
    }
    console.log(`On topic ${topic} send message ${message} with qos ${qos}, retain ${retain}`);
    console.log('props', JSON.stringify(options?.properties));
    this.wsClientService.publishMessage(topic, message, options);
  }

  clearHistory() {
    this.wsClientService.clearHistory();
  }

  filterChanged(value: MessageFilterConfig) {
    this.wsClientService.filterMessages(value);
  }

  messagePropertiesDialog() {
    this.dialog.open<PropertiesDialogComponent, PropertiesDialogComponentData, PublishMessageProperties>(PropertiesDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        mqttVersion: this.mqttVersion
      }
    }).afterClosed()
      .subscribe((properties) => {
        this.messangerFormGroup.patchValue({
          properties: isDefinedAndNotNull(properties) ? properties : null
        });
      });
  }

  onMessageFilterChange(type: string) {
    this.wsClientService.filterMessages({type});
  }

  onJsonValidation(isValid: boolean) {
    this.isPayloadValid = isValid;
  }
}
