-- V7__fix_spouse_zelle_and_known_transfers.sql
--
-- Auto-categorizes known internal transfer patterns that V6 missed.
-- These are credits/debits between Gaurav and Khyati's own accounts.
-- All safe to re-run (WHERE category_id IS NULL guard prevents overwriting).

-- ─────────────────────────────────────────────
-- Spouse Zelle transfers — Gaurav → Khyati (shows as credit on Khyati's BofA)
-- Pattern: "Zelle payment from GAURAV GAWADE"
-- ─────────────────────────────────────────────
UPDATE transactions t
SET category_id = (
    SELECT c.id FROM categories c
    WHERE c.user_id = t.user_id
      AND LOWER(c.name) = 'transfer'
    LIMIT 1
),
updated_at = NOW()
WHERE LOWER(t.merchant_name) LIKE '%zelle payment from gaurav gawade%'
  AND t.category_id IS NULL
  AND t.is_manual = false
  AND EXISTS (
    SELECT 1 FROM categories c
    WHERE c.user_id = t.user_id AND LOWER(c.name) = 'transfer'
  );

-- ─────────────────────────────────────────────
-- Spouse Zelle transfers — Khyati → Gaurav (shows as debit on Khyati's BofA)
-- Pattern: "Zelle payment to Gaurav Gawade"
-- ─────────────────────────────────────────────
UPDATE transactions t
SET category_id = (
    SELECT c.id FROM categories c
    WHERE c.user_id = t.user_id
      AND LOWER(c.name) = 'transfer'
    LIMIT 1
),
updated_at = NOW()
WHERE LOWER(t.merchant_name) LIKE '%zelle payment to gaurav gawade%'
  AND t.category_id IS NULL
  AND t.is_manual = false
  AND EXISTS (
    SELECT 1 FROM categories c
    WHERE c.user_id = t.user_id AND LOWER(c.name) = 'transfer'
  );

-- ─────────────────────────────────────────────
-- Spouse Zelle — Gaurav → Khyati "Savings" label
-- ─────────────────────────────────────────────
UPDATE transactions t
SET category_id = (
    SELECT c.id FROM categories c
    WHERE c.user_id = t.user_id
      AND LOWER(c.name) = 'transfer'
    LIMIT 1
),
updated_at = NOW()
WHERE LOWER(t.merchant_name) LIKE '%zelle payment from gaurav%'
  AND t.category_id IS NULL
  AND t.is_manual = false
  AND EXISTS (
    SELECT 1 FROM categories c
    WHERE c.user_id = t.user_id AND LOWER(c.name) = 'transfer'
  );

-- ─────────────────────────────────────────────
-- Spouse Zelle — Khyati → Gaurav (on Gaurav's Chase checking)
-- Pattern: "Zelle payment from KHYATI NAHTA"
-- ─────────────────────────────────────────────
UPDATE transactions t
SET category_id = (
    SELECT c.id FROM categories c
    WHERE c.user_id = t.user_id
      AND LOWER(c.name) = 'transfer'
    LIMIT 1
),
updated_at = NOW()
WHERE LOWER(t.merchant_name) LIKE '%zelle payment from khyati%'
  AND t.category_id IS NULL
  AND t.is_manual = false
  AND EXISTS (
    SELECT 1 FROM categories c
    WHERE c.user_id = t.user_id AND LOWER(c.name) = 'transfer'
  );

-- ─────────────────────────────────────────────
-- Robinhood credits (investment returns) → Investments
-- Currently null category, counting as income
-- ─────────────────────────────────────────────
UPDATE transactions t
SET category_id = (
    SELECT c.id FROM categories c
    WHERE c.user_id = t.user_id
      AND LOWER(c.name) = 'investments'
    LIMIT 1
),
updated_at = NOW()
WHERE LOWER(t.merchant_name) LIKE '%robinhood%'
  AND t.category_id IS NULL
  AND t.is_manual = false
  AND EXISTS (
    SELECT 1 FROM categories c
    WHERE c.user_id = t.user_id AND LOWER(c.name) = 'investments'
  );

-- ─────────────────────────────────────────────
-- GE Healthcare payroll → Salary
-- ─────────────────────────────────────────────
UPDATE transactions t
SET category_id = (
    SELECT c.id FROM categories c
    WHERE c.user_id = t.user_id
      AND LOWER(c.name) = 'salary'
    LIMIT 1
),
updated_at = NOW()
WHERE (LOWER(t.merchant_name) LIKE '%ge healthcare%payroll%'
    OR LOWER(t.merchant_name) LIKE '%ge healthcare%salary%'
    OR LOWER(t.merchant_name) LIKE '%ge precision%t&l%'
    OR LOWER(t.merchant_name) LIKE '%gehc t&l%')
  AND t.category_id IS NULL
  AND t.is_manual = false
  AND EXISTS (
    SELECT 1 FROM categories c
    WHERE c.user_id = t.user_id AND LOWER(c.name) = 'salary'
  );

-- ─────────────────────────────────────────────
-- Ernst & Young payroll → Salary
-- ─────────────────────────────────────────────
UPDATE transactions t
SET category_id = (
    SELECT c.id FROM categories c
    WHERE c.user_id = t.user_id
      AND LOWER(c.name) = 'salary'
    LIMIT 1
),
updated_at = NOW()
WHERE LOWER(t.merchant_name) LIKE '%ernst & young%payroll%'
  AND t.category_id IS NULL
  AND t.is_manual = false
  AND EXISTS (
    SELECT 1 FROM categories c
    WHERE c.user_id = t.user_id AND LOWER(c.name) = 'salary'
  );

-- ─────────────────────────────────────────────
-- American Express payment → Credit Card Payment
-- ─────────────────────────────────────────────
UPDATE transactions t
SET category_id = (
    SELECT c.id FROM categories c
    WHERE c.user_id = t.user_id
      AND LOWER(c.name) = 'credit card payment'
    LIMIT 1
),
updated_at = NOW()
WHERE LOWER(t.merchant_name) LIKE '%american express%ach pmt%'
  AND t.category_id IS NULL
  AND t.is_manual = false
  AND EXISTS (
    SELECT 1 FROM categories c
    WHERE c.user_id = t.user_id AND LOWER(c.name) = 'credit card payment'
  );

-- ─────────────────────────────────────────────
-- Fidelity investments → Investments
-- ─────────────────────────────────────────────
UPDATE transactions t
SET category_id = (
    SELECT c.id FROM categories c
    WHERE c.user_id = t.user_id
      AND LOWER(c.name) = 'investments'
    LIMIT 1
),
updated_at = NOW()
WHERE LOWER(t.merchant_name) LIKE '%fid bkg svc%'
  AND t.category_id IS NULL
  AND t.is_manual = false
  AND EXISTS (
    SELECT 1 FROM categories c
    WHERE c.user_id = t.user_id AND LOWER(c.name) = 'investments'
  );
