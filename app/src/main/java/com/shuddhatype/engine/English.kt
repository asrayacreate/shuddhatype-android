package com.shuddhatype.engine

/**
 * English words a Nepali typist reaches for without switching off the नेपाली
 * page: download, update, meeting, invoice.
 *
 * None of these have a Devanagari spelling the transliterator could usefully
 * guess, so before this list they came back as दोव्न्लोअड — letters that are
 * technically correct and useless to everyone.
 *
 * Two rules keep this list from stealing real Nepali words:
 *
 *   1. [MIN_LEN] — nothing shorter than four letters. Most collisions are
 *      short: man/मन, sun/सुन, ban/बन, gun/गुन, din/दिन.
 *   2. Four-letter words that are also common romanisations stay out even when
 *      they are perfectly good English — "than" is थान (a piece of cloth) far
 *      more often than it is a comparison, and "sale" is साले.
 *
 * A word that is genuinely both stays out. The Roman spelling is still offered
 * as a second suggestion for anything the lexicon does not recognise, so the
 * cost of leaving a word off this list is one extra tap, while the cost of a
 * wrong entry is a Nepali word the keyboard can no longer write.
 */
object English {

    fun contains(word: String): Boolean =
        word.length >= MIN_LEN && WORDS.contains(word)

    private const val MIN_LEN = 4

    private val WORDS: Set<String> = hashSetOf(
        // phone, apps, internet
        "download", "downloads", "upload", "update", "updates", "upgrade",
        "install", "uninstall", "mobile", "computer", "laptop", "desktop",
        "keyboard", "mouse", "screen", "display", "battery", "charger",
        "internet", "online", "offline", "website", "email", "gmail",
        "password", "username", "account", "login", "logout", "signup",
        "google", "facebook", "instagram", "youtube", "whatsapp", "viber",
        "tiktok", "messenger", "message", "notification", "setting",
        "settings", "option", "version", "software", "hardware", "android",
        "iphone", "camera", "photo", "video", "audio", "music", "file",
        "files", "folder", "delete", "cancel", "submit", "search", "browser",
        "chrome", "network", "wifi", "data", "storage", "memory", "backup",
        "restore", "share", "link", "click", "scroll", "swipe", "apps",
        "application", "program", "system", "server", "cloud", "digital",
        "device", "printer", "scanner", "bluetooth", "hotspot", "recharge",
        "balance", "code", "error", "crash", "loading", "refresh", "reload",
        "connect", "disconnect", "block", "unblock", "report", "feedback",
        "review", "rating", "comment", "post", "story", "live", "follow",
        "unfollow", "chat", "call", "contact", "number", "theme", "themes",
        "font", "size", "colour", "color", "dark", "light", "mode", "screenshot",

        // office and work
        "office", "meeting", "project", "budget", "invoice", "payment",
        "bill", "receipt", "order", "delivery", "customer", "client",
        "service", "company", "business", "market", "price", "cost",
        "profit", "loss", "salary", "bonus", "contract", "agreement",
        "document", "print", "copy", "paste", "sign", "stamp", "approve",
        "reject", "pending", "complete", "deadline", "schedule",
        "appointment", "manager", "director", "staff", "team", "work",
        "task", "plan", "target", "quality", "quantity", "sample", "model",
        "design", "drawing", "detail", "details", "final", "draft", "list",

        // construction, the day job
        "site", "building", "construction", "material", "cement", "steel",
        "brick", "sand", "paint", "tile", "wire", "pipe", "labour", "labor",
        "contractor", "engineer", "architect", "estimate", "quotation",
        "measurement", "area", "length", "width", "height", "floor", "roof",
        "wall", "door", "window", "kitchen", "bathroom", "garden", "parking",
        "column", "beam", "slab", "foundation", "plaster", "marble",
        "aluminium", "glass", "shutter", "gate", "boundary", "prefab",

        // money
        "bank", "cash", "card", "credit", "debit", "loan", "interest",
        "deposit", "withdraw", "transfer", "amount", "total", "discount",
        "rate", "exchange", "wallet", "esewa", "khalti", "fonepay", "refund",
        "advance", "due", "paid", "unpaid", "cheque", "check",

        // school, health, travel
        "school", "college", "university", "class", "exam", "result",
        "study", "book", "notebook", "paper", "uniform", "admission",
        "scholarship", "student", "teacher", "hospital", "doctor", "nurse",
        "medicine", "tablet", "health", "checkup", "blood", "pressure",
        "sugar", "fever", "cough", "cold", "pain", "treatment", "surgery",
        "clinic", "pharmacy", "vaccine", "injection", "patient", "emergency",
        "ambulance", "airport", "flight", "ticket", "passport", "visa",
        "hotel", "room", "booking", "travel", "tour", "trip", "bike",
        "road", "traffic", "station", "address", "location", "distance",
        "driver", "license", "insurance", "petrol", "diesel", "garage",

        // everyday
        "hello", "thanks", "thank", "please", "sorry", "welcome", "good",
        "morning", "evening", "night", "today", "tomorrow", "yesterday",
        "week", "month", "year", "time", "date", "hour", "minute", "second",
        "holiday", "festival", "birthday", "happy", "love", "life", "family",
        "friend", "brother", "sister", "mother", "father", "husband", "wife",
        "child", "children", "people", "person", "name", "home", "house",
        "shop", "market", "food", "water", "phone", "number", "photo",

        // common English filler that shows up mid-sentence
        "about", "after", "again", "also", "always", "another", "answer",
        "anything", "because", "before", "better", "best", "between",
        "change", "choose", "close", "come", "could", "create", "done",
        "during", "early", "enough", "every", "example", "first", "from",
        "give", "great", "group", "help", "here", "however", "important",
        "information", "inside", "just", "keep", "know", "large", "last",
        "later", "learn", "leave", "level", "little", "look", "made",
        "make", "many", "matter", "maybe", "mean", "might", "more", "most",
        "move", "much", "must", "need", "never", "next", "nice", "offer",
        "often", "only", "open", "other", "over", "part", "perhaps",
        "place", "point", "possible", "power", "problem", "provide",
        "question", "quick", "ready", "really", "reason", "remember",
        "right", "same", "seem", "send", "several", "should", "show",
        "side", "simple", "since", "small", "some", "sometimes", "soon",
        "sound", "start", "still", "stop", "such", "sure", "take", "talk",
        "tell", "that", "their", "them", "then", "there", "these", "they",
        "thing", "think", "this", "those", "though", "through", "together",
        "told", "took", "toward", "true", "turn", "under", "until", "upon",
        "used", "using", "very", "want", "watch", "well", "went", "were",
        "what", "when", "where", "which", "while", "white", "whole",
        "will", "wish", "with", "within", "without", "word", "world",
        "would", "write", "wrong", "your", "late", "long", "hard", "easy",
        "free", "full", "half", "line", "note", "case", "form", "page",
        "part", "past", "real", "rest", "safe", "save", "seen", "sent",
        "stay", "step", "test", "text", "type", "view",
        "wait", "walk", "warm", "wear", "back",
        "each", "even", "ever", "face", "fact", "fall", "fast",
        "feel", "find", "fine", "fire", "five", "four", "game", "girl",
        "glad", "goal", "hand", "head", "hear", "high", "hold", "hope",
        "idea", "join", "kind", "land", "lead", "left", "less", "lost",
        "loud", "luck", "main", "mark", "mind", "miss", "near", "news",
        "once", "pass", "past", "plus", "poor", "pull", "push", "rain",
        "read", "rich", "ride", "ring", "rise", "risk", "role", "room",
        "rule", "seat", "sell", "shot", "shut", "sick",
        "skin", "slow", "soft", "sort", "star",
        "tall", "term", "thin", "tire", "tool", "town",
        "tree", "trip", "true", "unit", "used", "user", "wide", "wind",
        "wood", "yard", "young", "youth"
    )
}
