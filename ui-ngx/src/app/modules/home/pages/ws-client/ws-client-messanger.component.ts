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

import { Component, Input, OnInit } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { TranslateService } from '@ngx-translate/core';
import { FormBuilder, UntypedFormGroup } from '@angular/forms';
import { DialogService } from '@core/services/dialog.service';
import { Direction, SortOrder } from '@shared/models/page/sort-order';
import { DatePipe } from '@angular/common';
import { DateAgoPipe } from '@shared/pipe/date-ago.pipe';
import { MqttQoS, MqttQoSType, mqttQoSTypes, mqttQoSValuesMap } from '@shared/models/session.model';
import { WsClientService } from '@core/http/ws-client.service';
import { isDefinedAndNotNull } from '@core/utils';
import { MessagesDisplayData } from './messages.component';
import { MatDialog } from '@angular/material/dialog';
import { PropertiesDialogComponent } from '@home/pages/ws-client/properties-dialog.component';
import { PublishMessageProperties } from '@shared/models/ws-client.model';
import mqtt from 'mqtt';

@Component({
  selector: 'tb-ws-client-messanger',
  templateUrl: './ws-client-messanger.component.html',
  styleUrls: ['./ws-client-messanger.component.scss']
})
export class WsClientMessangerComponent implements OnInit {

  client: any;

  @Input()
  subscription: any = null;

  @Input()
  subscriptions: any = null;

  @Input()
  clients: any = null;

  @Input()
  alarmActivityOnly: boolean = false;

  messangerFormGroup: UntypedFormGroup;
  mqttJsClients: any[] = [];

  alarmComments: Array<any>;

  displayData: Array<MessagesDisplayData> = new Array<MessagesDisplayData>();

  alarmCommentSortOrder: SortOrder = {
    property: 'createdTime',
    direction: Direction.DESC
  };

  editMode: boolean = false;

  mqttJsClient: any;
  mqttQoSTypes = mqttQoSTypes;

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private dateAgoPipe: DateAgoPipe,
              private wsClientService: WsClientService,
              public fb: FormBuilder,
              private dialog: MatDialog) {

  }

  ngOnInit() {
    this.messangerFormGroup = this.fb.group(
      {
        value: [null, []],
        topic: ['testtopic', []],
        qos: [MqttQoS.AT_LEAST_ONCE, []],
        retain: [true, []],
        meta: [null, []]
      }
    );
    this.wsClientService.selectedConnection$.subscribe(
      value => {
        if (isDefinedAndNotNull(value)) {
          this.wsClientService.getConnection(value.id).subscribe(
            res => {
              this.client = res;
              /*this.wsClientService.connectClient(res);
              this.connectMqttJsClient(this.wsClientService.getMqttJsClient());
              this.subscribeForTopics(res.subscriptions);*/
            }
          );
        }
      }
    )
    this.wsClientService.selectedSubscription$.subscribe(
      value => {
        if (isDefinedAndNotNull(value)) {
          // this.initSubs(value);
        }
      }
    )
  }

  test() {
    const host = 'ws://localhost:8084/mqtt';
    /*const options = {};
    for (let [key, value] of Object.entries(connection)) {
      options[key] = value;
    }*/
    const options = {
      clientId: 'tbmq_dev', // this.client?.clientId,
      username: 'tbmq_dev', // this.client?.username,
      password: 'tbmq_dev', // this.client?.password,
      protocolId: 'MQTT',
      clean: false,
      keepalive: 60
    };
    // @ts-ignore
    /*const mqttJsClient = mqtt.connect(host, options);
    mqttJsClient.on('connect', () => {
      console.log(`Client connected`)
      // Subscribe
    })
    mqttJsClient.subscribe('testtopic', { qos: 1, nl: false })
    mqttJsClient.on('message', (topic, message, packet) => {
      console.log('999 888', topic, new TextDecoder("utf-8").decode(message));
    });*/

    // mqttJsClient.subscribe('testtopic', {qos: 1});

  }

  private initSubs(subs: any) {
    const host = this.client?.protocol + this.client?.host + ":" + this.client?.port + this.client?.path;
    const options = {
      keepalive: 3660,
      clientId: 'tbmq_dev', // this.client?.clientId,
      username: 'tbmq_dev', // this.client?.username,
      password: 'tbmq_dev', // this.client?.password,
      protocolId: 'MQTT'
    }
    if (this.mqttJsClient) {
      // this.subscribeForTopic(subs);
    } else {
      // const client = this.createMqttJsClient(host, options);
      // this.mqttJsClient = client;
      // this.mqttJsClients.push(client);
      // this.connectMqttJsClient(client);
      // this.subscribeForTopic(subs);
    }

  }

  private createMqttJsClient(host, options) {
    return mqtt.connect(host, options);
  }

  subscribeForTopics(subscriptions: any[]) {
    for (let i = 0; i < subscriptions?.length; i++) {
      this.subscribeForTopic(subscriptions[i]);
      this.wsClientService.addSubscription(subscriptions[i]);
    }
  }

  subscribeForTopic(subscription) {
    const topic = subscription.topic;
    const qos = 1;
    this.wsClientService.getMqttJsConnection().subscribe('testtopic', { qos: 1 }, (mess) => {
      if (mess) {
        this.displayData.push(mess);
        console.log('message', mess)
      }
    })
  }

  connectMqttJsClient(client) {
    client.on('connect', (e) => {
      console.log(`Client connected: ${this.client?.clientId}`, e)
      /*client.subscribe(this.messangerFormGroup.get('topic').value, { qos: 1 }, (mess) => {
        if (mess) {
          this.displayData.push(mess);
          console.log('message', mess)
        }
      })*/
    });
    client.on('message', (topic, message, packet) => {
      const comment: MessagesDisplayData = {
        commentId: '123',
        displayName: this.client?.clientId,
        createdTime: this.datePipe.transform(Date.now()),
        createdDateAgo: topic,
        edit: false,
        isEdited: false,
        editedTime: 'string',
        editedDateAgo: 'string',
        showActions: false,
        commentText: new TextDecoder("utf-8").decode(message),
        isSystemComment: false,
        avatarBgColor: this.subscription?.color,
        type: 'sub'
      }
      // this.displayData.push(comment);
      console.log(`Received Message: ${message.toString()} On topic: ${topic}`)
    });
    client.on('error', (err) => {
      console.log('Connection error: ', err)
      client.end()
    });
    client.on('reconnect', () => {
      console.log('Reconnecting...')
    });
    client.on('close', () => {
      console.log('Closing...')
    });
    client.on('disconnect', () => {
      console.log('Disconnecting...')
    });
    client.on('offline', () => {
      console.log('Offline...')
    });
    client.on('end', () => {
      console.log('End...')
    });
    client.on('packetsend', () => {
      console.log('Packet Send...')
    });
    client.on('packetreceive', () => {
      console.log('Packet Receive...')
    });
  }

  mqttQoSValue(mqttQoSValue: MqttQoSType): string {
    const index = mqttQoSTypes.findIndex(object => {
      return object.value === mqttQoSValue.value;
    });
    const name = this.translate.instant(mqttQoSValue.name);
    return index + ' - ' + name;
  }

  unsubscribeClient() {
    this.mqttJsClient.unsubscribe(this.client?.topic, (e) => {
      console.log('Unsubscribed', e);
    });
  }

  loadAlarmComments(comment: MessagesDisplayData = null): void {
    if (comment) {
      this.displayData.push(comment);
      this.alarmComments = this.displayData;
    } else {
      this.displayData.length = 0;
    }
  }

  publishMessage(): void {
    const commentInputValue: string = this.messangerFormGroup.get('value').value;
    if (commentInputValue) {
      const topic = this.messangerFormGroup.get('topic').value;
      const value = this.messangerFormGroup.get('value').value;
      const qos = mqttQoSValuesMap.get(this.messangerFormGroup.get('qos').value);
      const retain = this.messangerFormGroup.get('retain').value;
      const message = JSON.stringify({
        value: commentInputValue,
        type: 'pub'
      })
      this.wsClientService.getMqttJsConnection().publish(topic, commentInputValue, { qos, retain });
      /*const comment: MessagesDisplayData = {
        commentId: '123',
        displayName: this.client?.clientId,
        createdTime: this.datePipe.transform(Date.now()),
        createdDateAgo: topic,
        edit: false,
        isEdited: false,
        editedTime: 'string',
        editedDateAgo: 'string',
        showActions: false,
        commentText: commentInputValue,
        isSystemComment: false,
        avatarBgColor: this.subscription?.color,
        type: 'pub'
      }
      this.displayData.push(comment);*/
      this.wsClientService.publishMessage({
        message: commentInputValue,
        topic,
        type: 'pub'
      });
    }
  }

  onCommentMouseEnter(commentId: string, displayDataIndex: number): void {
    this.displayData[displayDataIndex].showActions = true;
    /*if (!this.editMode) {
      this.displayData[displayDataIndex].showActions = true;
      const alarmUserId = this.getAlarmCommentById(commentId).userId.id;
      if (this.authUser.userId === alarmUserId) {
        this.displayData[displayDataIndex].showActions = true;
      }
    }*/
  }

  onCommentMouseLeave(displayDataIndex: number): void {
    this.displayData[displayDataIndex].showActions = false;
  }

  clearHistory() {
    this.wsClientService.clearHistory(this.client.id).subscribe();
  }

  openPublishMessageProperties() {
    this.dialog.open<PropertiesDialogComponent, null, PublishMessageProperties>(PropertiesDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog']
    }).afterClosed()
      .subscribe((res) => {
        if (isDefinedAndNotNull(res)) {

        }
      });
  }
}
