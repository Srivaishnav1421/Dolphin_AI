package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.*;
import com.chubby.dolphin.repository.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;

@Service
@Slf4j
public class DataSeeder {

    private final UserRepository   userRepo;
    private final WalletRepository walletRepo;
    private final PasswordEncoder  encoder;
    private final OrganizationRepository orgRepo;
    private final WorkspaceRepository    workspaceRepo;
    private final WorkflowTemplateRepository templateRepo;
    private final SubscriptionPlanRepository subscriptionPlanRepo;
    private final Environment environment;

    public DataSeeder(UserRepository userRepo,
                      WalletRepository walletRepo,
                      PasswordEncoder encoder,
                      OrganizationRepository orgRepo,
                      WorkspaceRepository workspaceRepo,
                      WorkflowTemplateRepository templateRepo,
                      SubscriptionPlanRepository subscriptionPlanRepo,
                      Environment environment) {
        this.userRepo = userRepo;
        this.walletRepo = walletRepo;
        this.encoder = encoder;
        this.orgRepo = orgRepo;
        this.workspaceRepo = workspaceRepo;
        this.templateRepo = templateRepo;
        this.subscriptionPlanRepo = subscriptionPlanRepo;
        this.environment = environment;
    }

    @Value("${first-run.owner.email:}")    private String firstRunOwnerEmail;
    @Value("${first-run.owner.password:}") private String firstRunOwnerPassword;
    @Value("${first-run.owner.name:Owner}") private String firstRunOwnerName;
    @Value("${first-run.organization.name:DolphinAI Organization}") private String firstRunOrganizationName;
    @Value("${first-run.workspace.name:Default Workspace}") private String firstRunWorkspaceName;
    @Value("${demo.email:}")    private String demoEmail;
    @Value("${demo.password:}") private String demoPassword;
    @Value("${app.seed.demo-users-enabled:true}") private boolean demoUsersEnabled;

    /**
     * Seeds only real bootstrap essentials and shared reference data:
     * 1. Optional FIRST_RUN_OWNER_* owner, organization, and neutral workspace
     * 2. Empty wallet for the first-run workspace
     * 3. Shared subscription/workflow reference rows
     */
    @PostConstruct
    public void seed() {
        authCleanup();
        seedSubscriptionPlans();
        seedFirstRunOwner();
        seedDemoTeamUsers();
        seedTemplates();
        log.info("DolphinAI startup seed completed.");
    }

    /**
     * One-time auth cleanup: remove directly-inserted test user and restore
     * admin password to the known DEMO_PASSWORD from .env.
     * Safe to remove this method after verification is complete.
     */
    private void authCleanup() {
        if (isProdProfile() || !demoUsersEnabled
                || demoEmail == null || demoEmail.isBlank()
                || demoPassword == null || demoPassword.isBlank()) {
            return;
        }

        // 1. Remove test@dolphin.ai if it exists (was inserted directly for testing)
        userRepo.findByEmail("test@dolphin.ai").ifPresent(testUser -> {
            userRepo.delete(testUser);
            log.info("🧹 Auth cleanup: removed directly-inserted test user test@dolphin.ai");
        });

        // 2. Restore admin@dolphin.ai password to DEMO_PASSWORD if configured
        userRepo.findByEmail(demoEmail).ifPresent(admin -> {
            admin.setPassword(encoder.encode(demoPassword));
            userRepo.save(admin);
            log.info("🔑 Auth cleanup: restored {} password to DEMO_PASSWORD value", demoEmail);
        });
    }

    /**
     * Seeds Phase 1 billing tiers: Starter, Growth, Pro, Enterprise.
     * Prices in INR. Only seeds if no plans exist yet.
     */
    private void seedSubscriptionPlans() {
        if (subscriptionPlanRepo.count() > 0) {
            log.info("✅ Subscription plans already seeded");
            return;
        }

        subscriptionPlanRepo.save(SubscriptionPlan.builder()
                .name("Starter")
                .basePriceInr(1999.00)
                .includedSeats(2)
                .seatPriceInr(499.00)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        subscriptionPlanRepo.save(SubscriptionPlan.builder()
                .name("Growth")
                .basePriceInr(4999.00)
                .includedSeats(5)
                .seatPriceInr(799.00)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        subscriptionPlanRepo.save(SubscriptionPlan.builder()
                .name("Pro")
                .basePriceInr(9999.00)
                .includedSeats(15)
                .seatPriceInr(999.00)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        subscriptionPlanRepo.save(SubscriptionPlan.builder()
                .name("Enterprise")
                .basePriceInr(24999.00)
                .includedSeats(50)
                .seatPriceInr(1499.00)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        log.info("✅ Phase 1 billing tiers seeded: Starter / Growth / Pro / Enterprise (INR)");
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

    private void seedFirstRunOwner() {
        if (firstRunOwnerEmail == null || firstRunOwnerEmail.isBlank()
                || firstRunOwnerPassword == null || firstRunOwnerPassword.isBlank()) {
            log.info("First-run owner bootstrap skipped because FIRST_RUN_OWNER_EMAIL or FIRST_RUN_OWNER_PASSWORD is not configured.");
            return;
        }
        if (userRepo.existsByEmail(firstRunOwnerEmail)) {
            log.info("First-run owner already exists: {}", firstRunOwnerEmail);
            return;
        }

        // 1. Create first-run organization
        Organization organization = new Organization();
        organization.setName(cleanName(firstRunOrganizationName, "DolphinAI Organization"));
        organization.setPlan("ENTERPRISE");
        organization.setBillingEmail(firstRunOwnerEmail);
        orgRepo.save(organization);

        // 2. Create one neutral first-run workspace. Real client workspaces are created by users later.
        Workspace workspace = new Workspace();
        workspace.setName(cleanName(firstRunWorkspaceName, "Default Workspace"));
        workspace.setOrganization(organization);
        workspaceRepo.save(workspace);

        // 3. Create Admin user
        User admin = new User();
        admin.setEmail(firstRunOwnerEmail);
        admin.setPassword(encoder.encode(firstRunOwnerPassword));
        admin.setName(firstRunOwnerName == null || firstRunOwnerName.isBlank() ? "Owner" : firstRunOwnerName);
        admin.setRole("OWNER");
        admin.setAccountId(workspace.getId());
        admin.setOrganization(organization);
        userRepo.save(admin);

        // 4. Create empty wallet for the first-run workspace
        Wallet wallet = new Wallet();
        wallet.setAccountId(workspace.getId());
        wallet.setBalance(0.0);
        wallet.setTotalSpent(0.0);
        wallet.setDailyBudgetLimit(5000.0);
        walletRepo.save(wallet);

        log.info("First-run owner and empty workspace initialized for owner email: {}", firstRunOwnerEmail);
    }

    private void seedDemoTeamUsers() {
        if (isProdProfile()) {
            log.info("Demo team user seed skipped in production profile.");
            return;
        }
        if (!demoUsersEnabled) {
            return;
        }

        if (demoEmail == null || demoEmail.isBlank() || demoPassword == null || demoPassword.isBlank()) {
            log.warn("Demo team user seed skipped because DEMO_EMAIL or DEMO_PASSWORD is not configured.");
            return;
        }

        User owner = userRepo.findByEmail(demoEmail).orElse(null);
        if (owner == null || owner.getWorkspaceId() == null) {
            return;
        }

        seedDemoUser("workspace.admin@dolphin.ai", "Admin User", "ADMIN", owner);
        seedDemoUser("manager@dolphin.ai", "Manager User", "MANAGER", owner);
        seedDemoUser("employee@dolphin.ai", "Employee User", "EMPLOYEE", owner);
        seedDemoUser("viewer@dolphin.ai", "Viewer User", "VIEWER", owner);
    }

    private void seedDemoUser(String email, String name, String role, User owner) {
        if (userRepo.existsByEmail(email)) {
            return;
        }
        User user = new User();
        user.setEmail(email);
        user.setPassword(encoder.encode(demoPassword));
        user.setName(name);
        user.setRole(role);
        user.setAccountId(owner.getWorkspaceId());
        user.setOrganization(owner.getOrganization());
        userRepo.save(user);
        log.info("✅ Demo {} user seeded: {}", role, email);
    }

    private boolean isProdProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    private String cleanName(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").trim();
        return cleaned.length() <= 120 ? cleaned : cleaned.substring(0, 120);
    }

}
