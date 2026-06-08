# DolphinAI — Payment System & Billing Audit Report

This document reports on the payment controllers, ledger models, invoice generators, and transaction frameworks in DolphinAI.

---

## 1. Code-Level Inspection

### What Exists (Real Implementations)
* **Razorpay Payment Integration (`PaymentController.java`)**:
  * `/api/payment/order`: Uses the Razorpay Java SDK to create an order using standard currency (INR) and amount parameters.
  * `/api/payment/verify`: Checks the cryptographic signature of the callback payload (`razorpay_payment_id`, `razorpay_order_id`, `razorpay_signature`) using the SDK's `Utils.verifyPaymentSignature`. If valid, it updates the ledger and workspace balance.
* **Wallet Balance & Ledgers (`WalletController.java`)**:
  * `/api/wallet/balance`: Loads the current wallet balance.
  * `/api/wallet/transactions`: Fetches a chronological list of debit/credit transactions from the database ledger.
* **GST Invoice Generator (`InvoiceController.java`, `GstInvoiceService.java`)**:
  * Computes CGST, SGST, and IGST based on the state/region of the workspace config.
  * Locks transaction values, generates a serial code via the `invoice_sequences` table, and compiles a GST-compliant invoice PDF.
* **Database Ledger Tables**:
  * `wallet`: Holds `id`, `account_id`, `balance` (double), and version timestamps.
  * `wallet_transactions`: Records transactions with `id`, `wallet_id`, `amount`, `type` (CREDIT/DEBIT), `description`, and `reference_id`.
  * `invoices` & `invoice_sequences`: Store invoice numbers and calculations.

### What is Missing
* **SaaS Subscription Plan Tiers**: The codebase has absolutely no models, controllers, or database structures representing "Starter", "Growth", or "Enterprise" subscription packages.
* **Recurring Billing & Renewals**: The system does not support card tokenization, mandate registrations, or automated billing cycles. It is strictly a manual top-up wallet system.
* **Gateway Multi-Tenancy**: The only gateway integrated is Razorpay. No Stripe support is coded, although the UI directive references Stripe.

### What is Mocked
* **AI Usage Warnings**: The `ai_workspace_budgets` table registers warning thresholds, but the system does not actively enforce API blocking when a wallet balance hits zero.

### What is Broken / Insecure
* **Razorpay Replay Vulnerability**: `verifyPayment` verifies signatures but never queries the database to check if the transaction ID was already processed. A user can reuse a valid signature payload to add duplicate funds to their wallet balance repeatedly.
* **Ephemeral File Storage**: Invoices are written to local server disk (`storage/invoices/`). If the Docker container restarts, all generated PDF files are lost.
* **Hidden Route Isolation**: The UI components for wallet recharge and billing transaction histories are hidden from the sidebar but remain accessible via direct URL routing.

---

## 2. Alignment with Core Growth OS

The directive mandates that **Billing must be kept isolated under Settings -> Billing** to prevent blocking core AI growth engines. 

* **Isolate & Preserve**: The existing Razorpay wallet system and GST invoice PDF compiler will be kept. However, they must be consolidated strictly under a single "Billing & Wallet" tab inside the Settings module.
* **Refactor Plan**:
  1. Add a transaction uniqueness check on `verifyPayment` in `PaymentController.java` to block signature replays.
  2. Implement a local plans config mapping (Starter, Growth, Enterprise) in Settings to display active subscription packages.
  3. Relocate PDF invoice storage from the local disk to secure S3-compliant storage.
