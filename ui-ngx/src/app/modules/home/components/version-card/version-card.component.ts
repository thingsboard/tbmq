import { Component, Input, OnInit } from '@angular/core';
import { Observable, of } from "rxjs";
import { Router } from "@angular/router";

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

  @Input() isLoading$: Observable<boolean>

  versionData: VersionData;
  updateAvailable$: Observable<boolean> = of(false);

  constructor(private router: Router) { }

  ngOnInit(): void {
    this.getData().subscribe(versionData => this.versionData = versionData);
  }

  getData(): Observable<VersionData> {
    return of({
      currentVersion: '1.0',
      latestVersion: '1.1',
      updateAvailable: false
    });
  }

  viewDocumentation(type) {
    this.router.navigateByUrl('');
  }

  navigateToPage(type) {
    this.router.navigateByUrl('');
  }

}
