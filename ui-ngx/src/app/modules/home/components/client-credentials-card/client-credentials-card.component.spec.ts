import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClientCredentialsCardComponent } from './client-credentials-card.component';

describe('ClientCredentialsCardComponent', () => {
  let component: ClientCredentialsCardComponent;
  let fixture: ComponentFixture<ClientCredentialsCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ClientCredentialsCardComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ClientCredentialsCardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
