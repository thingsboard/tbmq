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

export declare type MenuSectionType = 'link' | 'toggle';

export interface MenuSection {
  id: string;
  name: string;
  type: MenuSectionType;
  path: string;
  icon: string;
  notExact?: boolean;
  height?: string;
  pages?: Array<MenuSection>;
  isNew?: boolean;
  opened?: boolean;
}

export enum MenuId {
  home = 'home',
  sessions = 'sessions',
  subscriptions = 'subscriptions',
  client_credentials = 'client_credentials',
  unauthorized_clients = 'unauthorized_clients',
  web_socket_client = 'web_socket_client',
  retained_messages = 'retained_messages',
  shared_subscriptions_management = 'shared_subscriptions_management',
  shared_subscriptions = 'shared_subscriptions',
  shared_subscriptions_application = 'shared_subscriptions_application',
  kafka_management = 'kafka_management',
  kafka_topics = 'kafka_topics',
  kafka_consumer_groups = 'kafka_consumer_groups',
  kafka_brokers = 'kafka_brokers',
  monitoring = 'monitoring',
  monitoring_broker_state_health = 'monitoring_broker_state_health',
  monitoring_broker_traffic_performance = 'monitoring_broker_traffic_performance',
  monitoring_resource_usage = 'monitoring_resource_usage',
  integrations = 'integrations',
  users = 'users',
  mail_server = 'mail_server',
  system_settings = 'system_settings',
  system_settings_general = 'system_settings_general',
  system_settings_security = 'system_settings_security',
  mqtt_auth_provider = 'mqtt_auth_provider',
  authentication = 'authentication',
  blocked_clients = 'blocked_clients',
}
