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

import { NgModule } from '@angular/core';
import { MenuLinkComponent } from '@modules/home/menu/menu-link.component';
import { MenuToggleComponent } from '@modules/home/menu/menu-toggle.component';
import { SideMenuComponent } from '@modules/home/menu/side-menu.component';
import { GettingStartedMenuLinkComponent } from '@home/pages/getting-started/getting-started-menu-link.component';

@NgModule({
  imports: [
    MenuLinkComponent,
    MenuToggleComponent,
    SideMenuComponent,
    GettingStartedMenuLinkComponent,
  ]
})
export class MenuModule { }
