import { Component, OnInit } from '@angular/core';
import { MenuService } from "@core/services/menu.service";

@Component({
  selector: 'tb-quick-links',
  templateUrl: './quick-links.component.html',
  styleUrls: ['./quick-links.component.scss']
})
export class QuickLinksComponent implements OnInit {

  quickLinks$ = this.menuService.quickLinks();

  constructor(private menuService: MenuService) { }

  ngOnInit(): void {
  }

}
