// ══════════════════════════════════════════════════════════════
//  Chubby Dolphin AI — Enterprise Data Models
// ══════════════════════════════════════════════════════════════

export interface User {
  id: string;
  email: string;
  name: string;
  role: string;
  account_id: string;
}

export interface AuthResponse {
  access_token:  string;
  refresh_token: string;
  token_type:    string;
  expires_in:    number;
  user:          User;
}

export interface Campaign {
  id:                string;
  name:              string;
  status:            'ACTIVE' | 'PAUSED' | 'COMPLETED';
  objective:         'LEADS' | 'CONVERSIONS' | 'AWARENESS' | 'TRAFFIC';
  budget:            number;
  spent:             number;
  ctr:               number;
  cpl:               number;
  roas:              number;
  performance_score: number;
  account_id:        string;
  meta_campaign_id?: string;
  created_at:        string;
  updated_at:        string;
}

export interface Lead {
  id:               string;
  name:             string;
  message:          string;
  score:            number;
  status:           'HOT' | 'WARM' | 'COLD' | 'UNQUALIFIABLE';
  budget_signal:    string | null;
  timeline_signal:  string | null;
  intent_signal:    string | null;
  location_signal:  string | null;
  source:           string;
  gemini_analysis?: string;
  created_at:       string;
}

export interface WalletStatus {
  id:                 string;
  account_id:         string;
  balance:            number;
  total_spent:        number;
  daily_budget_limit: number;
  updated_at:         string;
  total_budget?:      number;
  spent?:             number;
  remaining?:         number;
  remaining_pct?:     number;
  status?:            'HEALTHY' | 'WARNING' | 'CRITICAL' | 'DEPLETED';
  alert_threshold?:   number;
}

export interface EmasMetrics {
  blended_roas:    number;
  cac:             number;
  mer:             number;
  total_spend:     number;
  total_revenue:   number;
  total_customers: number;
}

export interface BrainEvent {
  id:         string;
  event_type: string;
  message:    string;
  severity:   'INFO' | 'WARNING' | 'CRITICAL' | 'SUCCESS';
  account_id: string;
  created_at: string;
}

// ── New Enterprise Models ────────────────────────────────────────

export interface MetaConnection {
  id:                       string;
  account_id:               string;
  meta_user_id:             string;
  meta_ad_account_id:       string;
  meta_page_id?:            string;
  meta_page_name?:          string;
  ad_account_name:          string;
  token_status:             'VALID' | 'EXPIRED' | 'REVOKED' | 'EXPIRING_SOON';
  auto_manage_enabled:      boolean;
  max_daily_spend:          number;
  pause_roas_threshold:     number;
  scale_up_roas_threshold:  number;
  max_budget_change_percent: number;
  currency:                 string;
  timezone:                 string;
  last_sync_at?:            string;
  created_at:               string;
}

export interface BrainDecision {
  id:                  string;
  account_id:          string;
  campaign_id?:        string;
  campaign_name?:      string;
  decision_type:       'PAUSE' | 'RESUME' | 'SCALE_UP' | 'SCALE_DOWN' | 'CONTINUE' | 'BUDGET_REALLOCATE' | string;
  action:              string;
  confidence:          number;
  status:              'PENDING_APPROVAL' | 'AUTO_EXECUTED' | 'APPROVED' | 'REJECTED' | 'BLOCKED_BY_SAFETY' | 'LOGGED_ONLY' | 'EXECUTED' | 'EXECUTED_LOCAL_ONLY' | 'ROLLED_BACK';
  llm_provider?:       string;
  reason?:             string;
  budget_before?:      number;
  budget_after?:       number;
  roas_at_decision?:   number;
  ctr_at_decision?:    number;
  approved_by?:        string;
  approved_at?:        string;
  executed_at?:        string;
  createdAt?:          string;
  created_at?:         string;
  riskScore?:          number;
  confidenceScore?:    number;
  triggerMetrics?:     string;
  thresholdBreached?:  string;
  roasAtDecision?:     number;
  spentAtDecision?:    number;
  spent_at_decision?:  number;
  campaignSnapshotJson?: string;
  campaign_snapshot_json?: string;
}

export interface AdCreative {
  id:                string;
  account_id:        string;
  campaign_id?:      string;
  headline?:         string;
  body?:             string;
  call_to_action:    string;
  platform:          string;
  status:            'DRAFT' | 'REVIEW' | 'APPROVED' | 'ACTIVE' | 'PAUSED' | 'ARCHIVED';
  generated_by:      'AI_GENERATED' | 'MANUAL' | 'AI_ASSISTED';
  predicted_ctr?:    number;
  actual_ctr?:       number;
  impressions:       number;
  clicks:            number;
  ab_test_group?:    string;
  ab_test_id?:       string;
  image_url?:        string;
  created_at:        string;
}

export interface LlmProviderStatus {
  ollama: {
    enabled:   boolean;
    available: boolean;
    model:     string;
  };
  gemini: {
    enabled: boolean;
    model:   string;
  };
  active_provider: string;
}

export interface DashboardSummary {
  total_spend:       number;
  total_revenue:     number;
  blended_roas:      number;
  active_campaigns:  number;
  total_campaigns:   number;
  hot_leads:         number;
  warm_leads:        number;
  cold_leads:        number;
  wallet_balance:    number;
  meta_connected:    boolean;
  pending_approvals: number;
  llm_status:        LlmProviderStatus;
  recent_events:     BrainEvent[];
  // computed helpers
  total_leads?:       number;
  avg_roas?:          number;
  avg_cpl?:           number;
  campaigns_at_risk?: number;
}

export interface ArbitrageResult {
  recommendation:     string;
  expected_roas_lift: number;
  reasoning:          string;
  actions:            Array<{ campaign: string; change: string }>;
  llm_provider?:      string;
}
