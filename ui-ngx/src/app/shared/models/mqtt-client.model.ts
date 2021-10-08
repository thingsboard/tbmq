import { EntityId } from '@shared/models/id/entity-id';
import { EntityType } from '@shared/models/entity-type.models';
import { BaseData } from '@shared/models/base-data';
import { ConnectionState, SessionInfo, TopicSubscription } from '@shared/models/mqtt-session.model';

export interface Client extends ClientInfo, DetailedClientSessionInfo, BaseData<ClientId> {
}

export interface ClientInfo {
  clientId: string;
  type: ClientType;
}

export interface DetailedClientSessionInfo extends BaseData<ClientId> {
  clientId: string;
  sessionId: string;
  connectionState: ConnectionState;
  clientType: ClientType;
  nodeId: string;
  persistent: boolean;
  subscriptions: TopicSubscription[];
  keepAliveSeconds: number;
  connectedAt: number;
  disconnectedAt: number;
}

export class ClientId implements EntityId {
  entityType = EntityType.MQTT_CLIENT;
  id: string;

  constructor(id: string) {
    this.id = id;
  }
}

export enum ClientType {
  DEVICE = 'DEVICE',
  APPLICATION = 'APPLICATION'
}

export const clientTypeTranslationMap = new Map<ClientType, string>(
  [
    [ClientType.DEVICE, 'mqtt-client.device'],
    [ClientType.APPLICATION, 'mqtt-client.application']
  ]
);

export interface ClientSession {
  connected: boolean;
  sessionInfo: SessionInfo;
}
