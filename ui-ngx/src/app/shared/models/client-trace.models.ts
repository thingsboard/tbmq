/// Copyright © 2016-2026 The Thingsboard Authors

export type ClientTraceLevel = 'BASIC' | 'FULL';

export interface ClientTraceConfig {
  id?: string;
  clientId: string;
  expiresAt: string;
  level: ClientTraceLevel;
  createdTime?: number;
}

export interface ClientTraceEvent {
  event_id: string;
  trace_id: string;
  client_id: string;
  session_id: string;
  ts: string;
  direction: string;
  packet_type: string;
  topic: string;
  qos: number;
  packet_id: number;
  payload_size: number;
  remote_address: string;
  details: string;
  service_id: string;
}
