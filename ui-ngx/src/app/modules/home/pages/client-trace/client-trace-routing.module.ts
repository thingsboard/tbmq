/// Copyright © 2016-2026 The Thingsboard Authors

import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { Authority } from '@shared/models/authority.enum';

const routes: Routes = [{
  path: 'client-traces',
  loadComponent: () => import('./client-trace.component').then(m => m.ClientTraceComponent),
  data: {auth: [Authority.SYS_ADMIN], title: 'Client trace', breadcrumb: {label: 'Client trace', icon: 'timeline'}}
}];

@NgModule({imports: [RouterModule.forChild(routes)], exports: [RouterModule]})
export class ClientTraceRoutingModule {}
