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

import { Component, OnInit } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { FormBuilder, UntypedFormGroup } from '@angular/forms';
import { WsMqttQoSType, WsQoSTranslationMap, WsQoSTypes } from '@shared/models/session.model';
import { MqttJsClientService } from '@core/http/mqtt-js-client.service';
import { isDefinedAndNotNull } from '@core/utils';
import { MatDialog } from '@angular/material/dialog';
import { WsPublishMessagePropertiesDialogComponent, PropertiesDialogComponentData } from '@home/pages/ws-client/messages/ws-publish-message-properties-dialog.component';
import {
  Connection,
  ConnectionStatus,
  PublishMessageProperties,
  WsMessagesTypeFilters,
  WsPayloadFormats,
  WsSubscription
} from '@shared/models/ws-client.model';
import { ValueType } from '@shared/models/constants';
import { MessageFilterConfig } from '@home/pages/ws-client/messages/message-filter-config.component';
import { IClientPublishOptions } from 'mqtt';

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

  private publishMessagePropertiesTemp: PublishMessageProperties = null;

  constructor(protected store: Store<AppState>,
              private mqttJsClientService: MqttJsClientService,
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
        messageExpiryIntervalUnit: [undefined, []],
        topicAlias: [undefined, []],
        responseTopic: [undefined, []],
        correlationData: [undefined, []],
        userProperties: [undefined, []],
        contentType: [undefined, []]
      })
    });

    this.mqttJsClientService.selectedConnection$.subscribe(
      connection => {
        if (connection) {
          this.mqttVersion = connection.configuration.mqttVersion;
        }
      }
    )

    this.mqttJsClientService.selectedConnectionStatus$.subscribe(
      status => {
        this.isConnected = status === ConnectionStatus.CONNECTED;
      }
    );

    this.mqttJsClientService.filterMessages({
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
    const payload = this.messangerFormGroup.get('payload').value;
    const payloadFormat = this.messangerFormGroup.get('payloadFormat').value;
    const message = this.transformMessage(payload, payloadFormat);
    const topic = this.messangerFormGroup.get('topic').value;
    const qos = this.messangerFormGroup.get('qos').value;
    const retain = this.messangerFormGroup.get('retain').value;
    const propertiesForm = this.messangerFormGroup.get('properties').value;
    const options: IClientPublishOptions = {
      qos,
      retain
    };
    if (this.mqttVersion === 5 && Object.values(propertiesForm).some(value => isDefinedAndNotNull(value))) {
      options.properties = {};
      if (isDefinedAndNotNull(propertiesForm?.payloadFormatIndicator)) options.properties.payloadFormatIndicator = propertiesForm.payloadFormatIndicator;
      if (isDefinedAndNotNull(propertiesForm?.messageExpiryInterval)) options.properties.messageExpiryInterval = propertiesForm.messageExpiryInterval;
      // @ts-ignore
      if (isDefinedAndNotNull(propertiesForm?.messageExpiryIntervalUnit)) options.properties.messageExpiryIntervalUnit = propertiesForm.messageExpiryIntervalUnit;
      if (isDefinedAndNotNull(propertiesForm?.topicAlias)) options.properties.topicAlias = propertiesForm.topicAlias;
      if (isDefinedAndNotNull(propertiesForm?.userProperties)) options.properties.userProperties = propertiesForm.userProperties;
      if (isDefinedAndNotNull(propertiesForm?.contentType)) options.properties.contentType = propertiesForm.contentType;
      if (isDefinedAndNotNull(propertiesForm?.correlationData)) options.properties.correlationData = propertiesForm.correlationData;
      if (isDefinedAndNotNull(propertiesForm?.responseTopic)) options.properties.responseTopic = propertiesForm.responseTopic;
    }
    console.log(`On topic ${topic} send message ${message} with qos ${qos}, retain ${retain}`);
    console.log('props', JSON.stringify(options?.properties));
    this.mqttJsClientService.publishMessage(topic, message, options);
  }

  clearHistory() {
    this.mqttJsClientService.clearHistory();
  }

  filterChanged(value: MessageFilterConfig) {
    this.mqttJsClientService.filterMessages(value);
  }

  messagePropertiesDialog() {
    this.dialog.open<WsPublishMessagePropertiesDialogComponent, PropertiesDialogComponentData, PublishMessageProperties>(WsPublishMessagePropertiesDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        entity: this.publishMessagePropertiesTemp
      }
    }).afterClosed()
      .subscribe((properties) => {
        if (isDefinedAndNotNull(properties)) {
          this.publishMessagePropertiesTemp = properties;
          this.messangerFormGroup.patchValue({
            properties: properties
          });
        }
      });
  }

  onMessageFilterChange(type: string) {
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
}
