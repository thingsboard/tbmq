import { BaseData } from '@shared/models/base-data';
import { DetailedClientSessionInfo } from '@shared/models/mqtt-session.model';
import { ClientId } from '@shared/models/id/mqtt-client.id';

export interface Client extends ClientInfo, DetailedClientSessionInfo, BaseData<ClientId> {
}

export interface ClientInfo {
  clientId: string;
  type: ClientType;
}

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
