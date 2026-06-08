package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.BrainEvent;
import com.chubby.dolphin.entity.Wallet;
import com.chubby.dolphin.repository.BrainEventRepository;
import com.chubby.dolphin.repository.WalletRepository;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.RateLimiterService;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@Slf4j
public class PaymentController {

    private final SecurityUtils        sec;
    private final WalletRepository     walletRepo;
    private final BrainEventRepository brainEventRepo;
    private final RateLimiterService   rateLimiter;
    private final com.chubby.dolphin.repository.WalletTransactionRepository txRepo;

    @Value("${razorpay.key.id}")     private String keyId;
    @Value("${razorpay.key.secret}") private String keySecret;
    @Value("${razorpay.currency}")   private String currency;

    public PaymentController(SecurityUtils sec,
                             WalletRepository walletRepo,
                             BrainEventRepository brainEventRepo,
                             RateLimiterService rateLimiter,
                             com.chubby.dolphin.repository.WalletTransactionRepository txRepo) {
        this.sec = sec;
        this.walletRepo = walletRepo;
        this.brainEventRepo = brainEventRepo;
        this.rateLimiter = rateLimiter;
        this.txRepo = txRepo;
    }

    /**
     * Step 1 — Create a Razorpay order.
     * Rate limited: 5 payment attempts per minute per user.
     */
    @PostMapping("/order")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body) {
        String workspaceId = sec.currentWorkspaceId();

        // Rate limit payment attempts
        if (!rateLimiter.isAllowed(workspaceId, RateLimiterService.LimitType.PAYMENT)) {
            return ResponseEntity.status(429).body(Map.of("error", "Too many payment attempts. Try again in 1 minute."));
        }

        int amountInRupees = Integer.parseInt(body.get("amount").toString());
        if (amountInRupees < 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "Minimum amount is ₹1"));
        }

        int amountInPaise = amountInRupees * 100;
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);
            JSONObject options = new JSONObject();
            options.put("amount",          amountInPaise);
            options.put("currency",        currency);
            options.put("receipt",         "rcpt_" + System.currentTimeMillis());
            options.put("payment_capture", 1);

            Order order = client.orders.create(options);
            log.info("💳 Razorpay order created: {} for ₹{}", order.get("id"), amountInRupees);

            return ResponseEntity.ok(Map.of(
                "key_id",   keyId,
                "order_id", order.get("id").toString(),
                "amount",   amountInPaise,
                "currency", currency
            ));
        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Payment gateway error: " + e.getMessage()));
        }
    }

    /**
     * Step 2 — Verify Razorpay payment signature.
     * If valid → credit wallet. If invalid → reject.
     */
    @PostMapping("/verify")
    @Transactional
    public ResponseEntity<?> verifyPayment(@RequestBody Map<String, String> body) {
        String paymentId = body.get("razorpay_payment_id");
        String orderId   = body.get("razorpay_order_id");
        String signature = body.get("razorpay_signature");
        String amountStr = body.get("amount");

        if (paymentId == null || orderId == null || signature == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing payment fields"));
        }

        // 1. Payment Replay Protection
        if (txRepo.existsByReferenceId(paymentId)) {
            log.warn("⚠️ Duplicate Razorpay payment replay attempt detected: {}", paymentId);
            return ResponseEntity.status(409).body(Map.of("error", "Duplicate transaction detected"));
        }

        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id",   orderId);
            attributes.put("razorpay_payment_id", paymentId);
            attributes.put("razorpay_signature",  signature);

            boolean valid = Utils.verifyPaymentSignature(attributes, keySecret);
            if (!valid) {
                log.warn("❌ Invalid Razorpay signature for payment {}", paymentId);
                return ResponseEntity.badRequest().body(Map.of("error", "Payment verification failed — invalid signature"));
            }

            double amountInRupees = Double.parseDouble(amountStr != null ? amountStr : "0");
            String workspaceId    = sec.currentWorkspaceId();

            Wallet wallet = walletRepo.findByWorkspaceId(workspaceId).orElseGet(() -> {
                Wallet w = new Wallet();
                w.setWorkspaceId(workspaceId);
                w.setBalance(0.0);
                w.setTotalSpent(0.0);
                w.setDailyBudgetLimit(10000.0);
                return w;
            });
            wallet.setBalance(wallet.getBalance() + amountInRupees);
            wallet.setUpdatedAt(LocalDateTime.now());
            walletRepo.save(wallet);

            // Record transaction
            com.chubby.dolphin.entity.WalletTransaction tx = new com.chubby.dolphin.entity.WalletTransaction();
            tx.setWorkspaceId(workspaceId);
            tx.setType("CREDIT");
            tx.setAmount(amountInRupees);
            tx.setBalanceAfter(wallet.getBalance());
            tx.setDescription(String.format("💳 ₹%.0f added via Razorpay", amountInRupees));
            tx.setReferenceId(paymentId);
            tx.setCreatedAt(LocalDateTime.now());
            txRepo.save(tx);

            BrainEvent evt = new BrainEvent();
            evt.setWorkspaceId(workspaceId);
            evt.setEventType("WALLET_FUNDED");
            evt.setMessage(String.format("💳 ₹%.0f added via Razorpay [%s]", amountInRupees, paymentId));
            evt.setSeverity("SUCCESS");
            evt.setCreatedAt(LocalDateTime.now());
            brainEventRepo.save(evt);

            log.info("✅ Payment verified — ₹{} credited to {}", amountInRupees, workspaceId);
            return ResponseEntity.ok(Map.of(
                "success",    true,
                "payment_id", paymentId,
                "amount",     amountInRupees,
                "balance",    wallet.getBalance()
            ));

        } catch (Exception e) {
            log.error("Payment verification error: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Verification error: " + e.getMessage()));
        }
    }

    /** Public key config for frontend */
    @GetMapping("/config")
    public ResponseEntity<?> config() {
        return ResponseEntity.ok(Map.of("key_id", keyId, "currency", currency));
    }
}
