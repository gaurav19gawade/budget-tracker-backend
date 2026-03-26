package com.budgettracker.service;

import com.budgettracker.model.Category;
import com.budgettracker.model.Transaction;
import com.budgettracker.repository.CategoryRepository;
import com.budgettracker.repository.TransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategorizationService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final AnthropicCategorizationService llmService;

    // Keyword → canonical category name mapping.
    // Keys are lowercase substrings matched against the normalized merchant name.
    // Order matters — first match wins. More specific rules go first.
    private static final Map<String, String> KEYWORD_RULES = new LinkedHashMap<>() {{

        // ── Transfers (isExcluded) — match BEFORE anything else ──────────────
        // Spouse/own-account Zelle must be excluded before generic Zelle rules fire.
        put("online transfer to",        "Transfer");
        put("online transfer from",      "Transfer");
        put("transfer to sav",           "Transfer");
        put("transfer from sav",         "Transfer");
        put("transfer to chk",           "Transfer");
        put("transfer from chk",         "Transfer");

        // ── Credit Card Payments (isExcluded) ────────────────────────────────
        put("chase credit crd",          "Credit Card Payment");
        put("ach hold chase credit",     "Credit Card Payment");
        put("crcardpmt",                 "Credit Card Payment");
        put("autopay",                   "Credit Card Payment");
        put("automatic payment",         "Credit Card Payment");
        put("payment thank you",         "Credit Card Payment");
        put("credit card payment",       "Credit Card Payment");
        put("minimum payment",           "Credit Card Payment");
        put("amex epayment",             "Credit Card Payment");
        put("discover e-payment",        "Credit Card Payment");
        put("citi autopay",              "Credit Card Payment");
        put("capital one payment",       "Credit Card Payment");

        // ── Salary / Income (isExcluded) ─────────────────────────────────────
        put("payroll",                   "Salary");
        put("reg.salary",                "Salary");
        put("direct deposit",            "Salary");
        put("gehc t&l",                  "Salary");  // GE expense reimbursement

        // ── Investments (isExcluded) ──────────────────────────────────────────
        put("robinhood",                 "Investments");
        put("fid bkg svc",               "Investments");   // Fidelity
        put("fidelity",                  "Investments");
        put("vanguard",                  "Investments");
        put("schwab",                    "Investments");
        put("etrade",                    "Investments");
        put("aspora",                    "Investments");
        put("coinbase",                  "Investments");

        // ── Remittance ────────────────────────────────────────────────────────
        put("western union",             "Remittance");
        put("moneygram",                 "Remittance");
        put("remitly",                   "Remittance");
        put("wise",                      "Remittance");
        put("xoom",                      "Remittance");

        // ── Mortgage ─────────────────────────────────────────────────────────
        put("m & t mortgage",            "Mortgage");
        put("m&t mortgage",              "Mortgage");
        put("nsm dbamr",                 "Mortgage");      // Mr.Cooper/Nationstar
        put("mr. cooper",                "Mortgage");
        put("nationstar",                "Mortgage");
        put("quicken loans",             "Mortgage");
        put("rocket mortgage",           "Mortgage");
        put("mtg pyt",                   "Mortgage");
        put("pl*patricianasso",          "Mortgage");      // Patrician Associates rent
        put("pl*paylease",               "Mortgage");      // PayLease rent processing fee

        // ── Childcare ────────────────────────────────────────────────────────
        put("goddard",                   "Childcare");
        put("cadence educatio",          "Childcare");
        put("bright horizons",           "Childcare");
        put("kindercare",                "Childcare");
        put("learning care",             "Childcare");
        put("tutor",                     "Childcare");

        // ── Car Payment ───────────────────────────────────────────────────────
        put("hmf hmfusa",                "Car Payment");   // Hyundai Motor Finance
        put("toyota motor credit",       "Car Payment");
        put("ford motor credit",         "Car Payment");
        put("honda financial",           "Car Payment");
        put("bmw financial",             "Car Payment");
        put("ally financial",            "Car Payment");
        put("gm financial",              "Car Payment");
        put("auto loan",                 "Car Payment");

        // ── Utilities ────────────────────────────────────────────────────────
        put("ngrid",                     "Utilities");     // National Grid
        put("national grid",             "Utilities");
        put("eversource",                "Utilities");
        put("con edison",                "Utilities");
        put("pge",                       "Utilities");
        put("water bill",                "Utilities");
        put("electric bill",             "Utilities");
        put("gas bill",                  "Utilities");
        put("comcast",                   "Utilities");
        put("xfinity",                   "Utilities");
        put("spectrum",                  "Utilities");
        put("verizon",                   "Utilities");
        put("at&t",                      "Utilities");
        put("t-mobile",                  "Utilities");
        put("electric",                  "Utilities");
        put("internet",                  "Utilities");
        put("utility",                   "Utilities");

        // ── HOA ───────────────────────────────────────────────────────────────
        put("dean farm ridge",           "HOA");
        put("hoa",                       "HOA");
        put("homeowners association",    "HOA");
        put("condo fee",                 "HOA");

        // ── Healthcare ────────────────────────────────────────────────────────
        put("patientfi",                 "Healthcare");
        put("carecredit",                "Healthcare");
        put("medical",                   "Healthcare");
        put("clinic",                    "Healthcare");
        put("hospital",                  "Healthcare");
        put("dental",                    "Healthcare");
        put("vision",                    "Healthcare");
        put("pharmacy",                  "Healthcare");

        // ── Bank Fee ──────────────────────────────────────────────────────────
        put("overdraft",                 "Bank Fee");
        put("insufficient funds",        "Bank Fee");
        put("monthly fee",               "Bank Fee");
        put("account fee",               "Bank Fee");
        put("wire fee",                  "Bank Fee");

        // ── Refund (isExcluded) ───────────────────────────────────────────────
        put("return of posted check",    "Refund");
        put("payment reversal",          "Refund");

        // ── Food Delivery ─────────────────────────────────────────────────────
        // Match before Restaurants — doordash/ubereats are delivery, not dine-in
        put("doordash",                  "Food Delivery");
        put("uber eats",                 "Food Delivery");
        put("ubereats",                  "Food Delivery");
        put("grubhub",                   "Food Delivery");
        put("postmates",                 "Food Delivery");
        put("instacart",                 "Food Delivery");
        put("seamless",                  "Food Delivery");
        put("caviar",                    "Food Delivery");
        put("tiffin",                    "Food Delivery");  // Tiffin meal subscription
        put("kwalityicecream",           "Food Delivery");  // Kwality ice cream delivery

        // ── Restaurants ───────────────────────────────────────────────────────
        put("chipotle",                  "Restaurants");
        put("mcdonald",                  "Restaurants");
        put("starbucks",                 "Restaurants");
        put("dunkin",                    "Restaurants");
        put("taco bell",                 "Restaurants");
        put("burger king",               "Restaurants");
        put("wendy",                     "Restaurants");
        put("chick-fil-a",               "Restaurants");
        put("domino",                    "Restaurants");
        put("pizza hut",                 "Restaurants");
        put("panera",                    "Restaurants");
        put("olive garden",              "Restaurants");
        put("applebee",                  "Restaurants");
        put("cheesecake",                "Restaurants");
        put("ihop",                      "Restaurants");
        put("denny",                     "Restaurants");
        put("five guys",                 "Restaurants");
        put("shake shack",               "Restaurants");
        put("in-n-out",                  "Restaurants");
        put("sankalp",                   "Restaurants");  // Sankalp Indian restaurant
        put("kwality",                   "Restaurants");  // Kwality Indian restaurant
        put("halva",                     "Restaurants");  // Halva Mediterranean
        put("karma",                     "Restaurants");  // TST*KARMA
        put("turmeric",                  "Restaurants");  // Turmeric House
        put("brilla coffee",             "Restaurants");
        put("restaurant",                "Restaurants");
        put("grill",                     "Restaurants");
        put("bistro",                    "Restaurants");
        put("cafe",                      "Restaurants");
        put("diner",                     "Restaurants");
        put("eatery",                    "Restaurants");
        put("coffee",                    "Restaurants");
        put("espresso",                  "Restaurants");
        put("resta",                     "Restaurants");  // catches "NEROLI MERCATO RESTA"

        // ── Groceries ─────────────────────────────────────────────────────────
        put("whole foods",               "Groceries");
        put("wholefds",                  "Groceries");    // Teller abbreviation
        put("trader joe",                "Groceries");
        put("kroger",                    "Groceries");
        put("safeway",                   "Groceries");
        put("publix",                    "Groceries");
        put("aldi",                      "Groceries");
        put("costco whse",               "Groceries");   // Costco warehouse = groceries
        put("sam's club",                "Groceries");
        put("walmart",                   "Groceries");
        put("sprouts",                   "Groceries");
        put("wegmans",                   "Groceries");
        put("stop & shop",               "Groceries");
        put("shaws",                     "Groceries");   // Shaw's supermarket
        put("apna bazar",                "Groceries");   // Indian grocery
        put("fruttiberri",               "Groceries");   // Natural Fruttiberri
        put("grocery",                   "Groceries");
        put("supermarket",               "Groceries");

        // ── Gas ───────────────────────────────────────────────────────────────
        // NOTE: "costco gas" must come BEFORE "costco" (costco whse → Groceries above)
        put("costco gas",                "Gas");
        put("citgo",                     "Gas");         // Norwood Citgo
        put("shell",                     "Gas");
        put("exxon",                     "Gas");
        put("chevron",                   "Gas");
        put("mobil",                     "Gas");
        put("sunoco",                    "Gas");
        put("speedway",                  "Gas");
        put("casey",                     "Gas");
        put("wawa",                      "Gas");
        put("bp",                        "Gas");
        put("gas station",               "Gas");

        // ── Transportation ────────────────────────────────────────────────────
        put("lyft",                      "Transportation");
        put("uber",                      "Transportation");
        put("ezpass",                    "Transportation");
        put("ez pass",                   "Transportation");
        put("spothero",                  "Transportation");
        put("parkm",                     "Transportation");  // www.parkm.com
        put("logan pkg",                 "Transportation");  // Logan airport parking
        put("massport",                  "Transportation");
        put("parking",                   "Transportation");
        put("metro",                     "Transportation");
        put("mta",                       "Transportation");
        put("transit",                   "Transportation");
        put("zipcar",                    "Transportation");
        put("enterprise",                "Transportation");
        put("hertz",                     "Transportation");
        put("avis",                      "Transportation");
        put("amtrak",                    "Transportation");
        put("greyhound",                 "Transportation");

        // ── Travel ────────────────────────────────────────────────────────────
        put("delta air",                 "Travel");
        put("united air",                "Travel");
        put("american air",              "Travel");
        put("southwest air",             "Travel");
        put("jetblue",                   "Travel");
        put("spirit air",                "Travel");
        put("frontier air",              "Travel");
        put("airlines",                  "Travel");
        put("airbnb",                    "Travel");
        put("expedia",                   "Travel");
        put("booking.com",               "Travel");
        put("marriott",                  "Travel");
        put("hilton",                    "Travel");
        put("hyatt",                     "Travel");
        put("holiday inn",               "Travel");

        // ── Shopping ──────────────────────────────────────────────────────────
        put("amazon",                    "Shopping");
        put("ebay",                      "Shopping");
        put("etsy",                      "Shopping");
        put("best buy",                  "Shopping");
        put("apple store",               "Shopping");
        put("ikea",                      "Shopping");
        put("h&m",                       "Shopping");
        put("zara",                      "Shopping");
        put("gap",                       "Shopping");
        put("old navy",                  "Shopping");
        put("macy",                      "Shopping");
        put("nordstrom",                 "Shopping");
        put("tj maxx",                   "Shopping");
        put("tjmaxx",                    "Shopping");
        put("marshalls",                 "Shopping");
        put("ross",                      "Shopping");
        put("nike",                      "Shopping");
        put("adidas",                    "Shopping");
        put("sephora",                   "Shopping");
        put("uniqlo",                    "Shopping");
        put("kohl",                      "Shopping");
        put("target",                    "Shopping");   // Target (non-grocery)
        put("walmart.com",               "Shopping");
        put("wal-mart",                  "Shopping");
        put("walgreens",                 "Shopping");
        put("cvs",                       "Shopping");
        put("saks",                      "Shopping");
        put("finish line",               "Shopping");
        put("klarna",                    "Shopping");   // Klarna BNPL payments
        put("sonos",                     "Shopping");
        put("diy garage",                "Shopping");  // home goods/repair parts
        put("build-a-bear",              "Shopping");

        // ── Entertainment ─────────────────────────────────────────────────────
        put("netflix",                   "Entertainment");
        put("spotify",                   "Entertainment");
        put("hulu",                      "Entertainment");
        put("disney+",                   "Entertainment");
        put("hbo",                       "Entertainment");
        put("apple tv",                  "Entertainment");
        put("youtube",                   "Entertainment");
        put("twitch",                    "Entertainment");
        put("steam",                     "Entertainment");
        put("playstation",               "Entertainment");
        put("xbox",                      "Entertainment");
        put("amc",                       "Entertainment");
        put("regal",                     "Entertainment");
        put("cinema",                    "Entertainment");
        put("fandango",                  "Entertainment");
        put("peloton",                   "Entertainment");
        put("martini",                   "Entertainment");  // Martini's Pickleball
        put("pickleball",                "Entertainment");
        put("otf",                       "Entertainment");  // Orangetheory Fitness
        put("willow tv",                 "Entertainment");
        put("angel moor",                "Entertainment");
        put("dig n play",                "Entertainment");
        put("dignplay",                  "Entertainment");
        put("theater",                   "Entertainment");
        put("concert",                   "Entertainment");
        put("ticketmaster",              "Entertainment");
        put("stubhub",                   "Entertainment");
        put("apple.com/bill",            "Entertainment"); // Apple subscriptions

        // ── Donations ─────────────────────────────────────────────────────────
        put("world food program",        "Donations");
        put("red cross",                 "Donations");
        put("unicef",                    "Donations");
        put("charity",                   "Donations");
        put("donate",                    "Donations");

        // ── Internet (phone bill — already a category) ────────────────────────
        // Verizon and AT&T matched above under Utilities, this handles edge cases
        put("verizon paymentrec",        "Internet");
    }};

    /**
     * Categorizes uncategorized transactions for a user.
     *
     * Pass 1 — keyword rules: fast, free, handles well-known merchants.
     * Pass 2 — LLM (Claude Haiku): handles everything else in a single batch call.
     *
     * IMPORTANT: only transactions with category == null are touched.
     * Manual user categorizations (category != null) are never overwritten.
     *
     * @return number of transactions categorized
     */
    @Transactional
    public int categorizeForUser(Long userId) {
        List<Transaction> allTransactions  = transactionRepository.findByUserId(userId);
        List<Category>    userCategories   = categoryRepository.findByUserId(userId);

        if (userCategories.isEmpty()) {
            log.info("No categories for user {} — skipping categorization", userId);
            return 0;
        }

        // Only process uncategorized transactions — respect manual assignments
        List<Transaction> uncategorized = allTransactions.stream()
                .filter(tx -> tx.getCategory() == null)
                .filter(tx -> tx.getMerchantName() != null)
                .collect(Collectors.toList());

        if (uncategorized.isEmpty()) {
            log.debug("No uncategorized transactions for user {}", userId);
            return 0;
        }

        Map<String, Category> categoryLookup = buildCategoryLookup(userCategories);
        List<Transaction> toSave = new ArrayList<>();

        // ── Pass 1: keyword rules ─────────────────────────────────────────────
        List<Transaction> stillUncategorized = new ArrayList<>();

        for (Transaction tx : uncategorized) {
            String normalized = normalizeMerchant(tx.getMerchantName());
            Category found = findByKeyword(normalized, categoryLookup);
            if (found != null) {
                tx.setCategory(found);
                toSave.add(tx);
            } else {
                stillUncategorized.add(tx);
            }
        }

        log.info("Keyword pass: categorized {}/{} transactions for user {}",
                toSave.size(), uncategorized.size(), userId);

        // ── Pass 2: LLM for remaining ─────────────────────────────────────────
        if (!stillUncategorized.isEmpty() && llmService.isEnabled()) {
            Map<Long, Category> llmResults = llmService.categorize(stillUncategorized, userCategories);

            for (Transaction tx : stillUncategorized) {
                Category cat = llmResults.get(tx.getId());
                if (cat != null) {
                    tx.setCategory(cat);
                    toSave.add(tx);
                }
            }

            log.info("LLM pass: categorized {}/{} remaining transactions for user {}",
                    llmResults.size(), stillUncategorized.size(), userId);
        }

        if (!toSave.isEmpty()) {
            transactionRepository.saveAll(toSave);
        }

        log.info("Total categorized: {}/{} for user {}", toSave.size(), uncategorized.size(), userId);
        return toSave.size();
    }

    /**
     * Strips common Teller raw-string prefixes and noise before keyword matching.
     * e.g. "DD *DOORDASH WEGMANS" → "doordash wegmans"
     *      "AMAZON MKTPL*BE7US64Q1 A" → "amazon"
     *      "SQ *BLUE BOTTLE COFFEE" → "blue bottle coffee"
     */
    private String normalizeMerchant(String raw) {
        String s = raw.toLowerCase();

        // Strip common prefixes
        s = s.replaceAll("^(dd \\*|sq \\*|tst\\* |pp\\*|paypal \\*|amzn\\*|checkcard \\d+ )", "");

        // Strip everything after * (Teller often appends reference codes after *)
        int star = s.indexOf('*');
        if (star > 0) s = s.substring(0, star);

        // Strip trailing reference numbers (e.g. " 866-712-7753", " #12345")
        s = s.replaceAll("\\s+[#\\d][\\d\\-]{4,}.*$", "");

        return s.trim();
    }

    private Category findByKeyword(String normalizedMerchant, Map<String, Category> categoryLookup) {
        // 1. Keyword rules
        for (Map.Entry<String, String> rule : KEYWORD_RULES.entrySet()) {
            if (normalizedMerchant.contains(rule.getKey())) {
                Category cat = categoryLookup.get(rule.getValue().toLowerCase());
                if (cat != null) return cat;
            }
        }
        // 2. Direct category name match
        for (Map.Entry<String, Category> entry : categoryLookup.entrySet()) {
            if (normalizedMerchant.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Map<String, Category> buildCategoryLookup(List<Category> categories) {
        Map<String, Category> lookup = new HashMap<>();
        for (Category cat : categories) {
            lookup.put(cat.getName().toLowerCase().trim(), cat);
        }
        return lookup;
    }
}