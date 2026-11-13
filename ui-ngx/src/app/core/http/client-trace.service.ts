/// Copyright © 2016-2026 The Thingsboard Authors

import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from '@core/http/auth.service';
import { ClientTraceConfig, ClientTraceEvent } from '@shared/models/client-trace.models';

@Injectable({providedIn: 'root'})
export class ClientTraceService {
  constructor(private http: HttpClient) {}

  getConfigs(): Observable<ClientTraceConfig[]> {
    return this.http.get<ClientTraceConfig[]>('/api/client-traces');
  }

  save(config: ClientTraceConfig): Observable<ClientTraceConfig> {
    return this.http.post<ClientTraceConfig>('/api/client-traces', config);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/client-traces/${id}`);
  }

  getEvents(clientId: string, from: number, to: number): Observable<ClientTraceEvent[]> {
    const params = new HttpParams().set('from', from).set('to', to).set('limit', 1000);
    return this.http.get<ClientTraceEvent[]>(`/api/client-traces/${encodeURIComponent(clientId)}/events`, {params});
  }

  stream(clientId: string): Observable<ClientTraceEvent> {
    return new Observable(observer => {
      const controller = new AbortController();
      const read = async () => {
        try {
          const response = await fetch(`/api/client-traces/${encodeURIComponent(clientId)}/stream`, {
            headers: {Authorization: `Bearer ${AuthService.getJwtToken()}`},
            signal: controller.signal
          });
          if (!response.ok || !response.body) {
            throw new Error(`Trace stream returned ${response.status}`);
          }
          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          let buffer = '';
          while (!controller.signal.aborted) {
            const {done, value} = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, {stream: true});
            const frames = buffer.split(/\r?\n\r?\n/);
            buffer = frames.pop() || '';
            for (const frame of frames) {
              const data = frame.split(/\r?\n/)
                .filter(line => line.startsWith('data:'))
                .map(line => line.substring(5).trimStart()).join('\n');
              if (data) observer.next(JSON.parse(data) as ClientTraceEvent);
            }
          }
          if (!controller.signal.aborted) observer.complete();
        } catch (error) {
          if (!controller.signal.aborted) observer.error(error);
        }
      };
      void read();
      return () => controller.abort();
    });
  }
}
