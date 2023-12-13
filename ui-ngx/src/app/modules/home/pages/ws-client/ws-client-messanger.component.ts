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

import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { TranslateService } from '@ngx-translate/core';
import { FormBuilder, UntypedFormGroup } from '@angular/forms';
import { DialogService } from '@core/services/dialog.service';
import { Direction, SortOrder } from '@shared/models/page/sort-order';
import { DatePipe } from '@angular/common';
import { DateAgoPipe } from '@shared/pipe/date-ago.pipe';
import mqtt from 'mqtt';
import { MqttQoS, MqttQoSType, mqttQoSTypes } from '@shared/models/session.model';
import { WsClientService } from '@core/http/ws-client.service';
import { isDefinedAndNotNull } from '@core/utils';
import { MessagesDisplayData } from './messages.component';

export interface AlarmComment {

}

@Component({
  selector: 'tb-ws-client-messanger',
  templateUrl: './ws-client-messanger.component.html',
  styleUrls: ['./ws-client-messanger.component.scss']
})
export class WsClientMessangerComponent implements OnInit, OnChanges {

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

  alarmComments: Array<AlarmComment>;

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
              private dialogService: DialogService) {
  }

  ngOnInit() {
    this.messangerFormGroup = this.fb.group(
      {
        value: ['null', []],
        topic: ['testtopic/1', []],
        qos: [MqttQoS.AT_LEAST_ONCE, []],
        retain: [false, []],
        meta: [null, []]
      }
    );
    this.wsClientService.selectedConnection$.subscribe(
      value => {
        if (isDefinedAndNotNull(value)) {
          this.wsClientService.getConnection(value.id).subscribe(
            res => {
              this.client = res;
              this.wsClientService.connectClient(res);
              console.log('this.wsClientService.getMqttJsClient()', this.wsClientService.getMqttJsClient())
              this.connectMqttJsClient(this.wsClientService.getMqttJsClient());
              this.subscribeForTopics(res.subscriptions);
            }
          );
        }
      }
    )
    this.wsClientService.selectedSubscription$.subscribe(
      value => {
        if (isDefinedAndNotNull(value)) {
          this.initSubs(value);
        }
      }
    )
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (propName === 'subscription' && change.currentValue) {
          // this.initClient(change.currentValue?.topic);
        }
      }
    }
  }

  private initSubs(subs: any) {
    const host = this.client?.uri + this.client?.host + this.client?.port + this.client?.path;
    const options = {
      keepalive: 3660,
      clientId: 'tbmq_dev', // this.client?.clientId,
      username: 'tbmq_dev', // this.client?.username,
      password: 'tbmq_dev', // this.client?.password,
      protocolId: 'MQTT'
    }
    if (this.mqttJsClient) {
      this.subscribeForTopic(subs);
    } else {
      const client = this.createMqttJsClient(host, options);
      this.mqttJsClient = client;
      this.mqttJsClients.push(client);
      this.connectMqttJsClient(client);
      this.subscribeForTopic(subs);
    }

  }

  private createMqttJsClient(host, options) {
    return mqtt.connect(host, options);
  }

  subscribeForTopics(subscriptions: any[]) {
    for (let i = 0; i < subscriptions.length; i++) {
      this.subscribeForTopic(subscriptions[i]);
      this.wsClientService.addSubscription(subscriptions[i]);
    }
  }

  subscribeForTopic(subscription) {
    const topic = subscription.topic;
    const qos = 1;
    this.wsClientService.getMqttJsClient().subscribe('testtopic', { qos: 1 }, (mess) => {
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
      this.displayData.push(comment);
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

  saveComment(): void {
    const commentInputValue: string = this.messangerFormGroup.get('value').value;
    // if (commentInputValue) {
    //   this.mqttJsClient.publish(this.messangerFormGroup.get('topic').value, commentInputValue, { qos: 1, retain: false })
    // }

    this.mqttJsClient.publish(this.messangerFormGroup.get('topic').value, commentInputValue, { qos: 1, retain: false })

    /*const commentText = {
      "id": "22e2a2bd-c0e2-4458-bd45-7735a848c4c6",
      "createdTime": 1697194451985,
      "additionalInfo": {
        "lastLoginTs": 1701250722871,
        "lang": "en_US"
      },
      "email": "sysadmin@thingsboard.org",
      "authority": "SYS_ADMIN",
      "firstName": null,
      "lastName": null
    };
    const comment: MessagesDisplayData = {
      commentId: '123',
      displayName: this.client?.clientId,
      createdTime: this.datePipe.transform(Date.now()),
      createdDateAgo: 'testtopic/1',
      edit: false,
      isEdited: false,
      editedTime: 'string',
      editedDateAgo: 'string',
      showActions: false,
      commentText,
      isSystemComment: false,
      avatarBgColor: 'blue',
      type: 'pub'
    }
    this.displayData.push(comment);*/
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
}
