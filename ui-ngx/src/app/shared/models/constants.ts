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

import { MatFormFieldDefaultOptions } from '@angular/material/form-field';

export const Constants = {
  serverErrorCode: {
    general: 2,
    authentication: 10,
    jwtTokenExpired: 11,
    tenantTrialExpired: 12,
    credentialsExpired: 15,
    permissionDenied: 20,
    invalidArguments: 30,
    badRequestParams: 31,
    itemNotFound: 32,
    tooManyRequests: 33,
    tooManyUpdates: 34
  },
  entryPoints: {
    login: '/api/auth/login',
    tokenRefresh: '/api/auth/token',
    nonTokenBased: '/api/noauth'
  }
};

export const MediaBreakpoints = {
  xs: 'screen and (max-width: 599px)',
  sm: 'screen and (min-width: 600px) and (max-width: 959px)',
  md: 'screen and (min-width: 960px) and (max-width: 1279px)',
  lg: 'screen and (min-width: 1280px) and (max-width: 1919px)',
  xl: 'screen and (min-width: 1920px) and (max-width: 5000px)',
  'lt-sm': 'screen and (max-width: 599px)',
  'lt-md': 'screen and (max-width: 959px)',
  'lt-lg': 'screen and (max-width: 1279px)',
  'lt-xl': 'screen and (max-width: 1919px)',
  'gt-xs': 'screen and (min-width: 600px)',
  'gt-sm': 'screen and (min-width: 960px)',
  'gt-md': 'screen and (min-width: 1280px)',
  'gt-lg': 'screen and (min-width: 1920px)',
  'gt-xxl': 'screen and (min-width: 2000px)',
  'gt-xl': 'screen and (min-width: 5001px)',
  'md-lg': 'screen and (min-width: 960px) and (max-width: 1819px)'
};

export const helpBaseUrl = 'https://thingsboard.io';

export const HelpLinks = {
  linksMap: {
    outgoingMailSettings: helpBaseUrl + '/docs/mqtt-broker/user-guide/ui/mail-server/',
    users: helpBaseUrl + '/docs/mqtt-broker/user-guide/ui/users/',
    clientCredentials: helpBaseUrl + '/docs/mqtt-broker/user-guide/ui/mqtt-client-credentials/',
    sessions: helpBaseUrl + '/docs/mqtt-broker/user-guide/ui/sessions',
    sharedSubscriptions: helpBaseUrl + '/docs/mqtt-broker/user-guide/ui/shared-subscriptions',
    connection: helpBaseUrl + '/docs/mqtt-broker/user-guide/ui/websocket-client',
    unauthorizedClient: helpBaseUrl + '/docs/mqtt-broker/user-guide/ui/unauthorized-client',
    integrationHttp: helpBaseUrl + '/docs/mqtt-broker/user-guide/integrations/http',
    integrationMqtt: helpBaseUrl + '/docs/mqtt-broker/user-guide/integrations/mqtt',
    integrationKafka: helpBaseUrl + '/docs/mqtt-broker/user-guide/integrations/kafka',
  }
};

export const customTranslationsPrefix = 'custom.';

export const NULL_UUID = '13814000-1dd2-11b2-8080-808080808080';

export const appearance: MatFormFieldDefaultOptions = {
  appearance: 'fill'
};

export interface ContentTypeData {
  name: string;
  code: string;
}

export enum ContentType {
  JSON = 'JSON',
  TEXT = 'TEXT',
  BINARY = 'BINARY'
}

export const contentTypesMap = new Map<ContentType, ContentTypeData>(
  [
    [
      ContentType.JSON,
      {
        name: 'content-type.json',
        code: 'json'
      }
    ],
    [
      ContentType.TEXT,
      {
        name: 'content-type.text',
        code: 'text'
      }
    ],
    [
      ContentType.BINARY,
      {
        name: 'content-type.binary',
        code: 'text'
      }
    ]
  ]
);

export interface ValueTypeData {
  name: string;
  icon: string;
}

export enum ValueType {
  STRING = 'STRING',
  JSON = 'JSON'
}

export const valueTypesMap = new Map<ValueType, ValueTypeData>(
  [
    [
      ValueType.STRING,
      {
        name: 'value.string',
        icon: 'mdi:format-text'
      }
    ],
    [
      ValueType.JSON,
      {
        name: 'value.json',
        icon: 'mdi:code-json'
      }
    ]
  ]
);
