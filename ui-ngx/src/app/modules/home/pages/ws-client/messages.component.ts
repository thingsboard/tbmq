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

import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { MqttQoS } from '@shared/models/session.model';
import { DialogService } from '@core/services/dialog.service';
import { AbstractControl, UntypedFormBuilder, UntypedFormGroup } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { WsClientService } from '@core/http/ws-client.service';
import { DateAgoPipe } from '@app/shared/pipe/date-ago.pipe';
import { map } from "rxjs/operators";
import { isLiteralObject } from '@core/utils';
import { UserProperties } from '@shared/models/retained-message.model';

export interface MessagesDisplayData {
  commentId?: string,
  displayName?: string,
  qos?: number,
  retain?: boolean,
  createdTime: string,
  createdDateAgo?: string,
  edit?: boolean,
  isEdited?: boolean,
  editedTime?: string;
  editedDateAgo?: string,
  showActions?: boolean,
  commentText?: any,
  isSystemComment?: boolean,
  avatarBgColor?: string
  type?: string;
  contentType?: string;
  userProperties?: UserProperties;
}

@Component({
  selector: 'tb-ws-client-messages',
  templateUrl: './messages.component.html',
  styleUrls: ['./messages.component.scss']
})
export class MessagesComponent implements OnInit {

  @ViewChild('eventContentEditor', {static: true})
  eventContentEditorElmRef: ElementRef;

  headerOptions = [
    {
      name: 'All',
      value: 'all'
    },
    {
      name: 'Received',
      value: 'received'
    },
    {
      name: 'Published',
      value: 'published'
    }
  ];

  selectedOption = 'all';

  editMode: boolean = false;

  messangerFormGroup: UntypedFormGroup;
  displayData: Array<MessagesDisplayData> = new Array<MessagesDisplayData>();

  connection;

  constructor(private dialogService: DialogService,
              private fb: UntypedFormBuilder,
              private wsClientService: WsClientService,
              public dateAgoPipe: DateAgoPipe,
              private translate: TranslateService) {
    this.wsClientService.selectedConnection$.subscribe(
      res => {
        this.connection = res;
        this.onChange('all');
      }
    )
    this.wsClientService.newMessage$.subscribe(
      res => {
        const commentText = res?.message;
        if (commentText) {
          this.connection = res?.connection;
          const message = {
            commentId: '12345',
            displayName: 'clientId1',
            createdTime: '0',
            createdDateAgo: this.dateAgoPipe.transform('1702979233'),
            edit: false,
            isEdited: false,
            editedTime: 'string',
            editedDateAgo: 'string',
            showActions: false,
            commentText: commentText,
            isSystemComment: false,
            avatarBgColor: 'red',
            type: res.type
          };
          console.log('new message', res);
          this.displayData.push(message);
        }
      }
    );
  }

  onChange(e) {
    this.wsClientService.getConnectionMessages(this.connection.id)
      .pipe(map(el => el.data))
      .subscribe(messages => {
        switch (e) {
          case 'all':
            this.displayData = messages;
            break;
          case 'published':
            this.displayData = messages.filter(el => el.type === 'pub');
            break;
          case 'received':
            this.displayData = messages.filter(el => el.type === 'sub');
            break;
        }
      }
    );
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
  }

  saveEditedComment(commentId: string): void {
  }

  private clearCommentEditInput(): void {
  }

  isPubMessage(displayDataElement) {
    return displayDataElement.type === 'pub';
  }

  onCommentMouseEnter(commentId: string, displayDataIndex: number): void {
    this.displayData[displayDataIndex].showActions = true;
  }

  onCommentMouseLeave(displayDataIndex: number): void {
    this.displayData[displayDataIndex].showActions = false;
  }

  resendMessage(commentId: string): void {
    const commentDisplayData = this.getDataElementByCommentId(commentId);
    commentDisplayData.edit = true;
    this.editMode = true;
    this.getMessageValueFormControl().disable({emitEvent: false});
  }

  cancelEdit(commentId: string): void {
    const commentDisplayData = this.getDataElementByCommentId(commentId);
    commentDisplayData.edit = false;
    this.editMode = false;
    this.getMessageValueFormControl().enable({emitEvent: false});
  }

  copyMessage(commentId: string): void {
  }

  getMessageValueFormControl(): AbstractControl {
    return this.messangerFormGroup.get('value');
  }

  private getDataElementByCommentId(commentId: string): MessagesDisplayData {
    return this.displayData.find(commentDisplayData => commentDisplayData.commentId === commentId);
  }

  isJson(str) {
    try {
      return isLiteralObject(JSON.parse(str));
    } catch (e) {
      return false;
    }
  }

  clearHistory() {

  }

}
