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



import { enableProdMode, importProvidersFrom } from '@angular/core';
import { BrowserModule, bootstrapApplication } from '@angular/platform-browser';
import { provideAnimations } from '@angular/platform-browser/animations';
import { AppRoutingModule } from '@app/app-routing.module';
import { CoreModule } from '@core/core.module';
import { LoginModule } from '@modules/login/login.module';
import { HomeModule } from '@home/home.module';
import { PageNotFoundRoutingModule } from '@app/app.module';
import { AppComponent } from '@app/app.component';
import { environment } from '@env/environment';
import $ from 'jquery';

(window as any).jQuery = $;
(window as any).$ = $;

if (environment.production) {
  enableProdMode();
}

bootstrapApplication(AppComponent, {
    providers: [
        importProvidersFrom(BrowserModule, AppRoutingModule, CoreModule, LoginModule, HomeModule, PageNotFoundRoutingModule),
        provideAnimations()
    ]
}).catch(err => console.error(err));
