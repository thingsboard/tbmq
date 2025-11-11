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

export const Constants = {
  serverErrorCode: {
    general: 2,
    authentication: 10,
    jwtTokenExpired: 11,
    credentialsExpired: 15,
    permissionDenied: 20,
    invalidArguments: 30,
    badRequestParams: 31,
    itemNotFound: 32,
    tooManyRequests: 33,
    tooManyUpdates: 34,
    passwordViolation: 45
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
export const docsPath = '/docs/mqtt-broker';
export const HelpLinks = {
  linksMap: {
    outgoingMailSettings: helpBaseUrl + docsPath + '/user-guide/ui/mail-server',
    users: helpBaseUrl + docsPath + '/user-guide/ui/users',
    clientCredentials: helpBaseUrl + docsPath + '/user-guide/ui/mqtt-client-credentials',
    sessions: helpBaseUrl + docsPath + '/user-guide/ui/sessions',
    sharedSubscriptions: helpBaseUrl + docsPath + '/user-guide/ui/shared-subscriptions',
    connection: helpBaseUrl + docsPath + '/user-guide/ui/websocket-client',
    unauthorizedClient: helpBaseUrl + docsPath + '/user-guide/ui/unauthorized-clients',
    integrations: helpBaseUrl + docsPath + '/integrations',
    integrationHttp: helpBaseUrl + docsPath + '/integrations/http',
    integrationMqtt: helpBaseUrl + docsPath + '/integrations/mqtt',
    integrationKafka: helpBaseUrl + docsPath + '/integrations/kafka',
    mqttAuthSettings: helpBaseUrl + docsPath + '/security/authentication',
    oauth2Settings: helpBaseUrl + docsPath + '/security/oauth-2-support',
    oauth2Apple: 'https://developer.apple.com/sign-in-with-apple/get-started',
    oauth2Facebook: 'https://developers.facebook.com/docs/facebook-login/web#logindialog',
    oauth2Github: 'https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/creating-an-oauth-app',
    oauth2Google: 'https://developers.google.com/google-ads/api/docs/start',
    blockedClient: helpBaseUrl + docsPath + '/other/blocked-client',
    securitySettings: helpBaseUrl + docsPath + '/security/overview',
    providerBasic: helpBaseUrl + docsPath + '/security/authentication/basic',
    providerX509: helpBaseUrl + docsPath + '/security/authentication/x509',
    providerJwt: helpBaseUrl + docsPath + '/security/authentication/jwt',
    providerScram: helpBaseUrl + docsPath + '/security/authentication/scram',
    providerHttp: helpBaseUrl + docsPath + '/security/authentication/http',
    gettingStarted: helpBaseUrl + docsPath + '/getting-started',
    help: helpBaseUrl + docsPath + '/help',
    mqttOverWs: helpBaseUrl + docsPath + '/user-guide/mqtt-over-ws',
    retainedMessages: helpBaseUrl + docsPath + '/user-guide/retained-messages',
    lastWill: helpBaseUrl + docsPath + '/user-guide/last-will',
    keepAlive: helpBaseUrl + docsPath + '/user-guide/keep-alive',
    qos: helpBaseUrl + docsPath + '/user-guide/qos',
    topics: helpBaseUrl + docsPath + '/user-guide/topics',
    mqttProtocol: helpBaseUrl + docsPath + '/user-guide/mqtt-protocol',
    troubleshooting: helpBaseUrl + docsPath + '/troubleshooting',
    monitoring: helpBaseUrl + docsPath + '/user-guide/ui/monitoring',
    clientType: helpBaseUrl + docsPath + '/user-guide/mqtt-client-type',
    connectToThingsBoard: helpBaseUrl + docsPath + '/user-guide/integrations/how-to-connect-thingsboard-to-tbmq',
    perfTest100m: helpBaseUrl + docsPath + '/reference/100m-connections-performance-test',
    configuration: helpBaseUrl + docsPath + '/install/config',
    architecture: helpBaseUrl + docsPath + '/architecture',
    pricing: helpBaseUrl + '/pricing/?section=tbmq',
  }
};

export const customTranslationsPrefix = 'custom.';

export const NULL_UUID = '13814000-1dd2-11b2-8080-808080808080';

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
