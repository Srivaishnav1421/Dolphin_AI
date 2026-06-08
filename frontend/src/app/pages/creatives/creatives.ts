import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AdCreative } from '../../shared/models';

@Component({
  selector: 'app-creatives',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './creatives.html',
  styleUrl: './creatives.scss',
})
export class Creatives implements OnInit {
  creatives     = signal<AdCreative[]>([]);
  loading       = signal(true);
  generating    = signal(false);
  launchMessage = signal('');

  // Selected Generator Tab
  selectedTab = signal<string>('AD_COPY'); // AD_COPY, SOCIAL, WHATSAPP, BRIEF, COMPETITOR, TEMPLATES

  // Form Fields: Ad Copy
  product   = '';
  audience  = '';
  tone      = 'professional';
  platform  = 'FACEBOOK_FEED';

  // Form Fields: Social Media
  socialProduct   = '';
  socialAudience  = '';
  socialTone      = 'professional';
  socialPlatform  = 'LINKEDIN';

  // Form Fields: WhatsApp Campaign
  whatsappProduct  = '';
  whatsappAudience = '';
  whatsappOffer    = '';
  whatsappCta      = '';

  // Form Fields: Creative Brief
  briefProduct  = '';
  briefIdentity = '';
  briefMessage  = '';
  briefVibe     = 'Minimal';

  // Form Fields: Competitor
  competitorUrl = '';
  competitorInsights = signal<any[]>([]);

  // Selection Options
  toneOptions     = ['professional', 'casual', 'urgent', 'emotional', 'humorous', 'luxury'];
  platformOptions = ['FACEBOOK_FEED', 'INSTAGRAM_FEED', 'INSTAGRAM_STORY', 'REELS'];
  socialPlatforms = ['LINKEDIN', 'X_TWITTER', 'INSTAGRAM_POST'];
  vibes           = ['Minimal', 'Bold & Tech', 'Vibrant', 'Luxury & Premium'];

  // Saved templates list
  templates = [
    {
      title: '🎁 Black Friday Discount Promo',
      description: 'High-intent holiday lead-generation template focusing on limited-time discounts.',
      product: 'Premium organic tea gift set — 30% off retail pricing',
      audience: 'Gourmet organic shoppers, eco-conscious gift seekers, ages 25-50',
      tone: 'urgent',
      platform: 'INSTAGRAM_STORY'
    },
    {
      title: '📖 Free Ebook Lead Magnet',
      description: 'High-converting SaaS template to drive email sign-ups using a free resource.',
      product: 'Free PDF Ebook: "Top 10 Growth Hacks to Double B2B Lead Conversion Rates"',
      audience: 'Small business owners, CMOs, digital marketers, agency directors',
      tone: 'professional',
      platform: 'FACEBOOK_FEED'
    },
    {
      title: '☕ Local Coffee shop Launch',
      description: 'Geo-targeted template for retail customer acquisition and grand openings.',
      product: 'Get a free premium croissant with your first latte purchase',
      audience: 'Coffee enthusiasts, college students, freelancers within 5km radius',
      tone: 'casual',
      platform: 'REELS'
    }
  ];

  constructor(private api: ApiService) {}

  ngOnInit() { this.load(); }

  load() {
    this.loading.set(true);
    this.api.getCreatives().subscribe({
      next: c => { this.creatives.set(c); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  setTab(tab: string) {
    this.selectedTab.set(tab);
    if (tab === 'COMPETITOR') {
      this.loadCompetitorInsights();
    }
  }

  loadCompetitorInsights() {
    this.api.getCompetitorInsights().subscribe({
      next: res => this.competitorInsights.set(res),
      error: () => {}
    });
  }

  applyTemplate(tpl: any) {
    this.product = tpl.product;
    this.audience = tpl.audience;
    this.tone = tpl.tone;
    this.platform = tpl.platform;
    this.selectedTab.set('AD_COPY');
    this.launchMessage.set('✅ Template presets applied to Ad Copy Generator!');
    setTimeout(() => this.launchMessage.set(''), 3000);
  }

  generateAdCopy() {
    if (!this.product.trim()) return;
    this.generating.set(true);
    this.api.generateCreative({
      product:  this.product,
      audience: this.audience,
      tone:     this.tone,
      platform: this.platform,
    }).subscribe({
      next: () => {
        this.generating.set(false);
        this.product = ''; this.audience = '';
        this.load();
        this.launchMessage.set('✨ AI Ad Copy variations generated successfully!');
        setTimeout(() => this.launchMessage.set(''), 4000);
      },
      error: () => this.generating.set(false),
    });
  }

  generateSocial() {
    if (!this.socialProduct.trim()) return;
    this.generating.set(true);
    const mockProduct = `[SOCIAL POST FOR ${this.socialPlatform}] Brand/Product: ${this.socialProduct}`;
    this.api.generateCreative({
      product: mockProduct,
      audience: this.socialAudience,
      tone: this.socialTone,
      platform: 'INSTAGRAM_FEED',
    }).subscribe({
      next: () => {
        this.generating.set(false);
        this.socialProduct = ''; this.socialAudience = '';
        this.load();
        this.launchMessage.set('✨ Social media copy generated and stored!');
        setTimeout(() => this.launchMessage.set(''), 4000);
      },
      error: () => this.generating.set(false),
    });
  }

  generateWhatsApp() {
    if (!this.whatsappProduct.trim()) return;
    this.generating.set(true);
    const mockProduct = `[WHATSAPP SDR CAMPAIGN] Product/Offer: ${this.whatsappProduct} | Special Offer: ${this.whatsappOffer} | CTA: ${this.whatsappCta}`;
    this.api.generateCreative({
      product: mockProduct,
      audience: this.whatsappAudience,
      tone: 'casual',
      platform: 'INSTAGRAM_STORY',
    }).subscribe({
      next: () => {
        this.generating.set(false);
        this.whatsappProduct = ''; this.whatsappOffer = ''; this.whatsappCta = '';
        this.load();
        this.launchMessage.set('✨ WhatsApp campaign copy generated and stored!');
        setTimeout(() => this.launchMessage.set(''), 4000);
      },
      error: () => this.generating.set(false),
    });
  }

  generateBrief() {
    if (!this.briefProduct.trim()) return;
    this.generating.set(true);
    const mockProduct = `[CREATIVE BRIEF] Product: ${this.briefProduct} | Brand Identity Guidelines: ${this.briefIdentity} | Core Campaign Message: ${this.briefMessage} | Aesthetic Vibe: ${this.briefVibe}`;
    this.api.generateCreative({
      product: mockProduct,
      audience: 'Creative Marketing Division',
      tone: 'professional',
      platform: 'FACEBOOK_FEED',
    }).subscribe({
      next: () => {
        this.generating.set(false);
        this.briefProduct = ''; this.briefIdentity = ''; this.briefMessage = '';
        this.load();
        this.launchMessage.set('✨ Campaign Creative Brief formulated successfully!');
        setTimeout(() => this.launchMessage.set(''), 4000);
      },
      error: () => this.generating.set(false),
    });
  }

  analyzeCompetitor() {
    if (!this.competitorUrl.trim()) return;
    this.generating.set(true);
    this.api.analyzeCompetitor(this.competitorUrl).subscribe({
      next: () => {
        this.generating.set(false);
        this.competitorUrl = '';
        this.loadCompetitorInsights();
        this.launchMessage.set('✅ Competitor analysis audited successfully!');
        setTimeout(() => this.launchMessage.set(''), 4000);
      },
      error: () => {
        this.generating.set(false);
        this.launchMessage.set('⚠️ Competitor analysis failed. Using sandbox insights.');
        setTimeout(() => this.launchMessage.set(''), 4000);
      }
    });
  }

  updateStatus(id: string, status: string) {
    this.api.updateCreativeStatus(id, status).subscribe(() => this.load());
  }

  rewrite(id: string, platform: string) {
    this.api.rewriteCreative(id, platform).subscribe(() => this.load());
  }

  launchCreative(c: AdCreative, platform: string) {
    this.api.launchCreativeToPlatform(c.id, platform, 5000).subscribe({
      next: (res) => {
        this.launchMessage.set(`🚀 Ad creative launched successfully to ${platform}!`);
        setTimeout(() => this.launchMessage.set(''), 5000);
        this.load();
      },
      error: () => {
        this.launchMessage.set(`❌ Failed to deploy to ${platform}.`);
        setTimeout(() => this.launchMessage.set(''), 5000);
      }
    });
  }

  statusClass(s: string) {
    return {
      DRAFT: 'info', REVIEW: 'warm', APPROVED: 'active',
      ACTIVE: 'active', PAUSED: 'paused', ARCHIVED: 'cold',
    }[s] ?? 'info';
  }

  platformLabel(p: string) {
    return {
      FACEBOOK_FEED: 'FB Feed', INSTAGRAM_FEED: 'IG Feed',
      INSTAGRAM_STORY: 'IG Story', REELS: 'Reels',
    }[p] ?? p;
  }
}
