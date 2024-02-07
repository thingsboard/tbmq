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

export enum ClientType {
  DEVICE = 'DEVICE',
  APPLICATION = 'APPLICATION'
}

export const clientTypeTranslationMap = new Map<ClientType, string>(
  [
    [ClientType.DEVICE, 'mqtt-client.type-device'],
    [ClientType.APPLICATION, 'mqtt-client.type-application']
  ]
);

export const clientTypeColor = new Map<ClientType, string>(
  [
    [ClientType.APPLICATION, '#f4ebf2'],
    [ClientType.DEVICE, '#ebeef4']
  ]
);

export const clientTypeValueColor = new Map<ClientType, string>(
  [
    [ClientType.APPLICATION, '#a75b96'],
    [ClientType.DEVICE, '#002a6e']
  ]
);

export const clientTypeIcon = new Map<ClientType, string>(
  [
    [ClientType.APPLICATION, 'desktop_mac'],
    [ClientType.DEVICE, 'devices_other']
  ]
);
