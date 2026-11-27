/// Copyright © 2016-2026 The Thingsboard Authors

import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { ClientTraceService } from '@core/http/client-trace.service';
import { ClientTraceConfig, ClientTraceEvent } from '@shared/models/client-trace.models';
import { Subscription } from 'rxjs';

@Component({
  selector: 'tb-client-trace',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatSelectModule, MatTableModule],
  template: `
    <mat-card class="mat-padding">
      <mat-card-title>Client trace</mat-card-title>
      <form [formGroup]="form" (ngSubmit)="create()" class="trace-form">
        <mat-form-field><mat-label>Client ID</mat-label><input matInput formControlName="clientId"></mat-form-field>
        <mat-form-field><mat-label>Duration (minutes)</mat-label><input matInput type="number" formControlName="minutes"></mat-form-field>
        <mat-form-field><mat-label>Capture level</mat-label><mat-select formControlName="level">
          <mat-option value="BASIC">Basic</mat-option><mat-option value="FULL">Full metadata</mat-option>
        </mat-select></mat-form-field>
        <button mat-raised-button color="primary" [disabled]="form.invalid">Start trace</button>
      </form>
      <table mat-table [dataSource]="configs">
        <ng-container matColumnDef="clientId"><th mat-header-cell *matHeaderCellDef>Client ID</th><td mat-cell *matCellDef="let row">{{row.clientId}}</td></ng-container>
        <ng-container matColumnDef="level"><th mat-header-cell *matHeaderCellDef>Level</th><td mat-cell *matCellDef="let row">{{row.level}}</td></ng-container>
        <ng-container matColumnDef="expiresAt"><th mat-header-cell *matHeaderCellDef>Expires</th><td mat-cell *matCellDef="let row">{{row.expiresAt | date:'medium'}}</td></ng-container>
        <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th><td mat-cell *matCellDef="let row">
          <button mat-icon-button (click)="view(row.clientId)"><mat-icon>visibility</mat-icon></button>
          <button mat-icon-button (click)="remove(row)"><mat-icon>delete</mat-icon></button>
        </td></ng-container>
        <tr mat-header-row *matHeaderRowDef="configColumns"></tr><tr mat-row *matRowDef="let row; columns: configColumns"></tr>
      </table>
    </mat-card>
    <mat-card class="mat-padding events" *ngIf="selectedClientId">
      <mat-card-title>{{selectedClientId}} events</mat-card-title>
      <button mat-button (click)="loadEvents()"><mat-icon>refresh</mat-icon>Refresh</button>
      <table mat-table [dataSource]="events">
        <ng-container matColumnDef="ts"><th mat-header-cell *matHeaderCellDef>Time</th><td mat-cell *matCellDef="let row">{{row.ts | date:'medium'}}</td></ng-container>
        <ng-container matColumnDef="direction"><th mat-header-cell *matHeaderCellDef>Direction</th><td mat-cell *matCellDef="let row">{{row.direction}}</td></ng-container>
        <ng-container matColumnDef="packet"><th mat-header-cell *matHeaderCellDef>Packet</th><td mat-cell *matCellDef="let row">{{row.packet_type}}</td></ng-container>
        <ng-container matColumnDef="topic"><th mat-header-cell *matHeaderCellDef>Topic / details</th><td mat-cell *matCellDef="let row">{{row.topic || row.details}}</td></ng-container>
        <ng-container matColumnDef="size"><th mat-header-cell *matHeaderCellDef>Bytes</th><td mat-cell *matCellDef="let row">{{row.payload_size}}</td></ng-container>
        <tr mat-header-row *matHeaderRowDef="eventColumns"></tr><tr mat-row *matRowDef="let row; columns: eventColumns"></tr>
      </table>
    </mat-card>`,
  styles: [`.mat-padding{padding:24px}.trace-form{display:flex;gap:16px;align-items:center;flex-wrap:wrap;margin-top:20px}table{width:100%}.events{margin-top:16px}`]
})
export class ClientTraceComponent implements OnInit, OnDestroy {
  configs: ClientTraceConfig[] = [];
  events: ClientTraceEvent[] = [];
  selectedClientId?: string;
  configColumns = ['clientId', 'level', 'expiresAt', 'actions'];
  eventColumns = ['ts', 'direction', 'packet', 'topic', 'size'];
  private streamSubscription?: Subscription;
  form = this.fb.group({clientId: ['', Validators.required], minutes: [15, [Validators.required, Validators.min(1), Validators.max(1440)]], level: ['BASIC' as const, Validators.required]});

  constructor(private fb: FormBuilder, private traces: ClientTraceService) {}
  ngOnInit(): void { this.reload(); }
  ngOnDestroy(): void { this.streamSubscription?.unsubscribe(); }
  reload(): void { this.traces.getConfigs().subscribe(value => this.configs = value); }
  create(): void {
    const value = this.form.getRawValue();
    this.traces.save({clientId: value.clientId!, level: value.level!, expiresAt: new Date(Date.now() + value.minutes! * 60000).toISOString()})
      .subscribe(() => this.reload());
  }
  remove(config: ClientTraceConfig): void { if (config.id) this.traces.delete(config.id).subscribe(() => this.reload()); }
  view(clientId: string): void {
    this.selectedClientId = clientId;
    this.loadEvents();
    this.streamSubscription?.unsubscribe();
    this.streamSubscription = this.traces.stream(clientId).subscribe(event => {
      this.events = [event, ...this.events].slice(0, 1000);
    });
  }
  loadEvents(): void { if (this.selectedClientId) this.traces.getEvents(this.selectedClientId, Date.now() - 86400000, Date.now()).subscribe(value => this.events = value); }
}
