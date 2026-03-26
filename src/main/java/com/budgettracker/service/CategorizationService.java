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
        // Food Delivery (before Restaurants — doordash/ubereats are delivery, not restaurants)
        put("doordash",        "Food Delivery");
        put("uber eats",       "Food Delivery");
        put("ubereats",        "Food Delivery");
        put("grubhub",         "Food Delivery");
        put("postmates",       "Food Delivery");
        put("instacart",       "Food Delivery");
        put("seamless",        "Food Delivery");
        put("caviar",          "Food Delivery");

        // Restaurants / Fast Food
        put("chipotle",        "Restaurants");
        put("mcdonald",        "Restaurants");
        put("starbucks",       "Restaurants");
        put("dunkin",          "Restaurants");
        put("taco bell",       "Restaurants");
        put("burger king",     "Restaurants");
        put("wendy",           "Restaurants");
        put("chick-fil-a",     "Restaurants");
        put("domino",          "Restaurants");
        put("pizza hut",       "Restaurants");
        put("panera",          "Restaurants");
        put("olive garden",    "Restaurants");
        put("applebee",        "Restaurants");
        put("cheesecake",      "Restaurants");
        put("ihop",            "Restaurants");
        put("denny",           "Restaurants");
        put("five guys",       "Restaurants");
        put("shake shack",     "Restaurants");
        put("in-n-out",        "Restaurants");
        put("restaurant",      "Restaurants");
        put("grill",           "Restaurants");
        put("bistro",          "Restaurants");
        put("cafe",            "Restaurants");
        put("diner",           "Restaurants");
        put("eatery",          "Restaurants");
        put("coffee",          "Restaurants");
        put("espresso",        "Restaurants");

        // Groceries
        put("whole foods",     "Groceries");
        put("trader joe",      "Groceries");
        put("kroger",          "Groceries");
        put("safeway",         "Groceries");
        put("publix",          "Groceries");
        put("aldi",            "Groceries");
        put("costco",          "Groceries");
        put("sam's club",      "Groceries");
        put("walmart",         "Groceries");
        put("target",          "Groceries");
        put("sprouts",         "Groceries");
        put("wegmans",         "Groceries");
        put("stop & shop",     "Groceries");
        put("giant",           "Groceries");
        put("market",          "Groceries");
        put("grocery",         "Groceries");
        put("supermarket",     "Groceries");

        // Transport (uber before ubereats to avoid conflict — ubereats matched above)
        put("lyft",            "Transport");
        put("uber",            "Transport");
        put("metro",           "Transport");
        put("mta",             "Transport");
        put("transit",         "Transport");
        put("parking",         "Transport");
        put("toll",            "Transport");
        put("zipcar",          "Transport");
        put("enterprise",      "Transport");
        put("hertz",           "Transport");
        put("avis",            "Transport");
        put("amtrak",          "Transport");
        put("greyhound",       "Transport");

        // Gas (fixed: "bp" without trailing space, matched as whole word via normalization)
        put("shell",           "Gas");
        put("exxon",           "Gas");
        put("bp",              "Gas");
        put("chevron",         "Gas");
        put("mobil",           "Gas");
        put("sunoco",          "Gas");
        put("speedway",        "Gas");
        put("casey",           "Gas");
        put("wawa",            "Gas");
        put("gas station",     "Gas");
        put("fuel",            "Gas");

        // Credit Card Payments only — deliberately narrow to avoid catching
        // Zelle, ACH transfers, or other legitimate expense payments.
        put("automatic payment",     "Credit Card Payment");  // bank autopay confirmation
        put("autopay",               "Credit Card Payment");  // explicit autopay
        put("payment thank you",     "Credit Card Payment");  // credit card confirmation msg
        put("credit card payment",   "Credit Card Payment");  // explicit label
        put("minimum payment",       "Credit Card Payment");  // min payment on card
        put("chase credit crd",      "Credit Card Payment");  // Chase-specific
        put("amex epayment",         "Credit Card Payment");  // Amex-specific
        put("discover e-payment",    "Credit Card Payment");  // Discover-specific
        put("citi autopay",          "Credit Card Payment");  // Citi-specific
        put("capital one payment",   "Credit Card Payment");  // CapOne-specific

        // Shopping
        put("amazon",          "Shopping");
        put("ebay",            "Shopping");
        put("etsy",            "Shopping");
        put("best buy",        "Shopping");
        put("apple store",     "Shopping");
        put("ikea",            "Shopping");
        put("h&m",             "Shopping");
        put("zara",            "Shopping");
        put("gap",             "Shopping");
        put("old navy",        "Shopping");
        put("macy",            "Shopping");
        put("nordstrom",       "Shopping");
        put("tj maxx",         "Shopping");
        put("marshalls",       "Shopping");
        put("ross",            "Shopping");
        put("nike",            "Shopping");
        put("adidas",          "Shopping");

        // Entertainment
        put("netflix",         "Entertainment");
        put("spotify",         "Entertainment");
        put("hulu",            "Entertainment");
        put("disney+",         "Entertainment");
        put("hbo",             "Entertainment");
        put("apple tv",        "Entertainment");
        put("youtube",         "Entertainment");
        put("twitch",          "Entertainment");
        put("steam",           "Entertainment");
        put("playstation",     "Entertainment");
        put("xbox",            "Entertainment");
        put("amc",             "Entertainment");
        put("regal",           "Entertainment");
        put("cinema",          "Entertainment");
        put("theater",         "Entertainment");
        put("concert",         "Entertainment");
        put("ticketmaster",    "Entertainment");
        put("stubhub",         "Entertainment");

        // Travel
        put("airlines",        "Travel");
        put("united",          "Travel");
        put("delta",           "Travel");
        put("american air",    "Travel");
        put("southwest",       "Travel");
        put("jetblue",         "Travel");
        put("spirit",          "Travel");
        put("frontier",        "Travel");
        put("airbnb",          "Travel");
        put("expedia",         "Travel");
        put("booking.com",     "Travel");
        put("marriott",        "Travel");
        put("hilton",          "Travel");
        put("hyatt",           "Travel");
        put("holiday inn",     "Travel");

        // Health
        put("cvs",             "Health");
        put("walgreens",       "Health");
        put("rite aid",        "Health");
        put("pharmacy",        "Health");
        put("medical",         "Health");
        put("clinic",          "Health");
        put("hospital",        "Health");
        put("dental",          "Health");
        put("vision",          "Health");

        // Fitness
        put("gym",             "Fitness");
        put("planet fitness",  "Fitness");
        put("equinox",         "Fitness");
        put("crossfit",        "Fitness");
        put("peloton",         "Fitness");
        put("yoga",            "Fitness");
        put("fitness",         "Fitness");

        // Utilities
        put("at&t",            "Utilities");
        put("verizon",         "Utilities");
        put("t-mobile",        "Utilities");
        put("comcast",         "Utilities");
        put("xfinity",         "Utilities");
        put("spectrum",        "Utilities");
        put("electric",        "Utilities");
        put("internet",        "Utilities");
        put("utility",         "Utilities");
        put("insurance",       "Utilities");
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