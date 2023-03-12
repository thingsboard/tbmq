import { Component, OnInit } from '@angular/core';
import { Observable, of } from "rxjs";

export interface VersionData {
  currentVersion: string;
  latestVersion: string;
  updateAvailable: boolean;
}

@Component({
  selector: 'tb-version-card',
  templateUrl: './version-card.component.html',
  styleUrls: ['./version-card.component.scss']
})
export class VersionCardComponent implements OnInit {

  versionData: VersionData;

  constructor() { }

  ngOnInit(): void {
    this.getData().subscribe(versionData => this.versionData = versionData);
  }

  getData(): Observable<VersionData> {
    return of({
      currentVersion: '1.0',
      latestVersion: '1.1',
      updateAvailable: true
    });
  }

}
