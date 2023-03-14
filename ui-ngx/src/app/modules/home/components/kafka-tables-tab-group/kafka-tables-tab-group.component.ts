import { Component, OnInit } from '@angular/core';
import { TranslateService } from "@ngx-translate/core";

@Component({
  selector: 'tb-kafka-tables-tab-group',
  templateUrl: './kafka-tables-tab-group.component.html',
  styleUrls: ['./kafka-tables-tab-group.component.scss']
})
export class KafkaTablesTabGroupComponent implements OnInit {

  constructor(private translate: TranslateService) { }

  ngOnInit(): void {
  }

  label(tab: 'topics' | 'consumerGroups') {
    switch (tab) {
      case "topics":
        return this.translate.instant('kafka.topics')
      case "consumerGroups":
        return this.translate.instant('kafka.consumer-groups')
    }
  }

}
