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
import { MqttQoS } from '@shared/models/session.model';
import { DialogService } from '@core/services/dialog.service';
import { AbstractControl, UntypedFormBuilder, UntypedFormGroup } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { WsClientService } from '@core/http/ws-client.service';

export interface MessagesDisplayData {
  commentId?: string,
  displayName?: string,
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
}

@Component({
  selector: 'tb-ws-client-messages',
  templateUrl: './messages.component.html',
  styleUrls: ['./messages.component.scss']
})
export class MessagesComponent implements OnInit {

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

  constructor(private dialogService: DialogService,
              private fb: UntypedFormBuilder,
              private wsClientService: WsClientService,
              private translate: TranslateService) {
    this.wsClientService.getConnectionMessages().subscribe(
      res => {
        this.displayData = res.data;
      }
    )
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

}
