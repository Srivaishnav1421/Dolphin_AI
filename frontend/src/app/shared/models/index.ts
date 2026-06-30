// ══════════════════════════════════════════════════════════════
//  Chubby Dolphin AI — Enterprise Data Models
// ══════════════════════════════════════════════════════════════

export interface User {
  id: string;
  email: string;
  name: string;
  role: string;
  account_id: string;
  organization_id?: string;
  two_factor_enabled?: boolean;
  workspaces?: WorkspaceOption[];
}

export interface WorkspaceOption {
  id: string;
  name: string;
  role?: string;
  active?: boolean;
  organization_id?: string;
  created_at?: string;
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
  target_cpl?:       number | null;
  spent:             number | null;
  ctr:               number | null;
  cpl:               number | null;
  roas:              number | null;
  performance_score: number | null;
  description?:      string | null;
  account_id:        string;
  meta_campaign_id?: string;
  created_at:        string;
  updated_at:        string;
}

export interface Lead {
  id:               string;
  name:             string;
  phone?:           string;
  email?:           string;
  message:          string;
  score:            number;
  status:           'NEW' | 'CONTACTED' | 'QUALIFIED' | 'WON' | 'LOST';
  temperature?:     'HOT' | 'WARM' | 'COLD' | 'UNKNOWN';
  pipeline_stage?:  'NEW_LEAD' | 'CONTACTED' | 'QUALIFIED' | 'INTERESTED' | 'PROPOSAL_SENT' | 'FOLLOW_UP' | 'NEGOTIATION' | 'CONVERTED' | 'LOST' | 'DORMANT' | 'RECYCLED';
  priority?:        'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
  budget_signal:    string | null;
  timeline_signal:  string | null;
  intent_signal:    string | null;
  location_signal:  string | null;
  source:           string;
  campaign_id?:     string;
  assigned_user_id?: string;
  tags?:            string;
  notes?:           string;
  budget?:          number;
  interest_category?: string;
  location?:        string;
  last_contacted_at?: string;
  next_follow_up_at?: string;
  conversion_probability?: number;
  expected_revenue?: number;
  lost_reason?:     string;
  ai_summary?:      string;
  next_best_action?: string;
  gemini_analysis?: string;
  score_breakdown_json?: string;
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

export interface AnalyticsSummary {
  workspace_id: string;
  generated_at: string;
  campaign_summary: {
    total: number;
    active: number;
    paused: number;
    completed: number;
    total_budget: number;
    total_spend: number;
    recorded_attributed_revenue: number;
    average_roas: number;
    average_cpl: number;
    source_table: string;
    empty: boolean;
  };
  lead_summary: {
    total: number;
    new_leads: number;
    hot: number;
    warm: number;
    cold: number;
    unknown_temperature: number;
    average_score: number;
    source_table: string;
    empty: boolean;
  };
  approval_summary: {
    total: number;
    pending: number;
    approved: number;
    rejected: number;
    requires_execution: number;
    source_table: string;
    empty: boolean;
  };
  content_factory_summary: {
    items: number;
    variants: number;
    draft_variants: number;
    submitted_variants: number;
    approved_variants: number;
    average_score: number;
    source_tables: string[];
    empty: boolean;
  };
  ad_brain_summary: {
    runs: number;
    latest_run_at?: string | null;
    campaigns_evaluated: number;
    evaluations_created: number;
    approvals_created: number;
    duplicate_approvals_skipped: number;
    risks_created: number;
    opportunities_created: number;
    source_table: string;
    empty: boolean;
  };
  risk_opportunity_summary: {
    math_evaluations: number;
    requires_approval: number;
    critical: number;
    high: number;
    not_enough_data: number;
    latest_evaluation_at?: string | null;
    source_table: string;
    empty: boolean;
  };
  empty_state: {
    is_empty: boolean;
    message: string;
  };
  read_only: boolean;
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

export interface ApprovalItem {
  id: string;
  organization_id?: string | null;
  workspace_id?: string | null;
  account_id?: string | null;
  source_module: 'AD_BRAIN' | 'CONTENT_FACTORY' | 'CAMPAIGN' | 'CREATIVE_STUDIO' | 'CRM' | 'AUTOMATION' | 'INTEGRATION' | 'SYSTEM' | string;
  source_entity_type?: string | null;
  source_entity_id?: string | null;
  action_type: 'PAUSE_CAMPAIGN' | 'RESUME_CAMPAIGN' | 'KILL_CAMPAIGN' | 'CHANGE_BUDGET' | 'CHANGE_OBJECTIVE' | 'APPROVE_CREATIVE' | 'PUBLISH_CREATIVE' | 'LAUNCH_CREATIVE' | 'SEND_WHATSAPP' | 'CALL_LEAD' | 'CHANGE_LEAD_STATUS' | 'EXECUTE_WORKFLOW' | 'OTHER' | string;
  title: string;
  description?: string | null;
  recommendation_json?: string | null;
  math_snapshot_json?: string | null;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED' | 'EXECUTED' | 'FAILED';
  requires_execution: boolean;
  execution_status?: string | null;
  execution_result_json?: string | null;
  rejection_reason?: string | null;
  created_at: string;
  updated_at: string;
  approved_at?: string | null;
  rejected_at?: string | null;
  executed_at?: string | null;
  expires_at?: string | null;
  execution_available: boolean;
}

export interface AdBrainRunResult {
  run_id: string;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED';
  campaigns_evaluated: number;
  evaluations_created: number;
  approval_items_created: number;
  duplicate_approvals_skipped: number;
  risks_created: number;
  opportunities_created: number;
  started_at: string;
  completed_at?: string | null;
  error_message?: string | null;
  message: string;
}

export interface AdBrainSignal {
  id: string;
  campaign_id?: string | null;
  evaluation_type: string;
  status: string;
  severity: 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  action_type: string;
  score?: number | null;
  title: string;
  description?: string | null;
  formula_version: string;
  requires_approval: boolean;
  created_at: string;
}

export interface CampaignMathEvaluation {
  id: string;
  organization_id?: string | null;
  workspace_id?: string | null;
  account_id?: string | null;
  campaign_id?: string | null;
  run_id?: string | null;
  evaluation_type: string;
  status: 'OK' | 'NOT_ENOUGH_DATA' | string;
  severity: 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  action_type: string;
  score?: number | null;
  title: string;
  description?: string | null;
  input_snapshot_json?: string | null;
  formula_version: string;
  requires_approval: boolean;
  created_at: string;
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

export type ContentFactoryGenerationMode = 'AI_GENERATED' | 'TEMPLATE_GENERATED';
export type ContentFactoryApprovalStatus = 'DRAFT' | 'SUBMITTED_FOR_APPROVAL' | 'APPROVED' | 'REJECTED';
export type ContentFactoryTone = 'FORMAL' | 'CASUAL' | 'BOLD' | 'FRIENDLY';
export type ContentFactoryContentType = 'META_AD_COPY' | 'INSTAGRAM_POST' | 'WHATSAPP_MESSAGE' | 'REEL_SCRIPT' | 'LANDING_PAGE_HEADLINE';

export interface ContentFactoryScoreBreakdown {
  length_score: number;
  power_word_score: number;
  urgency_score: number;
  emoji_score: number;
  clarity_score: number;
  score: number;
  formula_version: string;
}

export interface ContentFactoryVariant {
  id: string;
  item_id: string;
  variant_index: number;
  headline?: string | null;
  description?: string | null;
  cta?: string | null;
  content_text: string;
  generation_mode: ContentFactoryGenerationMode;
  score: number;
  score_breakdown_json: string;
  approval_status: ContentFactoryApprovalStatus;
  approval_item_id?: string | null;
  created_at: string;
  updated_at: string;
  approved_at?: string | null;
  rejected_at?: string | null;
}

export interface ContentFactoryItem {
  id: string;
  organization_id?: string | null;
  workspace_id: string;
  account_id: string;
  created_by?: string | null;
  content_type: ContentFactoryContentType;
  business_name: string;
  product_service: string;
  target_audience: string;
  location?: string | null;
  offer?: string | null;
  tone: ContentFactoryTone;
  language?: string | null;
  channel: string;
  goal?: string | null;
  cta_style?: string | null;
  generation_mode: ContentFactoryGenerationMode;
  input_request_json?: string | null;
  variants: ContentFactoryVariant[];
  created_at: string;
  updated_at: string;
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
  total_campaign_budget?: number;
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
  automation?: {
    active:              number;
    completed:           number;
    failed:              number;
    average_duration_ms: number;
  };
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

export interface MarketingForm {
  id: string;
  account_id?: string;
  workspaceId?: string;
  name: string;
  slug: string;
  industryType?: string;
  campaignId?: string;
  status: 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'ARCHIVED';
  fieldsJson?: string;
  settingsJson?: string;
  spamProtectionEnabled?: boolean;
  triggerAutomation?: boolean;
  submissionsCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface LandingPage {
  id: string;
  account_id?: string;
  workspaceId?: string;
  title: string;
  slug: string;
  industryType?: string;
  templateKey?: string;
  campaignId?: string;
  formId?: string;
  status: 'DRAFT' | 'PUBLISHED' | 'UNPUBLISHED' | 'ARCHIVED';
  sectionsJson?: string;
  seoJson?: string;
  customDomain?: string;
  publicPath?: string;
  visits?: number;
  submissions?: number;
  publishedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface FormSubmission {
  id: string;
  formId: string;
  landingPageId?: string;
  campaignId?: string;
  leadId?: string;
  source: string;
  status: 'ACCEPTED' | 'SPAM_REJECTED' | 'FAILED';
  payloadJson?: string;
  createdAt: string;
}
