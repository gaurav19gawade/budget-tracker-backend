package com.budgettracker.service;

import com.budgettracker.model.Category;
import com.budgettracker.model.Transaction;
import com.budgettracker.repository.CategoryRepository;
import com.budgettracker.repository.TransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategorizationService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    // Keyword → canonical category name mapping
    // Keys are lowercase substrings to match against merchant name
    private static final Map<String, String> KEYWORD_RULES = new LinkedHashMap<>() {{
        // Restaurants / Fast Food
        put("chipotle",        "Restaurants");
        put("mcdonald",        "Restaurants");
        put("starbucks",       "Restaurants");
        put("subway",          "Restaurants");
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

        // Food Delivery
        put("doordash",        "Food Delivery");
        put("ubereats",        "Food Delivery");
        put("uber eats",       "Food Delivery");
        put("grubhub",         "Food Delivery");
        put("postmates",       "Food Delivery");
        put("instacart",       "Food Delivery");
        put("seamless",        "Food Delivery");
        put("caviar",          "Food Delivery");

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

        // Transport
        put("uber",            "Transport");
        put("lyft",            "Transport");
        put("metro",           "Transport");
        put("subway transit",  "Transport");
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

        // Gas
        put("shell",           "Gas");
        put("exxon",           "Gas");
        put("bp ",             "Gas");
        put("chevron",         "Gas");
        put("mobil",           "Gas");
        put("sunoco",          "Gas");
        put("speedway",        "Gas");
        put("casey",           "Gas");
        put("wawa",            "Gas");
        put("gas station",     "Gas");
        put("fuel",            "Gas");

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

        // Travel / Airlines
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

        // Health / Pharmacy
        put("cvs",             "Health");
        put("walgreens",       "Health");
        put("rite aid",        "Health");
        put("pharmacy",        "Health");
        put("medical",         "Health");
        put("clinic",          "Health");
        put("hospital",        "Health");
        put("dental",          "Health");
        put("vision",          "Health");
        put("dr ",             "Health");

        // Fitness
        put("gym",             "Fitness");
        put("planet fitness",  "Fitness");
        put("equinox",         "Fitness");
        put("crossfit",        "Fitness");
        put("peloton",         "Fitness");
        put("yoga",            "Fitness");
        put("fitness",         "Fitness");

        // Utilities / Bills
        put("at&t",            "Utilities");
        put("verizon",         "Utilities");
        put("t-mobile",        "Utilities");
        put("comcast",         "Utilities");
        put("xfinity",         "Utilities");
        put("spectrum",        "Utilities");
        put("electric",        "Utilities");
        put("water",           "Utilities");
        put("internet",        "Utilities");
        put("utility",         "Utilities");
        put("insurance",       "Utilities");

        // Coffee (after restaurants to not overlap)
        put("starbucks reserve","Restaurants");
        put("coffee",          "Restaurants");
        put("espresso",        "Restaurants");
    }};

    /**
     * Categorizes all transactions for a given user.
     * Overwrites existing category if a keyword match is found.
     * Called automatically after every Teller sync.
     */
    @Transactional
    public int categorizeForUser(Long userId) {
        List<Transaction> transactions = transactionRepository.findByUserId(userId);
        List<Category> userCategories  = categoryRepository.findByUserId(userId);

        if (userCategories.isEmpty()) {
            log.info("No categories found for user {} — skipping categorization", userId);
            return 0;
        }

        // Build a lookup: canonical name (lowercase) → Category entity
        Map<String, Category> categoryLookup = new HashMap<>();
        for (Category cat : userCategories) {
            categoryLookup.put(cat.getName().toLowerCase(), cat);
        }

        int matched = 0;
        for (Transaction tx : transactions) {
            if (tx.getMerchantName() == null) continue;

            String merchant = tx.getMerchantName().toLowerCase();
            Category found  = findCategory(merchant, categoryLookup);

            if (found != null && !found.equals(tx.getCategory())) {
                tx.setCategory(found);
                matched++;
            }
        }

        if (matched > 0) {
            transactionRepository.saveAll(transactions);
            log.info("Auto-categorized {} transactions for user {}", matched, userId);
        }

        return matched;
    }

    /**
     * Finds the best matching category for a merchant name.
     * Tries exact keyword rules first, then falls back to direct category name match.
     */
    private Category findCategory(String merchant, Map<String, Category> categoryLookup) {
        // 1. Try keyword rules
        for (Map.Entry<String, String> rule : KEYWORD_RULES.entrySet()) {
            if (merchant.contains(rule.getKey())) {
                Category cat = categoryLookup.get(rule.getValue().toLowerCase());
                if (cat != null) return cat;
            }
        }

        // 2. Fallback: check if merchant name directly contains a category name
        for (Map.Entry<String, Category> entry : categoryLookup.entrySet()) {
            if (merchant.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }
}

