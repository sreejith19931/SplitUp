package com.finance.splitup.data

import android.content.Context
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class CategoryResult(
    val category: String,
    val subCategory: String? = null,
    val secondaryCategory: String? = null,
    val confidence: Float = 0f,
    val isLearned: Boolean = false,
    val matchedKeyword: String? = null
)

object CategoryDetector {

    private const val PREFS_NAME = "category_detector_learned_prefs"
    private const val KEY_LEARNED_MAP = "learned_title_mappings"

    private val learnedUserMappings = ConcurrentHashMap<String, String>()
    @Volatile
    private var isInitialized = false

    fun init(context: Context?) {
        if (isInitialized || context == null) return
        synchronized(this) {
            if (isInitialized) return
            try {
                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val savedData = prefs.getString(KEY_LEARNED_MAP, null)
                if (!savedData.isNullOrBlank()) {
                    savedData.split(";").forEach { pair ->
                        val parts = pair.split("=")
                        if (parts.size == 2) {
                            val key = try { java.net.URLDecoder.decode(parts[0].trim(), "UTF-8") } catch (_: Exception) { parts[0].trim() }
                            val cat = try { java.net.URLDecoder.decode(parts[1].trim(), "UTF-8") } catch (_: Exception) { parts[1].trim() }
                            if (key.isNotEmpty() && cat.isNotEmpty()) {
                                learnedUserMappings[key] = cat
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CategoryDetector", "Failed to load learned mappings", e)
            }
            isInitialized = true
        }
    }

    fun clearLearnedMappingsForTesting() {
        learnedUserMappings.clear()
    }

    fun learnCategory(title: String, category: String, context: Context? = null) {
        if (title.isBlank() || category.isBlank() || category == ExpenseCategories.OTHER || category == ExpenseCategories.SETTLEMENT) {
            return
        }

        val normalized = normalizeTitle(title)
        if (normalized.length < 2) return

        learnedUserMappings[normalized] = category

        val tokens = tokenize(title)
        if (tokens.size in 1..3) {
            val shortPhrase = tokens.joinToString(" ")
            learnedUserMappings[shortPhrase] = category
        }

        tokens.forEach { token ->
            if (token.length >= 3 && !STOP_WORDS.contains(token)) {
                learnedUserMappings[token] = category
            }
        }

        try {
            context?.applicationContext?.let { ctx ->
                persistLearnedMappings(ctx)
            }
        } catch (_: Exception) {}
    }

    fun seedHistory(expenses: List<Expense>, groupExpenses: List<GroupExpense> = emptyList()) {
        expenses.forEach { exp ->
            if (exp.title.isNotBlank() && exp.category.isNotBlank() && exp.category != ExpenseCategories.OTHER) {
                learnedUserMappings[normalizeTitle(exp.title)] = exp.category
            }
        }
        groupExpenses.forEach { exp ->
            if (exp.title.isNotBlank() && exp.category.isNotBlank() && exp.category != ExpenseCategories.OTHER) {
                learnedUserMappings[normalizeTitle(exp.title)] = exp.category
            }
        }
    }

    private fun persistLearnedMappings(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val serialized = learnedUserMappings.entries.take(500).joinToString(";") {
                val encKey = java.net.URLEncoder.encode(it.key, "UTF-8")
                val encVal = java.net.URLEncoder.encode(it.value, "UTF-8")
                "$encKey=$encVal"
            }
            prefs.edit().putString(KEY_LEARNED_MAP, serialized).apply()
        } catch (e: Exception) {
            android.util.Log.e("CategoryDetector", "Failed to persist learned mappings", e)
        }
    }

    fun detectCategory(title: String, timestamp: Long = System.currentTimeMillis(), context: Context? = null): CategoryResult {
        if (title.isBlank()) {
            return CategoryResult(ExpenseCategories.OTHER)
        }

        try {
            context?.let { init(it) }
        } catch (_: Exception) {}

        val normalizedInput = normalizeTitle(title)
        val tokens = tokenize(title)

        // 1. Check direct learned user mappings
        learnedUserMappings[normalizedInput]?.let { learnedCat ->
            return CategoryResult(
                category = learnedCat,
                subCategory = inferSubCategory(learnedCat, normalizedInput),
                secondaryCategory = null,
                confidence = 1.0f,
                isLearned = true,
                matchedKeyword = normalizedInput
            )
        }

        for ((learnedPhrase, cat) in learnedUserMappings) {
            if (learnedPhrase.length >= 3 && normalizedInput.contains(learnedPhrase)) {
                return CategoryResult(
                    category = cat,
                    subCategory = inferSubCategory(cat, normalizedInput),
                    secondaryCategory = null,
                    confidence = 0.95f,
                    isLearned = true,
                    matchedKeyword = learnedPhrase
                )
            }
        }

        // 2. Multilingual / Multi-word phrase matching dictionary
        var bestPhraseMatchCategory: String? = null
        var bestPhraseMatchKeyword: String? = null
        var bestPhraseLength = 0

        for ((category, phrases) in PHRASE_DICTIONARY) {
            for (phrase in phrases) {
                if (normalizedInput.contains(phrase)) {
                    if (phrase.length > bestPhraseLength) {
                        bestPhraseLength = phrase.length
                        bestPhraseMatchCategory = category
                        bestPhraseMatchKeyword = phrase
                    }
                }
            }
        }

        // 3. Token-based, Stem-based & Fuzzy Weighted Scoring
        val scores = mutableMapOf<String, Float>()
        val matchedKeywords = mutableMapOf<String, String>()

        ExpenseCategories.all.forEach { cat ->
            scores[cat] = 0f
        }

        bestPhraseMatchCategory?.let { phraseCat ->
            scores[phraseCat] = (scores[phraseCat] ?: 0f) + 40f
            matchedKeywords[phraseCat] = bestPhraseMatchKeyword ?: ""
        }

        for (token in tokens) {
            if (STOP_WORDS.contains(token)) continue

            for ((category, keywords) in TOKEN_DICTIONARY) {
                for (keyword in keywords) {
                    when {
                        token == keyword -> {
                            scores[category] = (scores[category] ?: 0f) + 25f
                            if (!matchedKeywords.containsKey(category)) matchedKeywords[category] = token
                        }
                        token.startsWith(keyword) && keyword.length >= 4 -> {
                            scores[category] = (scores[category] ?: 0f) + 15f
                            if (!matchedKeywords.containsKey(category)) matchedKeywords[category] = keyword
                        }
                        keyword.length >= 5 && token.contains(keyword) -> {
                            scores[category] = (scores[category] ?: 0f) + 12f
                            if (!matchedKeywords.containsKey(category)) matchedKeywords[category] = keyword
                        }
                        token.length >= 6 && keyword.length >= 6 && levenshteinDistance(token, keyword) <= 1 -> {
                            scores[category] = (scores[category] ?: 0f) + 10f
                            if (!matchedKeywords.containsKey(category)) matchedKeywords[category] = keyword
                        }
                    }
                }
            }
        }

        // 4. Temporal Context Prior (Time of day weighting)
        try {
            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            if (hour in 6..10) {
                scores[ExpenseCategories.FOOD] = (scores[ExpenseCategories.FOOD] ?: 0f) + 4f
                scores[ExpenseCategories.GROCERIES] = (scores[ExpenseCategories.GROCERIES] ?: 0f) + 2f
            } else if (hour in 18..22) {
                scores[ExpenseCategories.FOOD] = (scores[ExpenseCategories.FOOD] ?: 0f) + 3f
                scores[ExpenseCategories.ENTERTAINMENT] = (scores[ExpenseCategories.ENTERTAINMENT] ?: 0f) + 3f
            }
        } catch (_: Exception) {}

        val sortedCategories = scores.entries
            .filter { it.value > 0f && it.key != ExpenseCategories.OTHER }
            .sortedByDescending { it.value }

        if (sortedCategories.isEmpty()) {
            return CategoryResult(
                category = ExpenseCategories.OTHER,
                confidence = 0f
            )
        }

        val primaryEntry = sortedCategories[0]
        val primaryCat = primaryEntry.key
        val primaryScore = primaryEntry.value
        val primaryKeyword = matchedKeywords[primaryCat]

        val secondaryCat: String? = if (sortedCategories.size > 1) {
            val runnerUp = sortedCategories[1]
            if (runnerUp.value >= 15f && runnerUp.value >= primaryScore * 0.4f && runnerUp.key != primaryCat) {
                runnerUp.key
            } else null
        } else null

        val confidence = (primaryScore / 50f).coerceIn(0.1f, 0.99f)
        val subCategory = inferSubCategory(primaryCat, normalizedInput)

        return CategoryResult(
            category = primaryCat,
            subCategory = subCategory,
            secondaryCategory = secondaryCat,
            confidence = confidence,
            isLearned = false,
            matchedKeyword = primaryKeyword
        )
    }

    fun detectCategory(title: String, context: Context? = null): CategoryResult {
        return detectCategory(title, System.currentTimeMillis(), context)
    }

    fun detectCategoryFromTitle(title: String, context: Context? = null): String {
        return detectCategory(title, context).category
    }

    private fun inferSubCategory(category: String, text: String): String? {
        val subList = ExpenseCategories.subCategoriesMap[category] ?: return null
        for (sub in subList) {
            val subKeywords = sub.lowercase(Locale.ROOT).split(Regex("[\\s&/]+"))
            if (subKeywords.any { it.length > 2 && text.contains(it) }) {
                return sub
            }
        }
        return subList.firstOrNull()
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val memo = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) memo[i][0] = i
        for (j in 0..s2.length) memo[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                memo[i][j] = minOf(
                    memo[i - 1][j] + 1,
                    memo[i][j - 1] + 1,
                    memo[i - 1][j - 1] + cost
                )
            }
        }
        return memo[s1.length][s2.length]
    }

    private fun normalizeTitle(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("(sq\\s*\\*|tst\\s*\\*|paypal\\s*\\*|uber\\s*\\*)"), " ")
            .replace(Regex("#\\d+"), " ")
            .replace(Regex("\\b[0-9]{5}(-[0-9]{4})?\\b"), " ")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun tokenize(text: String): List<String> {
        return normalizeTitle(text).split(" ").filter { it.isNotBlank() && it.length > 1 }
    }

    private val STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "for", "with", "at", "to", "from", "in", "on", "of", "by",
        "my", "our", "your", "his", "her", "their", "is", "was", "be", "been", "paid", "spent",
        "buy", "bought", "got", "get", "cost", "fee", "total", "payment", "amount", "charge",
        "card", "rs", "usd", "eur", "gbp", "inr", "thanks", "thank", "you", "item", "purchase",
        "order", "no", "id", "ref", "txn", "receipt", "s", "t", "d", "m", "re", "ll", "ve"
    )

    private val PHRASE_DICTIONARY = mapOf(
        ExpenseCategories.FOOD to listOf(
            "taco bell", "burger king", "dairy queen",
            "panda express", "jack in the box", "in n out", "chick fil a", "krispy kreme",
            "costa coffee", "peet s coffee", "peets coffee", "dunkin donuts", "dunkin donut",
            "domino s pizza", "dominos pizza", "pizza hut", "papa johns", "papa john", "five guys",
            "shake shack", "uber eats", "ubereats", "door dash", "doordash", "grub hub", "grubhub",
            "just eat", "justeat", "food panda", "foodpanda", "swiggy", "zomato", "deliveroo",
            "instacart", "gopuff", "blinkit", "zepto", "food truck", "ice cream", "boba tea",
            "bubble tea", "hot pot", "bbq grill", "coffee shop"
        ),
        ExpenseCategories.GROCERIES to listOf(
            "trader joe", "trader joes", "whole foods", "grocery store", "super market", "farmers market", "organic market", "produce stand",
            "costco wholesale", "sam s club", "sams club", "aldi supermarket", "lidl grocery",
            "kroger market", "safeway store", "publix supermarket", "heb grocery", "wegmans food",
            "sprouts farmers", "fresh market", "whole foods market"
        ),
        ExpenseCategories.TRANSPORT to listOf(
            "uber ride", "lyft ride", "gas station", "petrol pump", "car wash", "parking meter",
            "toll road", "toll gate", "ez pass", "express lane", "tesla supercharger", "ev charging",
            "ev charger", "rental car", "hertz rent", "avis rent", "enterprise rent", "airline ticket",
            "flight ticket", "train ticket", "bus ticket", "metro card", "oyster card", "transit pass",
            "air fare", "plane ticket"
        ),
        ExpenseCategories.SHOPPING to listOf(
            "apple store", "best buy", "micro center", "home depot", "bed bath", "urban outfitters",
            "victoria secret", "foot locker", "target shop", "amazon prime", "ebay shop",
            "online order", "department store", "hardware store", "gift shop", "thrift store"
        ),
        ExpenseCategories.BILLS to listOf(
            "electric bill", "electricity bill", "water bill", "gas bill", "power bill", "heating bill",
            "trash bill", "waste management", "internet bill", "phone bill", "mobile bill",
            "cellular bill", "broadband bill", "wifi bill", "credit card bill", "house rent",
            "room rent", "property tax", "hoa fee", "tuition fee", "school fee", "college tuition",
            "life insurance", "health insurance", "car insurance", "auto insurance", "self storage",
            "dry cleaner", "dry cleaning"
        ),
        ExpenseCategories.ENTERTAINMENT to listOf(
            "movie ticket", "cinema ticket", "concert ticket", "broadway show", "amusement park",
            "theme park", "planet fitness", "gold gym", "playstation network", "xbox live",
            "nintendo eshop", "youtube premium", "spotify premium", "apple music", "prime video",
            "disney plus", "hbo max", "golf course", "bowling alley", "night club", "comedy club"
        ),
        ExpenseCategories.HEALTH to listOf(
            "cvs pharmacy", "walgreens pharmacy", "rite aid", "hospital visit", "doctor appointment",
            "dental clinic", "eye doctor", "optometrist clinic", "urgent care", "physical therapy"
        ),
        ExpenseCategories.TRAVEL to listOf(
            "flight booking", "hotel booking", "airbnb stay", "booking com", "expedia travel",
            "agoda booking", "tripadvisor tour", "car rental", "cruise booking"
        ),
        ExpenseCategories.EDUCATION to listOf(
            "tuition fee", "school tuition", "university fee", "college tuition", "online course",
            "udemy course", "coursera subscription", "textbook purchase"
        ),
        ExpenseCategories.INCOME to listOf(
            "monthly salary", "pay day", "payday", "pay check", "paycheck", "direct deposit",
            "interest income", "tax refund", "cash back", "cashback", "side hustle", "freelance work"
        )
    )

    private val TOKEN_DICTIONARY = mapOf(
        ExpenseCategories.FOOD to listOf(
            "mcdonalds", "mcdonald", "starbucks", "subway", "kfc", "dominos", "chipotle",
            "bakery", "butcher", "deli", "bistro", "restaurant", "cafe", "coffee", "latte",
            "espresso", "cappuccino", "dinner", "lunch", "breakfast", "brunch", "snack", "snacks",
            "pizza", "burger", "burgers", "sushi", "ramen", "tacos", "taco", "burrito", "noodle",
            "noodles", "pasta", "buffet", "bar", "pub", "brewery", "winery", "beer", "wine",
            "cocktail", "cocktails", "drinks", "liquor", "food", "eats", "dining", "eatery",
            "steakhouse", "seafood", "donut", "donuts", "pastry", "bagel", "boba", "swiggy",
            "zomato", "doordash", "deliveroo", "khana", "chai", "nashta", "dhaba",
            "essen", "kaffee", "bäckerei", "mittagessen", "abendessen", "frühstück", "bier", "wein"
        ),
        ExpenseCategories.GROCERIES to listOf(
            "grocery", "groceries", "supermarket", "walmart", "costco", "aldi", "lidl", "kroger",
            "safeway", "publix", "wegmans", "sprouts", "wholefoods", "traderjoes", "tesco", "carrefour",
            "woolworths", "coles", "sainsbury", "asda", "waitrose", "heb", "meijer", "produce",
            "sabzi", "doodh", "lebensmittel", "supermarkt"
        ),
        ExpenseCategories.TRANSPORT to listOf(
            "uber", "lyft", "grab", "bolt", "didi", "ola", "waymo", "cab", "cabs", "taxi", "taxis",
            "rickshaw", "metro", "subway", "train", "bus", "buses", "tram", "rail", "railway",
            "transit", "amtrak", "eurostar", "oyster", "clipper", "gas", "gasoline", "petrol",
            "fuel", "diesel", "parking", "parkometer", "toll", "tolls", "turnpike", "flight",
            "flights", "airline", "airfare", "baggage", "ryanair", "easyjet", "delta", "united",
            "lufthansa", "emirates", "southwest", "jetblue", "spirit", "carwash", "auto", "vehicle",
            "commute", "hertz", "avis", "enterprise", "sixt", "safhar", "yatra",
            "bahn", "zug", "benzin", "parken", "flug", "fahrkarte"
        ),
        ExpenseCategories.SHOPPING to listOf(
            "amazon", "apple", "iphone", "ipad", "macbook", "airpods", "laptop", "electronics",
            "gadget", "samsung", "sony", "bose", "dell", "hp", "lenovo", "newegg", "ebay", "etsy",
            "aliexpress", "zara", "nike", "adidas", "uniqlo", "shein", "asos", "nordstrom", "macys",
            "gap", "oldnavy", "lululemon", "sephora", "ulta", "ikea", "wayfair", "clothes",
            "clothing", "shirt", "shirts", "pants", "shoes", "sneakers", "dress", "jacket", "coat",
            "fashion", "apparel", "boutique", "mall", "shopping", "store", "shop", "merchandise",
            "jewelry", "watch", "furniture", "decor", "hardware", "bestbuy", "kapde", "joota", "dukaan",
            "kleidung", "schuhe", "einkaufen", "geschäft"
        ),
        ExpenseCategories.BILLS to listOf(
            "rent", "mortgage", "lease", "electricity", "electric", "power", "hydro", "water",
            "trash", "garbage", "sewage", "utility", "utilities", "heating", "solar", "internet",
            "broadband", "wifi", "telecom", "verizon", "att", "tmobile", "vodafone", "orange",
            "comcast", "xfinity", "spectrum", "centurylink", "cox", "insurance", "geico",
            "progressive", "allstate", "tuition", "tax", "taxes", "loan", "debt",
            "interest", "bill", "bills", "maintenance", "repair", "repairs", "laundry",
            "laundromat", "cleaner", "cleaners", "plumber", "electrician", "storage",
            "bijli", "kiraya", "recharge", "miete", "strom", "wasser", "versicherung", "rechnung"
        ),
        ExpenseCategories.ENTERTAINMENT to listOf(
            "netflix", "spotify", "hulu", "disney", "hbo", "max", "youtube", "twitch", "audible",
            "kindle", "crunchyroll", "soundcloud", "steam", "playstation", "psn", "xbox",
            "nintendo", "roblox", "game", "games", "gaming", "arcade", "cinema", "movie",
            "movies", "theater", "theatre", "concert", "ticket", "tickets", "ticketmaster",
            "eventbrite", "event", "show", "party", "club", "nightclub", "bowling", "golf", "ski",
            "resort", "museum", "gallery", "zoo", "aquarium", "gym", "fitness", "equinox", "ymca",
            "yoga", "pilates", "crossfit", "festival", "stadium", "kino", "film", "konzert", "spiel"
        ),
        ExpenseCategories.HEALTH to listOf(
            "pharmacy", "medicine", "doctor", "hospital", "clinic", "dental", "dentist", "optometrist",
            "prescription", "cvs", "walgreens", "riteaid", "healthcare", "therapy", "vitamin", "vitamins",
            "apotheke", "arzt", "krankenhaus"
        ),
        ExpenseCategories.TRAVEL to listOf(
            "hotel", "airbnb", "hostel", "resort", "booking", "expedia", "agoda", "tripadvisor",
            "vacation", "luggage", "passport", "visa", "flughafen", "übernachtung"
        ),
        ExpenseCategories.EDUCATION to listOf(
            "tuition", "school", "university", "college", "course", "courses", "udemy", "coursera",
            "edx", "book", "books", "textbook", "textbooks", "supplies", "stationery", "exam",
            "schüle", "universität", "buch"
        ),
        ExpenseCategories.INCOME to listOf(
            "salary", "payroll", "paycheck", "stipend", "dividend", "dividends", "yield", "income",
            "refund", "cashback", "bonus", "commission", "grant", "scholarship", "pension",
            "allowance", "deposit", "freelance", "reimbursement", "paisa", "kamai", "tankha",
            "gehalt", "lohn", "einkommen", "rückerstattung"
        )
    )
}
