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
import { select, Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { TranslateService } from '@ngx-translate/core';
import { AbstractControl, FormBuilder, FormGroup } from '@angular/forms';
import { DialogService } from '@core/services/dialog.service';
import { User } from '@shared/models/user.model';
import { selectUserDetails } from '@core/auth/auth.selectors';
import { Direction, SortOrder } from '@shared/models/page/sort-order';
import { map } from 'rxjs/operators';
import { DatePipe } from '@angular/common';
import { DateAgoPipe } from '@shared/pipe/date-ago.pipe';
import mqtt from 'mqtt';

interface MessagesDisplayData {
  commentId?: string,
  displayName?: string,
  createdTime: string,
  createdDateAgo?: string,
  edit?: boolean,
  isEdited?: boolean,
  editedTime?: string;
  editedDateAgo?: string,
  showActions?: boolean,
  commentText?: string,
  isSystemComment?: boolean,
  avatarBgColor?: string
}

export interface AlarmComment {

}

@Component({
  selector: 'tb-ws-client-comment',
  templateUrl: './client-messanger.component.html',
  styleUrls: ['./client-messanger.component.scss']
})
export class ClientMessangerComponent implements OnInit, OnChanges {

  @Input()
  connection: any = null;

  @Input()
  subscription: any = null;

  @Input()
  subscriptions: any = null;

  @Input()
  clients: any = null;

  @Input()
  alarmActivityOnly: boolean = false;

  messangerFormGroup: FormGroup;

  alarmComments: Array<AlarmComment>;

  displayData: Array<MessagesDisplayData> = new Array<MessagesDisplayData>();

  alarmCommentSortOrder: SortOrder = {
    property: 'createdTime',
    direction: Direction.DESC
  };

  editMode: boolean = false;

  userDisplayName$ = this.store.pipe(
    select(selectUserDetails),
    map((user) => this.getUserDisplayName(user))
  );

  currentUserDisplayName: string;
  currentUserAvatarColor: string;

  client: any;

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private dateAgoPipe: DateAgoPipe,
              public fb: FormBuilder,
              private dialogService: DialogService
  ) {

    this.messangerFormGroup = this.fb.group(
      {
        alarmCommentEdit: [''],
        alarmComment: [''],
        topic: ['testtopic/1']
      }
    );
  }

  ngOnInit() {
    if (this.connection) {
      // this.initClient();
    }
    // this.loadAlarmComments();
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (propName === 'subscription' && change.currentValue) {
          this.initClient(change.currentValue);
        }
      }
    }
  }

  private initClient(sub?: any) {
    const host = this.connection?.uri + this.connection?.host + this.connection?.port + this.connection?.path;
    const options = {
      keepalive: 60,
      clientId: this.connection?.clientId,
      username: this.connection?.username,
      password: this.connection?.password,
      protocolId: 'MQTT'
    }
    if (sub?.topic) {
      const subscription = this.subscriptions.find(el => el.topic === sub.topic);
      if (subscription) {
        this.messangerFormGroup.patchValue({
          topic: subscription.topic,
          qos: subscription.qos,
          retain: subscription.retain
        });
      } else {
        this.client = this.createMqttJsClient(host, options);
        this.connectMqttJsClient();
      }
    }

  }

  private createMqttJsClient(host, options) {
    return mqtt.connect(host, options);
  }

  connectMqttJsClient() {
    this.client.on('connect', (e) => {
      console.log(`Client connected: ${this.connection?.clientId}`, e)
      this.client.subscribe(this.subscription.topic, { qos: 1 }, (mess) => {
        this.displayData.push(mess);
        console.log('message', mess)
      })
    });
    this.client.on('message', (topic, message, packet) => {
      const comment: MessagesDisplayData = {
        commentId: '123',
        displayName: this.connection?.clientId,
        createdTime: this.datePipe.transform(Date.now()),
        createdDateAgo: topic,
        edit: false,
        isEdited: false,
        editedTime: 'string',
        editedDateAgo: 'string',
        showActions: false,
        commentText: message,
        isSystemComment: false,
        avatarBgColor: 'red'
      }
      this.displayData.push(comment);
      console.log(`Received Message: ${message.toString()} On topic: ${topic}`)
    });
    this.client.on('error', (err) => {
      console.log('Connection error: ', err)
      this.client.end()
    });
    this.client.on('reconnect', () => {
      console.log('Reconnecting...')
    });
  }

  unsubscribeClient() {
    this.client.unsubscribe(this.connection?.topic, (e) => {
      console.log('Unsubscribed', e);
    });
  }

  uns

  loadAlarmComments(comment: MessagesDisplayData = null): void {
    if (comment) {
      this.displayData.push(comment);
      this.alarmComments = this.displayData;
    } else {
      this.displayData.length = 0;
    }
  }

  changeSortDirection() {
    let currentDirection = this.alarmCommentSortOrder.direction;
    this.alarmCommentSortOrder.direction = currentDirection === Direction.DESC ? Direction.ASC : Direction.DESC;
    this.loadAlarmComments();
  }

  exportAlarmActivity() {
    let fileName = this.translate.instant('alarm.alarm') + '_' + this.translate.instant('alarm-activity.activity');
    // this.importExportService.exportCsv(this.getDataForExport(), fileName.toLowerCase());
  }

  saveComment(): void {
    const commentInputValue: string = this.getAlarmCommentFormControl().value;
    if (commentInputValue) {
      this.client.publish(this.messangerFormGroup.get('topic').value, commentInputValue, { qos: 1, retain: false })
    }
  }

  saveEditedComment(commentId: string): void {
    const commentEditInputValue: string = this.getAlarmCommentEditFormControl().value;
    if (commentEditInputValue) {
      // const editedComment: AlarmComment = this.getAlarmCommentById(commentId);
      // editedComment.comment.text = commentEditInputValue;
      // this.doSave(editedComment);
      this.clearCommentEditInput();
      this.editMode = false;
      this.getAlarmCommentFormControl().enable({emitEvent: false});
    }
  }

  private doSave(comment: MessagesDisplayData): void {
    // this.emqx(comment);
    // this.loadAlarmComments(comment);
  }

  editComment(commentId: string): void {
    const commentDisplayData = this.getDataElementByCommentId(commentId);
    commentDisplayData.edit = true;
    this.editMode = true;
    this.getAlarmCommentEditFormControl().patchValue(commentDisplayData.commentText);
    this.getAlarmCommentFormControl().disable({emitEvent: false});
  }

  cancelEdit(commentId: string): void {
    const commentDisplayData = this.getDataElementByCommentId(commentId);
    commentDisplayData.edit = false;
    this.editMode = false;
    this.getAlarmCommentFormControl().enable({emitEvent: false});
  }

  deleteComment(commentId: string): void {
    // const alarmCommentInfo: AlarmComment = this.getAlarmCommentById(commentId);
    // const commentText: string = alarmCommentInfo.comment.text;
    this.dialogService.confirm(
      this.translate.instant('alarm-activity.delete-alarm-comment'),
      // commentText,
      this.translate.instant('action.cancel'),
      this.translate.instant('action.delete')).subscribe(
      (result) => {
        if (result) {
          // this.alarmCommentService.deleteAlarmComments(this.alarmId, commentId, {ignoreLoading: true})
          //   .subscribe(() => {
          //       this.loadAlarmComments();
          //     }
          // )
        }
      }
    )
  }

  getSortDirectionIcon() {
    return this.alarmCommentSortOrder.direction === Direction.DESC ? 'mdi:sort-descending' : 'mdi:sort-ascending'
  }

  getSortDirectionTooltipText() {
    let text = this.alarmCommentSortOrder.direction === Direction.DESC ? 'alarm-activity.newest-first' :
      'alarm-activity.oldest-first';
    return this.translate.instant(text);
  }

  isDirectionAscending() {
    return this.alarmCommentSortOrder.direction === Direction.ASC;
  }

  onCommentMouseEnter(commentId: string, displayDataIndex: number): void {
    if (!this.editMode) {
      // const alarmUserId = this.getAlarmCommentById(commentId).userId.id;
      // if (this.authUser.userId === alarmUserId) {
      //   this.displayData[displayDataIndex].showActions = true;
      // }
    }
  }

  onCommentMouseLeave(displayDataIndex: number): void {
    // this.displayData[displayDataIndex].showActions = false;
  }

  getUserInitials(userName: string): string {
    let initials = '';
    const userNameSplit = userName.split(' ');
    initials += userNameSplit[0].charAt(0).toUpperCase();
    if (userNameSplit.length > 1) {
      initials += userNameSplit[userNameSplit.length - 1].charAt(0).toUpperCase();
    }
    return initials;
  }

  getCurrentUserBgColor(userDisplayName: string) {
    // return this.utilsService.stringToHslColor(userDisplayName, 40, 60);
  }

  private getUserDisplayName(alarmCommentInfo: any | User): string {
    let name = '';
    if ((alarmCommentInfo.firstName && alarmCommentInfo.firstName.length > 0) ||
      (alarmCommentInfo.lastName && alarmCommentInfo.lastName.length > 0)) {
      if (alarmCommentInfo.firstName) {
        name += alarmCommentInfo.firstName;
      }
      if (alarmCommentInfo.lastName) {
        if (name.length > 0) {
          name += ' ';
        }
        name += alarmCommentInfo.lastName;
      }
    } else {
      name = alarmCommentInfo.email;
    }
    return name;
  }

  getAlarmCommentFormControl(): AbstractControl {
    return this.messangerFormGroup.get('alarmComment');
  }

  getAlarmCommentEditFormControl(): AbstractControl {
    return this.messangerFormGroup.get('alarmCommentEdit');
  }

  private clearCommentInput(): void {
    this.getAlarmCommentFormControl().patchValue('');
  }

  private clearCommentEditInput(): void {
    this.getAlarmCommentEditFormControl().patchValue('');
  }

  private getAlarmCommentById(id: string): void {
    // return this.alarmComments.find(comment => comment.id.id === id);
  }

  private getDataElementByCommentId(commentId: string): MessagesDisplayData {
    return this.displayData.find(commentDisplayData => commentDisplayData.commentId === commentId);
  }

  private getDataForExport() {
    let dataToExport = [];
    for (let row of this.displayData) {
      let exportRow = {
        [this.translate.instant('alarm-activity.author')]: row.isSystemComment ?
          this.translate.instant('alarm-activity.system') : row.displayName,
        [this.translate.instant('alarm-activity.created-date')]: row.createdTime,
        [this.translate.instant('alarm-activity.edited-date')]: row.editedTime,
        [this.translate.instant('alarm-activity.text')]: row.commentText
      }
      dataToExport.push(exportRow)
    }
    return dataToExport;
  }

  private emqx(comment) {
    // const clientId = 'mqttjs_' + Math.random().toString(16).substr(2, 8)
    // const host = 'ws://broker.emqx.io:8083/mqtt'
    const host = 'ws://localhost:8084/mqtt'
    const options = {
      keepalive: 60,
      clientId: this.connection?.clientId,
      username: this.connection?.username,
      password: this.connection?.password,
      protocolId: 'MQTT'
    }
    console.log('Connecting mqtt client')
    // @ts-ignore
    const client = mqtt.connect(host, options)
    this.client.on('error', (err) => {
      console.log('Connection error: ', err)
      this.client.end()
    })
    this.client.on('reconnect', () => {
      console.log('Reconnecting...')
    });
    this.client.on('connect', (e) => {
      console.log(`Client connected: ${this.connection?.clientId}`, e)
      // Subscribe
      this.client.subscribe('testtopic/#', { qos: 0 }, (mess) => {
        this.displayData.push(mess)
        console.log('message', mess)
      })
    })
// Unsubscribe
    this.client.unsubscribe('testtopic', (e) => {
      console.log('Unsubscribed', e);
    })
    // Publish
    this.client.publish('testtopic/123', 'WS connection demo...!', { qos: 0, retain: false })
// Receive
    this.client.on('message', (topic, message, packet) => {
      console.log(`Received Message: ${message.toString()} On topic: ${topic}`)
    })
  }

}
