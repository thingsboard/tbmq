import { ComponentFixture, TestBed } from '@angular/core/testing';

import { KafkaTableComponent } from './kafka-table.component';

describe('KafkaTableComponent', () => {
  let component: KafkaTableComponent;
  let fixture: ComponentFixture<KafkaTableComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ KafkaTableComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(KafkaTableComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
