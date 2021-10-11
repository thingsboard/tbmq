import { EntityId } from '@shared/models/id/entity-id';
import { EntityType } from '@shared/models/entity-type.models';

export class ClientId implements EntityId {
  entityType = EntityType.MQTT_CLIENT_CREDENTIALS;
  id: string;
  constructor(id: string) {
    this.id = id;
  }
}
