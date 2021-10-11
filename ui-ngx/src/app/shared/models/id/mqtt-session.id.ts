import { EntityId } from '@shared/models/id/entity-id';
import { EntityType } from '@shared/models/entity-type.models';

export class SessionId implements EntityId {
  entityType = EntityType.MQTT_SESSION;
  id: string;
  constructor(id: string) {
    this.id = id;
  }
}
