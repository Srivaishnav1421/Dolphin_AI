import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiService } from '../../core/services/api.service';
import { ContentFactoryItem } from '../../shared/models';
import { Creatives } from './creatives';

describe('Content Factory page', () => {
  let fixture: ComponentFixture<Creatives>;
  let component: Creatives;
  let apiService: any;

  const item: ContentFactoryItem = {
    id: 'item-1',
    workspace_id: 'workspace-1',
    account_id: 'workspace-1',
    content_type: 'META_AD_COPY',
    business_name: 'Dolphin Dental',
    product_service: 'Implant consultation',
    target_audience: 'Working professionals',
    location: 'Hyderabad',
    offer: 'Free scan today',
    tone: 'FRIENDLY',
    language: 'English',
    channel: 'Meta',
    goal: 'Generate qualified leads',
    cta_style: 'Book Now',
    generation_mode: 'TEMPLATE_GENERATED',
    variants: [
      {
        id: 'variant-1',
        item_id: 'item-1',
        variant_index: 1,
        headline: 'Limited Offer Today',
        description: 'Save now with trusted local support.',
        cta: 'Book Now',
        content_text: 'Limited Offer Today\nSave now with trusted local support.\nBook Now',
        generation_mode: 'TEMPLATE_GENERATED',
        score: 82,
        score_breakdown_json: JSON.stringify({
          length_score: 15,
          power_word_score: 18,
          urgency_score: 15,
          emoji_score: 6,
          clarity_score: 25,
          score: 82,
          formula_version: 'content-factory-score-v1',
        }),
        approval_status: 'DRAFT',
        created_at: '2026-06-19T10:00:00',
        updated_at: '2026-06-19T10:00:00',
      },
    ],
    created_at: '2026-06-19T10:00:00',
    updated_at: '2026-06-19T10:00:00',
  };

  beforeEach(async () => {
    apiService = {
      getContentFactoryItems: vi.fn().mockReturnValue(of([item])),
      generateContentFactory: vi.fn().mockReturnValue(of(item)),
      submitContentFactoryVariantApproval: vi.fn().mockReturnValue(of({
        variant: { ...item.variants[0], approval_status: 'SUBMITTED_FOR_APPROVAL', approval_item_id: 'approval-1' },
        approval: { id: 'approval-1' },
      })),
    };

    await TestBed.configureTestingModule({
      imports: [Creatives],
      providers: [
        { provide: ApiService, useValue: apiService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Creatives);
    component = fixture.componentInstance;
  });

  it('loads and renders variants from the real API response', () => {
    fixture.detectChanges();

    expect(apiService.getContentFactoryItems).toHaveBeenCalled();
    expect(component.selectedItem()?.id).toBe('item-1');
    expect(fixture.nativeElement.textContent).toContain('Limited Offer Today');
    expect(fixture.nativeElement.textContent).toContain('TEMPLATE_GENERATED');
  });

  it('form submits to the Content Factory generate API', () => {
    fixture.detectChanges();
    component.businessName = 'Dolphin Dental';
    component.productService = 'Implant consultation';
    component.targetAudience = 'Working professionals';

    component.generate();

    expect(apiService.generateContentFactory).toHaveBeenCalledWith(expect.objectContaining({
      business_name: 'Dolphin Dental',
      product_service: 'Implant consultation',
      target_audience: 'Working professionals',
      content_type: 'META_AD_COPY',
    }));
  });

  it('shows score breakdown from the API response', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Score breakdown');
    expect(fixture.nativeElement.textContent).toContain('Power words');
    expect(fixture.nativeElement.textContent).toContain('18/30');
  });

  it('API failure shows an error and does not render fake variants', () => {
    apiService.getContentFactoryItems.mockReturnValue(of([]));
    apiService.generateContentFactory.mockReturnValue(throwError(() => ({ error: { error: 'Provider down' } })));
    fixture.detectChanges();
    component.businessName = 'Dolphin Dental';
    component.productService = 'Implant consultation';
    component.targetAudience = 'Working professionals';

    component.generate();
    fixture.detectChanges();

    expect(component.error()).toBe('Provider down');
    expect(component.selectedItem()).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Fake Variant');
  });

  it('submit approval calls the real variant approval API', () => {
    fixture.detectChanges();

    component.submitForApproval(item.variants[0]);

    expect(apiService.submitContentFactoryVariantApproval).toHaveBeenCalledWith('variant-1');
    expect(component.selectedItem()?.variants[0].approval_status).toBe('SUBMITTED_FOR_APPROVAL');
  });
});
