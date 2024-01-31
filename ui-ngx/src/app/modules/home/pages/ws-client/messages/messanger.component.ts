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
import { isDefinedAndNotNull } from '@core/utils';
import { MatDialog } from '@angular/material/dialog';
import { PropertiesDialogComponent } from '@home/pages/ws-client/messages/properties-dialog.component';
import {
  Connection,
  ConnectionStatus,
  PublishMessageProperties,
  WsMessagesTypeFilters, WsPayloadFormats,
  WsSubscription
} from '@shared/models/ws-client.model';
import { ValueType } from '@shared/models/constants';
import { MessageFilterConfig } from '@home/pages/ws-client/messages/message-filter-config.component';

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
        payloadFormatIndicator: [false, []],
        messageExpiryInterval: [undefined, []],
        topicAlias: [undefined, []],
        responseTopic: [undefined, []],
        correlationData: [undefined, []],
        userProperties: [undefined, []],
        subscriptionIdentifier: [undefined, []],
        contentType: [undefined, []]
      })
    });

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
    });
  }

  publishMessage(): void {
    const value = this.messangerFormGroup.get('payload').value;
    const payloadFormat = this.messangerFormGroup.get('payloadFormat').value;
    const payload = (payloadFormat === ValueType.JSON) ? JSON.stringify(value) : value;
    const topic = this.messangerFormGroup.get('topic').value;
    const qos = this.messangerFormGroup.get('qos').value;
    const retain = this.messangerFormGroup.get('retain').value;
    const properties = this.messangerFormGroup.get('properties').value;
    const publishOpts = {
      payload,
      topic,
      options: {
        qos,
        retain,
        // properties
      }
    }
    console.log('message', publishOpts)
    this.wsClientService.publishMessage(publishOpts);
  }

  clearHistory() {
    this.wsClientService.clearHistory();
  }

  filterChanged(value: MessageFilterConfig) {
    this.wsClientService.filterMessages(value);
  }

  messagePropertiesDialog() {
    this.dialog.open<PropertiesDialogComponent, null, PublishMessageProperties>(PropertiesDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog']
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
}
