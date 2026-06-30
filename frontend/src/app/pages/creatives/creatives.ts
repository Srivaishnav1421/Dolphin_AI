import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import {
  ContentFactoryContentType,
  ContentFactoryItem,
  ContentFactoryScoreBreakdown,
  ContentFactoryTone,
  ContentFactoryVariant,
} from '../../shared/models';
import { AppIcon } from '../../shared/ui';

@Component({
  selector: 'app-creatives',
  standalone: true,
  imports: [CommonModule, FormsModule, AppIcon],
  templateUrl: './creatives.html',
  styleUrl: './creatives.scss',
})
export class Creatives implements OnInit {
  items = signal<ContentFactoryItem[]>([]);
  selectedItem = signal<ContentFactoryItem | null>(null);
  loading = signal(true);
  generating = signal(false);
  submittingVariantId = signal<string | null>(null);
  error = signal<string | null>(null);
  message = signal<string | null>(null);

  businessName = '';
  productService = '';
  targetAudience = '';
  location = '';
  offer = '';
  tone: ContentFactoryTone = 'FRIENDLY';
  language = 'English';
  channel = 'Meta';
  goal = 'Generate qualified leads';
  ctaStyle = 'Book Now';
  contentType: ContentFactoryContentType = 'META_AD_COPY';

  toneOptions: ContentFactoryTone[] = ['FORMAL', 'CASUAL', 'BOLD', 'FRIENDLY'];
  contentTypeOptions: Array<{ value: ContentFactoryContentType; label: string; channel: string }> = [
    { value: 'META_AD_COPY', label: 'Meta ad copy', channel: 'Meta' },
    { value: 'INSTAGRAM_POST', label: 'Instagram post', channel: 'Instagram' },
    { value: 'WHATSAPP_MESSAGE', label: 'WhatsApp message', channel: 'WhatsApp' },
    { value: 'REEL_SCRIPT', label: 'Reel script', channel: 'Instagram Reels' },
    { value: 'LANDING_PAGE_HEADLINE', label: 'Landing page headline', channel: 'Landing page' },
  ];
  languageOptions = ['English', 'Hindi', 'Tamil', 'Telugu', 'Kannada', 'Marathi', 'Bengali'];
  goalOptions = ['Generate qualified leads', 'Book appointments', 'Sell products', 'Promote an event', 'Retarget warm audiences'];
  ctaOptions = ['Book a demo', 'Book Now', 'Learn More', 'Get Offer', 'Reply Now', 'Shop Now', 'Contact Us'];

  totalVariants = computed(() => this.items().reduce((sum, item) => sum + (item.variants?.length || 0), 0));
  pendingApprovalCount = computed(() => this.items()
    .flatMap(item => item.variants || [])
    .filter(variant => variant.approval_status === 'SUBMITTED_FOR_APPROVAL').length);
  approvedCount = computed(() => this.items()
    .flatMap(item => item.variants || [])
    .filter(variant => variant.approval_status === 'APPROVED').length);

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.loadItems();
  }

  loadItems() {
    this.loading.set(true);
    this.error.set(null);
    this.api.getContentFactoryItems().subscribe({
      next: items => {
        const list = items ?? [];
        this.items.set(list);
        const selectedId = this.selectedItem()?.id;
        this.selectedItem.set(list.find(item => item.id === selectedId) ?? list[0] ?? null);
        this.loading.set(false);
      },
      error: () => {
        this.items.set([]);
        this.selectedItem.set(null);
        this.loading.set(false);
        this.error.set('Content Factory could not be loaded from the backend.');
      },
    });
  }

  generate() {
    if (!this.canGenerate()) return;
    this.generating.set(true);
    this.error.set(null);
    this.message.set(null);

    this.api.generateContentFactory({
      business_name: this.businessName.trim(),
      product_service: this.productService.trim(),
      target_audience: this.targetAudience.trim(),
      location: this.location.trim(),
      offer: this.offer.trim(),
      tone: this.tone,
      language: this.language,
      channel: this.channel,
      goal: this.goal,
      cta_style: this.ctaStyle,
      content_type: this.contentType,
    }).subscribe({
      next: item => {
        this.generating.set(false);
        this.selectedItem.set(item);
        this.items.set([item, ...this.items().filter(existing => existing.id !== item.id)]);
        this.message.set(`Generated ${item.variants.length} ${item.generation_mode} variants.`);
      },
      error: err => {
        this.generating.set(false);
        const detail = err?.error?.error || err?.error?.message || 'Generation failed. No variants were created.';
        this.error.set(detail);
      },
    });
  }

  submitForApproval(variant: ContentFactoryVariant) {
    this.submittingVariantId.set(variant.id);
    this.error.set(null);
    this.message.set(null);

    this.api.submitContentFactoryVariantApproval(variant.id).subscribe({
      next: response => {
        this.submittingVariantId.set(null);
        const updated = response?.variant as ContentFactoryVariant | undefined;
        if (updated) {
          this.replaceVariant(updated);
        }
        this.message.set('Variant submitted to Approval Queue.');
      },
      error: err => {
        this.submittingVariantId.set(null);
        this.error.set(err?.error?.error || 'Could not submit this variant for approval.');
      },
    });
  }

  selectItem(item: ContentFactoryItem) {
    this.selectedItem.set(item);
  }

  setContentType(value: ContentFactoryContentType) {
    this.contentType = value;
    const option = this.contentTypeOptions.find(item => item.value === value);
    if (option) {
      this.channel = option.channel;
    }
  }

  canGenerate() {
    return !!this.businessName.trim()
      && !!this.productService.trim()
      && !!this.targetAudience.trim()
      && !this.generating();
  }

  breakdown(variant: ContentFactoryVariant): ContentFactoryScoreBreakdown | null {
    if (!variant.score_breakdown_json) return null;
    try {
      return JSON.parse(variant.score_breakdown_json) as ContentFactoryScoreBreakdown;
    } catch {
      return null;
    }
  }

  breakdownRows(variant: ContentFactoryVariant) {
    const breakdown = this.breakdown(variant);
    if (!breakdown) return [];
    return [
      { label: 'Length', value: breakdown.length_score, max: 15 },
      { label: 'Power words', value: breakdown.power_word_score, max: 30 },
      { label: 'Urgency', value: breakdown.urgency_score, max: 20 },
      { label: 'Emoji', value: breakdown.emoji_score, max: 10 },
      { label: 'Clarity', value: breakdown.clarity_score, max: 25 },
    ];
  }

  contentTypeLabel(value: ContentFactoryContentType | string) {
    return this.contentTypeOptions.find(item => item.value === value)?.label ?? value;
  }

  modeClass(mode: string) {
    return mode === 'AI_GENERATED' ? 'mode-ai' : 'mode-template';
  }

  statusClass(status: string) {
    return {
      DRAFT: 'status-draft',
      SUBMITTED_FOR_APPROVAL: 'status-pending',
      APPROVED: 'status-approved',
      REJECTED: 'status-rejected',
    }[status] ?? 'status-draft';
  }

  private replaceVariant(updated: ContentFactoryVariant) {
    const selected = this.selectedItem();
    if (!selected) return;
    const variants = selected.variants.map(variant => variant.id === updated.id ? updated : variant);
    const updatedItem = { ...selected, variants };
    this.selectedItem.set(updatedItem);
    this.items.set(this.items().map(item => item.id === updatedItem.id ? updatedItem : item));
  }
}
