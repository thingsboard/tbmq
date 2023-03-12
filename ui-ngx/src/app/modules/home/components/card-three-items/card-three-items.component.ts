import { Component, Input, OnInit } from '@angular/core';
import { ThreeCardsData } from "@shared/models/stats.model";
import { PageComponent } from "@shared/components/page.component";
import { Store } from "@ngrx/store";
import { AppState } from "@core/core.state";

@Component({
  selector: 'tb-card-three-items',
  templateUrl: './card-three-items.component.html',
  styleUrls: ['./card-three-items.component.scss']
})
export class CardThreeItemsComponent extends PageComponent implements OnInit {

  @Input('data') data: ThreeCardsData;

  constructor(protected store: Store<AppState>) {
    super(store);
  }

  ngOnInit(): void {
  }

  navigateTo() {
    var root = this.data.link.href;

  }

  viewDocs() {
    var root = this.data.docs.href;
  }

}
