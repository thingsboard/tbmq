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

import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HTTP_INTERCEPTORS, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { StoreModule } from '@ngrx/store';
import { EffectsModule } from '@ngrx/effects';
import { StoreDevtoolsModule } from '@ngrx/store-devtools';
import { GlobalHttpInterceptor } from './interceptors/global-http-interceptor';
import { effects, metaReducers, reducers } from './core.state';
import { environment as env } from '@env/environment';
import {
  MissingTranslationHandler,
  TranslateCompiler,
  TranslateLoader,
  TranslateModule,
  TranslateParser
} from '@ngx-translate/core';
import { TbMissingTranslationHandler } from './translate/missing-translate-handler';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { TranslateDefaultCompiler } from '@core/translate/translate-default-compiler';
import { WINDOW_PROVIDERS } from '@core/services/window.service';
import { TranslateDefaultParser } from '@core/translate/translate-default-parser';
import { TranslateDefaultLoader } from '@core/translate/translate-default-loader';

@NgModule({
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatSnackBarModule,
    // ngx-translate
    TranslateModule.forRoot({
      loader: {
        provide: TranslateLoader,
        useClass: TranslateDefaultLoader
      },
      missingTranslationHandler: {
        provide: MissingTranslationHandler,
        useClass: TbMissingTranslationHandler
      },
      compiler: {
        provide: TranslateCompiler,
        useClass: TranslateDefaultCompiler
      },
      parser: {
        provide: TranslateParser,
        useClass: TranslateDefaultParser
      }
    }),

    // ngrx
    StoreModule.forRoot(reducers,
      { metaReducers,
        runtimeChecks: {
          strictStateImmutability: true,
          strictActionImmutability: true,
          strictStateSerializability: true,
          strictActionSerializability: true
        }}
    ),
    EffectsModule.forRoot(effects),
    env.production
      ? []
      : StoreDevtoolsModule.instrument({
        name: env.appTitle,
        connectInZone: true
      })
  ],
  providers: [
    {
      provide: HTTP_INTERCEPTORS,
      useClass: GlobalHttpInterceptor,
      multi: true
    },
    WINDOW_PROVIDERS,
    provideHttpClient(withInterceptorsFromDi()),
  ],
  exports: []
})
export class CoreModule {
}
