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

import { BaseData } from '@shared/models/base-data';
import { UserProperties } from '@home/components/client-credentials-templates/user-properties.component';

export interface Connection extends BaseData {
  name: string;
  connected?: boolean;
}

export interface ConnectionDetailed extends Connection {
  uri: string;
  host: string;
  port: number;
  path: string;
  clientId: string;
  username: string;
  password: string;
  keepAlive: number;
  reconnectPeriod: number;
  connectTimeout: number;
  clean: boolean;
  protocolVersion: string;
  properties: ConnectionProperties;
  userProperties: UserProperties;
  will: ConnectionWill;
  subscriptions?: any[];
}

export interface ConnectionProperties {
  sessionExpiryInterval: number;
  receiveMaximum: number;
  maximumPacketSize: number;
  topicAliasMaximum: number;
  requestResponseInformation: boolean;
  requestProblemInformation: boolean;
}

export interface ConnectionWill {

}
