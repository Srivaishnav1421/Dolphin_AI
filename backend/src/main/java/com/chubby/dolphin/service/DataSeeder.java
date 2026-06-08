package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.*;
import com.chubby.dolphin.repository.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@Slf4j
public class DataSeeder {

    private final UserRepository   userRepo;
    private final WalletRepository walletRepo;
    private final PasswordEncoder  encoder;
    private final OrganizationRepository orgRepo;
    private final WorkspaceRepository    workspaceRepo;
    private final CampaignRepository     campaignRepo;
    private final MetricSnapshotRepository snapshotRepo;
    private final WorkflowTemplateRepository templateRepo;

    public DataSeeder(UserRepository userRepo,
                      WalletRepository walletRepo,
                      PasswordEncoder encoder,
                      OrganizationRepository orgRepo,
                      WorkspaceRepository workspaceRepo,
                      CampaignRepository campaignRepo,
                      MetricSnapshotRepository snapshotRepo,
                      WorkflowTemplateRepository templateRepo) {
        this.userRepo = userRepo;
        this.walletRepo = walletRepo;
        this.encoder = encoder;
        this.orgRepo = orgRepo;
        this.workspaceRepo = workspaceRepo;
        this.campaignRepo = campaignRepo;
        this.snapshotRepo = snapshotRepo;
        this.templateRepo = templateRepo;
    }

    @Value("${demo.email}")    private String demoEmail;
    @Value("${demo.password}") private String demoPassword;

    /**
     * Seeds initial records on startup:
     * 1. Parent Organization
     * 2. Multiple Isolated Client Workspaces
     * 3. Admin user linked to organization & default active workspace
     * 4. Multi-tenant mock campaigns and daily metrics snapshots
     */
    @PostConstruct
    public void seed() {
        seedAdminUser();
        seedTemplates();
        log.info("🐬 DolphinAI — Seed completed | Login: {} / {}", demoEmail, demoPassword);
    }

    private void seedTemplates() {
        if (templateRepo.count() > 0) {
            log.info("✅ Workflow templates already seeded");
            return;
        }

        templateRepo.save(new WorkflowTemplate("tmpl-support", "Customer Support Workflow", "Automates initial support request routing, drafts automated responses, and escalates to human agents if sentiment is negative.", "Support", "{}"));
        templateRepo.save(new WorkflowTemplate("tmpl-research", "Research Workflow", "Extracts campaign insights, fetches competitor ad metrics, and generates a comparative marketing report.", "Research", "{}"));
        templateRepo.save(new WorkflowTemplate("tmpl-content", "Content Generation Workflow", "Produces high-quality ad creative headlines and body text using the LLM router, tailored to custom target audiences.", "Content", "{}"));
        templateRepo.save(new WorkflowTemplate("tmpl-lead", "Lead Qualification Workflow", "Scores new inbound leads, parses contact info/intent signals, and routes high-intent opportunities directly to Slack.", "Marketing", "{}"));
        templateRepo.save(new WorkflowTemplate("tmpl-meeting", "Meeting Assistant Workflow", "Transcribes user-provided audio notes or meeting outlines, parses actionable tasks, and schedules calendar events.", "Operations", "{}"));
        templateRepo.save(new WorkflowTemplate("tmpl-operations", "Internal Operations Workflow", "Monitors workflow run health, checks rate limits/budgets, and logs system warnings to database and admin emails.", "Operations", "{}"));

        log.info("✅ 6 initial SaaS workflow templates successfully seeded.");
    }

    private void seedAdminUser() {
        if (userRepo.existsByEmail(demoEmail)) {
            log.info("✅ Admin user already exists: {}", demoEmail);
            return;
        }

        // 1. Create Parent Agency Organization
        Organization agencyOrg = new Organization();
        agencyOrg.setName("DolphinAI Agency");
        agencyOrg.setPlan("ENTERPRISE");
        agencyOrg.setBillingEmail("billing@dolphin.ai");
        orgRepo.save(agencyOrg);

        // 2. Create Isolated client workspaces
        Workspace w1 = new Workspace();
        w1.setName("Dolphin eCommerce India");
        w1.setOrganization(agencyOrg);
        workspaceRepo.save(w1);

        Workspace w2 = new Workspace();
        w2.setName("Apex Real Estate Delhi");
        w2.setOrganization(agencyOrg);
        workspaceRepo.save(w2);

        // 3. Create Admin user
        User admin = new User();
        admin.setEmail(demoEmail);
        admin.setPassword(encoder.encode(demoPassword));
        admin.setName("Srivan");
        admin.setRole("OWNER");
        admin.setAccountId(w1.getId()); // dolphin eCommerce India is active default
        admin.setOrganization(agencyOrg);
        userRepo.save(admin);

        // 4. Create dynamic empty wallets per workspace
        Wallet wallet1 = new Wallet();
        wallet1.setAccountId(w1.getId());
        wallet1.setBalance(0.0);
        wallet1.setTotalSpent(0.0);
        wallet1.setDailyBudgetLimit(5000.0);
        walletRepo.save(wallet1);

        Wallet wallet2 = new Wallet();
        wallet2.setAccountId(w2.getId());
        wallet2.setBalance(0.0);
        wallet2.setTotalSpent(0.0);
        wallet2.setDailyBudgetLimit(5000.0);
        walletRepo.save(wallet2);

        log.info("✅ Empty workspaces and owner account successfully initialized with zero balances.");
    }

    private void seedMetrics(String accountId, Campaign c, double totalSpend,
                             long totalImps, long totalClicks, long totalConvs, double totalRev) {
        LocalDate now = LocalDate.now();
        double dailySpend = totalSpend / 10;
        long dailyImps = totalImps / 10;
        long dailyClicks = totalClicks / 10;
        long dailyConvs = totalConvs / 10;
        double dailyRev = totalRev / 10;

        for (int i = 9; i >= 0; i--) {
            MetricSnapshot s = new MetricSnapshot();
            s.setAccountId(accountId);
            s.setCampaignId(c.getId());
            s.setCampaignName(c.getName());
            s.setSnapshotDate(now.minusDays(i));
            s.setSpend(dailySpend);
            s.setImpressions(dailyImps);
            s.setClicks(dailyClicks);
            s.setConversions(dailyConvs);
            s.setRevenue(dailyRev);
            s.setCtr(dailyImps > 0 ? ((double) dailyClicks / dailyImps) * 100 : 0.0);
            s.setRoas(dailySpend > 0 ? dailyRev / dailySpend : 0.0);
            snapshotRepo.save(s);
        }
    }
}
