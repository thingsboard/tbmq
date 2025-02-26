///
/// Copyright © 2016-2025 The Thingsboard Authors
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

import { NgModule, SecurityContext } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { HomeRoutingModule } from './home-routing.module';
import { MarkdownModule, MARKED_OPTIONS } from 'ngx-markdown';
import { MarkedOptionsService } from '@shared/components/marked-options.service';
import { MenuModule } from '@home/menu/menu.module';

@NgModule({
  providers: [
    DatePipe
  ],
  imports: [
    CommonModule,
    HomeRoutingModule,
    MenuModule,
    MarkdownModule.forRoot({
      sanitize: SecurityContext.NONE,
      markedOptions: {
        provide: MARKED_OPTIONS,
        useExisting: MarkedOptionsService
      }
    }),
  ]
})
export class HomeModule { }
