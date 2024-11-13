///
/// Copyright © 2016-2024 The Thingsboard Authors
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

import * as AngularAnimations from '@angular/animations';
import * as AngularCore from '@angular/core';
import * as AngularCommon from '@angular/common';
import * as AngularForms from '@angular/forms';
import * as AngularFlexLayout from '@angular/flex-layout';
import * as AngularPlatformBrowser from '@angular/platform-browser';
import * as AngularRouter from '@angular/router';
import * as AngularCdkCoercion from '@angular/cdk/coercion';
import * as AngularCdkCollections from '@angular/cdk/collections';
import * as AngularCdkKeycodes from '@angular/cdk/keycodes';
import * as AngularCdkLayout from '@angular/cdk/layout';
import * as AngularCdkOverlay from '@angular/cdk/overlay';
import * as AngularCdkPortal from '@angular/cdk/portal';
import * as AngularMaterialAutocomplete from '@angular/material/autocomplete';
import * as AngularMaterialBadge from '@angular/material/badge';
import * as AngularMaterialBottomSheet from '@angular/material/bottom-sheet';
import * as AngularMaterialButton from '@angular/material/button';
import * as AngularMaterialButtonToggle from '@angular/material/button-toggle';
import * as AngularMaterialCard from '@angular/material/card';
import * as AngularMaterialCheckbox from '@angular/material/checkbox';
import * as AngularMaterialChips from '@angular/material/chips';
import * as AngularMaterialCore from '@angular/material/core';
import * as AngularMaterialDialog from '@angular/material/dialog';
import * as AngularMaterialDivider from '@angular/material/divider';
import * as AngularMaterialExpansion from '@angular/material/expansion';
import * as AngularMaterialFormField from '@angular/material/form-field';
import * as AngularMaterialGridList from '@angular/material/grid-list';
import * as AngularMaterialIcon from '@angular/material/icon';
import * as AngularMaterialInput from '@angular/material/input';
import * as AngularMaterialList from '@angular/material/list';
import * as AngularMaterialMenu from '@angular/material/menu';
import * as AngularMaterialPaginator from '@angular/material/paginator';
import * as AngularMaterialProgressBar from '@angular/material/progress-bar';
import * as AngularMaterialProgressSpinner from '@angular/material/progress-spinner';
import * as AngularMaterialRadio from '@angular/material/radio';
import * as AngularMaterialSelect from '@angular/material/select';
import * as AngularMaterialSidenav from '@angular/material/sidenav';
import * as AngularMaterialSlideToggle from '@angular/material/slide-toggle';
import * as AngularMaterialSlider from '@angular/material/slider';
import * as AngularMaterialSnackBar from '@angular/material/snack-bar';
import * as AngularMaterialSort from '@angular/material/sort';
import * as AngularMaterialStepper from '@angular/material/stepper';
import * as AngularMaterialTable from '@angular/material/table';
import * as AngularMaterialTabs from '@angular/material/tabs';
import * as AngularMaterialToolbar from '@angular/material/toolbar';
import * as AngularMaterialTooltip from '@angular/material/tooltip';
import * as AngularMaterialTree from '@angular/material/tree';
import * as NgrxStore from '@ngrx/store';
import * as RxJs from 'rxjs';
import * as RxJsOperators from 'rxjs/operators';
import * as TranslateCore from '@ngx-translate/core';
import * as TbCore from '@core/public-api';
import * as TbShared from '@shared/public-api';
import * as TbHomeComponents from '@home/components/public-api';
import _moment from 'moment';
import * as DragDropModule from "@angular/cdk/drag-drop";
import * as HttpClientModule from "@angular/common/http";

import { IModulesMap } from '@modules/common/modules-map.models';

declare const System;

class ModulesMap implements IModulesMap {

  private initialized = false;

  private modulesMap: {[key: string]: any} = {
    '@angular/animations': System.newModule(AngularAnimations),
    '@angular/core': System.newModule(AngularCore),
    '@angular/common': System.newModule(AngularCommon),
    '@angular/common/http': System.newModule(HttpClientModule),
    '@angular/forms': System.newModule(AngularForms),
    '@angular/flex-layout': System.newModule(AngularFlexLayout),
    '@angular/platform-browser': System.newModule(AngularPlatformBrowser),
    '@angular/router': System.newModule(AngularRouter),
    '@angular/cdk/coercion': System.newModule(AngularCdkCoercion),
    '@angular/cdk/collections': System.newModule(AngularCdkCollections),
    '@angular/cdk/keycodes': System.newModule(AngularCdkKeycodes),
    '@angular/cdk/layout': System.newModule(AngularCdkLayout),
    '@angular/cdk/overlay': System.newModule(AngularCdkOverlay),
    '@angular/cdk/portal': System.newModule(AngularCdkPortal),
    '@angular/cdk/drag-drop': System.newModule(DragDropModule),
    '@angular/material/autocomplete': System.newModule(AngularMaterialAutocomplete),
    '@angular/material/badge': System.newModule(AngularMaterialBadge),
    '@angular/material/bottom-sheet': System.newModule(AngularMaterialBottomSheet),
    '@angular/material/button': System.newModule(AngularMaterialButton),
    '@angular/material/button-toggle': System.newModule(AngularMaterialButtonToggle),
    '@angular/material/card': System.newModule(AngularMaterialCard),
    '@angular/material/checkbox': System.newModule(AngularMaterialCheckbox),
    '@angular/material/chips': System.newModule(AngularMaterialChips),
    '@angular/material/core': System.newModule(AngularMaterialCore),
    '@angular/material/dialog': System.newModule(AngularMaterialDialog),
    '@angular/material/divider': System.newModule(AngularMaterialDivider),
    '@angular/material/expansion': System.newModule(AngularMaterialExpansion),
    '@angular/material/form-field': System.newModule(AngularMaterialFormField),
    '@angular/material/grid-list': System.newModule(AngularMaterialGridList),
    '@angular/material/icon': System.newModule(AngularMaterialIcon),
    '@angular/material/input': System.newModule(AngularMaterialInput),
    '@angular/material/list': System.newModule(AngularMaterialList),
    '@angular/material/menu': System.newModule(AngularMaterialMenu),
    '@angular/material/paginator': System.newModule(AngularMaterialPaginator),
    '@angular/material/progress-bar': System.newModule(AngularMaterialProgressBar),
    '@angular/material/progress-spinner': System.newModule(AngularMaterialProgressSpinner),
    '@angular/material/radio': System.newModule(AngularMaterialRadio),
    '@angular/material/select': System.newModule(AngularMaterialSelect),
    '@angular/material/sidenav': System.newModule(AngularMaterialSidenav),
    '@angular/material/slide-toggle': System.newModule(AngularMaterialSlideToggle),
    '@angular/material/slider': System.newModule(AngularMaterialSlider),
    '@angular/material/snack-bar': System.newModule(AngularMaterialSnackBar),
    '@angular/material/sort': System.newModule(AngularMaterialSort),
    '@angular/material/stepper': System.newModule(AngularMaterialStepper),
    '@angular/material/table': System.newModule(AngularMaterialTable),
    '@angular/material/tabs': System.newModule(AngularMaterialTabs),
    '@angular/material/toolbar': System.newModule(AngularMaterialToolbar),
    '@angular/material/tooltip': System.newModule(AngularMaterialTooltip),
    '@angular/material/tree': System.newModule(AngularMaterialTree),
    '@ngrx/store': System.newModule(NgrxStore),
    rxjs: System.newModule(RxJs),
    'rxjs/operators': System.newModule(RxJsOperators),
    '@ngx-translate/core': System.newModule(TranslateCore),
    '@core/public-api': System.newModule(TbCore),
    '@shared/public-api': System.newModule(TbShared),
    '@home/components/public-api': System.newModule(TbHomeComponents),
    moment: System.newModule(_moment)
  };

  init() {
    if (!this.initialized) {
      System.constructor.prototype.resolve = (id) => {
        try {
          if (this.modulesMap[id]) {
            return 'app:' + id;
          } else {
            return id;
          }
        } catch (err) {
          return id;
        }
      };
      for (const moduleId of Object.keys(this.modulesMap)) {
        System.set('app:' + moduleId, this.modulesMap[moduleId]);
      }
      System.constructor.prototype.shouldFetch = (url: string) => url.endsWith('/download');
      System.constructor.prototype.fetch = (url, options: RequestInit & {meta?: any}) => {
        if (options?.meta?.additionalHeaders) {
          options.headers = { ...options.headers, ...options.meta.additionalHeaders };
        }
        return fetch(url, options);
      };
      this.initialized = true;
    }
  }
}

export const modulesMap = new ModulesMap();

// export const modulesMap: {[key: string]: any} = {
  // '@angular/animations': System.newModule(AngularAnimations),
  // '@angular/core': System.newModule(AngularCore),
  // '@angular/common': System.newModule(AngularCommon),
  // '@angular/common/http': System.newModule(HttpClientModule),
  // '@angular/forms': System.newModule(AngularForms),
  // '@angular/flex-layout': System.newModule(AngularFlexLayout),
  // '@angular/platform-browser': System.newModule(AngularPlatformBrowser),
  // '@angular/router': System.newModule(AngularRouter),
  // '@angular/cdk/coercion': System.newModule(AngularCdkCoercion),
  // '@angular/cdk/collections': System.newModule(AngularCdkCollections),
  // '@angular/cdk/keycodes': System.newModule(AngularCdkKeycodes),
  // '@angular/cdk/layout': System.newModule(AngularCdkLayout),
  // '@angular/cdk/overlay': System.newModule(AngularCdkOverlay),
  // '@angular/cdk/portal': System.newModule(AngularCdkPortal),
  // '@angular/cdk/drag-drop': System.newModule(DragDropModule),
  // '@angular/material/autocomplete': System.newModule(AngularMaterialAutocomplete),
  // '@angular/material/badge': System.newModule(AngularMaterialBadge),
  // '@angular/material/bottom-sheet': System.newModule(AngularMaterialBottomSheet),
  // '@angular/material/button': System.newModule(AngularMaterialButton),
  // '@angular/material/button-toggle': System.newModule(AngularMaterialButtonToggle),
  // '@angular/material/card': System.newModule(AngularMaterialCard),
  // '@angular/material/checkbox': System.newModule(AngularMaterialCheckbox),
  // '@angular/material/chips': System.newModule(AngularMaterialChips),
  // '@angular/material/core': System.newModule(AngularMaterialCore),
  // '@angular/material/dialog': System.newModule(AngularMaterialDialog),
  // '@angular/material/divider': System.newModule(AngularMaterialDivider),
  // '@angular/material/expansion': System.newModule(AngularMaterialExpansion),
  // '@angular/material/form-field': System.newModule(AngularMaterialFormField),
  // '@angular/material/grid-list': System.newModule(AngularMaterialGridList),
  // '@angular/material/icon': System.newModule(AngularMaterialIcon),
  // '@angular/material/input': System.newModule(AngularMaterialInput),
  // '@angular/material/list': System.newModule(AngularMaterialList),
  // '@angular/material/menu': System.newModule(AngularMaterialMenu),
  // '@angular/material/paginator': System.newModule(AngularMaterialPaginator),
  // '@angular/material/progress-bar': System.newModule(AngularMaterialProgressBar),
  // '@angular/material/progress-spinner': System.newModule(AngularMaterialProgressSpinner),
  // '@angular/material/radio': System.newModule(AngularMaterialRadio),
  // '@angular/material/select': System.newModule(AngularMaterialSelect),
  // '@angular/material/sidenav': System.newModule(AngularMaterialSidenav),
  // '@angular/material/slide-toggle': System.newModule(AngularMaterialSlideToggle),
  // '@angular/material/slider': System.newModule(AngularMaterialSlider),
  // '@angular/material/snack-bar': System.newModule(AngularMaterialSnackBar),
  // '@angular/material/sort': System.newModule(AngularMaterialSort),
  // '@angular/material/stepper': System.newModule(AngularMaterialStepper),
  // '@angular/material/table': System.newModule(AngularMaterialTable),
  // '@angular/material/tabs': System.newModule(AngularMaterialTabs),
  // '@angular/material/toolbar': System.newModule(AngularMaterialToolbar),
  // '@angular/material/tooltip': System.newModule(AngularMaterialTooltip),
  // '@angular/material/tree': System.newModule(AngularMaterialTree),
  // '@ngrx/store': System.newModule(NgrxStore),
  // rxjs: System.newModule(RxJs),
  // 'rxjs/operators': System.newModule(RxJsOperators),
  // '@ngx-translate/core': System.newModule(TranslateCore),
  // '@core/public-api': System.newModule(TbCore),
  // '@shared/public-api': System.newModule(TbShared),
  // '@home/components/public-api': System.newModule(TbHomeComponents),
  // moment: System.newModule(_moment)
// };
