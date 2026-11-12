package com.kadraa.novka.domain

data class DefaultCategory(
    val key: String,
    val en: String,
    val bs: String,
    val icon: String,
    val kind: CategoryKind,
    val parent: String? = null,
)

object DefaultCategories {
    /** The first eight are a colour-blind-checked chart palette; the rest extend it for extra groups. */
    val colours = listOf(
        0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100, 0xFFE87BA4, 0xFF008300, 0xFF6250D6, 0xFFE34948,
        0xFF8D6E63, 0xFF00838F, 0xFF7CB342, 0xFF5C6BC0, 0xFFAD1457, 0xFF78909C,
    )

    private fun group(key: String, en: String, bs: String, icon: String, vararg children: DefaultCategory) =
        listOf(DefaultCategory(key, en, bs, icon, CategoryKind.EXPENSE)) + children.map { it.copy(parent = key) }

    private fun e(key: String, en: String, bs: String, icon: String) = DefaultCategory(key, en, bs, icon, CategoryKind.EXPENSE)
    private fun i(key: String, en: String, bs: String, icon: String) = DefaultCategory(key, en, bs, icon, CategoryKind.INCOME)

    val all: List<DefaultCategory> = listOf(
        group(
            "food", "Food", "Hrana", "🍽️",
            e("groceries", "Groceries", "Namirnice", "🛒"),
            e("restaurants", "Restaurants", "Restorani", "🍝"),
            e("coffee", "Coffee", "Kafa", "☕"),
            e("fastfood", "Fast food", "Brza hrana", "🍔"),
            e("delivery", "Food delivery", "Dostava hrane", "🛵"),
            e("bakery", "Bakery", "Pekara", "🥐"),
            e("bars", "Bars and drinks", "Kafići i piće", "🍺"),
        ),
        group(
            "transport", "Transport", "Prevoz", "🚗",
            e("fuel", "Fuel", "Gorivo", "⛽"),
            e("public_transport", "Public transport", "Javni prevoz", "🚌"),
            e("taxi", "Taxi", "Taksi", "🚕"),
            e("parking", "Parking", "Parking", "🅿️"),
            e("car_maintenance", "Car maintenance", "Održavanje auta", "🔧"),
            e("car_registration", "Registration and insurance", "Registracija i osiguranje", "📋"),
            e("tolls", "Tolls", "Putarina", "🛣️"),
        ),
        group(
            "housing", "Housing", "Stanovanje", "🏠",
            e("rent", "Rent", "Kirija", "🔑"),
            e("mortgage", "Mortgage", "Stambeni kredit", "🏦"),
            e("home_maintenance", "Repairs", "Popravke", "🛠️"),
            e("furniture", "Furniture", "Namještaj", "🛋️"),
            e("household", "Household supplies", "Kućne potrepštine", "🧽"),
        ),
        group(
            "bills", "Bills", "Računi", "🧾",
            e("electricity", "Electricity", "Struja", "💡"),
            e("water", "Water", "Voda", "🚿"),
            e("heating", "Heating and gas", "Grijanje i plin", "🔥"),
            e("internet", "Internet", "Internet", "🌐"),
            e("phone", "Phone", "Telefon", "📱"),
            e("streaming", "TV and streaming", "TV i streaming", "📺"),
            e("subscriptions", "Subscriptions", "Pretplate", "🔁"),
        ),
        group(
            "health", "Health", "Zdravlje", "⚕️",
            e("pharmacy", "Pharmacy", "Apoteka", "💊"),
            e("doctor", "Doctor", "Ljekar", "🩺"),
            e("dentist", "Dentist", "Zubar", "🦷"),
            e("fitness", "Fitness", "Fitnes", "🏋️"),
            e("personal_care", "Personal care", "Lična njega", "💇"),
        ),
        group(
            "shopping", "Shopping", "Kupovina", "🛍️",
            e("clothes", "Clothes and shoes", "Odjeća i obuća", "👕"),
            e("electronics", "Electronics", "Elektronika", "💻"),
            e("cosmetics", "Cosmetics", "Kozmetika", "💄"),
            e("books", "Books", "Knjige", "📚"),
            e("hobby", "Hobbies", "Hobiji", "🎨"),
        ),
        group(
            "entertainment", "Entertainment", "Zabava", "🎬",
            e("games", "Games", "Igre", "🎮"),
            e("cinema", "Cinema and events", "Kino i događaji", "🎟️"),
            e("music", "Music", "Muzika", "🎵"),
            e("sports", "Sports", "Sport", "⚽"),
            e("nightlife", "Nightlife", "Izlasci", "🪩"),
            e("betting", "Betting", "Klađenje", "🎲"),
        ),
        group(
            "travel", "Travel", "Putovanja", "✈️",
            e("flights", "Flights", "Letovi", "🛫"),
            e("accommodation", "Accommodation", "Smještaj", "🏨"),
            e("activities", "Trips and activities", "Izleti i aktivnosti", "🗺️"),
        ),
        group(
            "education", "Education", "Obrazovanje", "🎓",
            e("tuition", "Tuition", "Školarina", "🏫"),
            e("courses", "Courses", "Kursevi", "🧑‍💻"),
            e("school_supplies", "School supplies", "Školski pribor", "✏️"),
        ),
        group(
            "family", "Family", "Porodica", "👨‍👩‍👧",
            e("childcare", "Childcare", "Čuvanje djece", "🧸"),
            e("kids", "Kids", "Djeca", "🍼"),
            e("allowance", "Pocket money", "Džeparac", "💶"),
        ),
        group(
            "pets", "Pets", "Ljubimci", "🐾",
            e("pet_food", "Pet food", "Hrana za ljubimce", "🦴"),
            e("vet", "Vet", "Veterinar", "🐕"),
        ),
        group(
            "gifts", "Gifts", "Pokloni", "🎁",
            e("gifts_given", "Gifts given", "Dati pokloni", "🎀"),
            e("donations", "Donations", "Donacije", "❤️"),
        ),
        group(
            "fees", "Fees and taxes", "Naknade i porezi", "🏛️",
            e("bank_fees", "Bank fees", "Bankarske naknade", "🏧"),
            e("taxes", "Taxes", "Porezi", "📑"),
            e("fines", "Fines", "Kazne", "⚠️"),
            e("insurance", "Insurance", "Osiguranje", "🛡️"),
        ),
        listOf(
            e("other_expense", "Other", "Ostalo", "❓"),
            i("salary", "Salary", "Plata", "💼"),
            i("freelance", "Freelance", "Honorar", "🧑‍💻"),
            i("gifts_received", "Gifts received", "Primljeni pokloni", "🎁"),
            i("refunds", "Refunds", "Povrati", "↩️"),
            i("interest", "Interest", "Kamata", "📈"),
            i("sales", "Sales", "Prodaja", "🏷️"),
            i("pension", "Pension", "Penzija", "👵"),
            i("scholarship", "Scholarship", "Stipendija", "🎓"),
            i("other_income", "Other income", "Ostali prihodi", "➕"),
        ),
    ).flatten()

    private val keywords: List<Pair<String, String>> = listOf(
        "groceries" to "bingo konzum mercator amko hose robot lidl kaufland spar tropic market supermarket fis crvena jabuka interex",
        "fastfood" to "mcdonald mcdonalds kfc burger",
        "delivery" to "glovo korpa wolt",
        "coffee" to "caffe cafe kafe coffee kafeterija",
        "restaurants" to "restoran restaurant pizza pizzeria cevabdzinica cevapi buregdzinica grill",
        "bakery" to "pekara bakery",
        "fuel" to "petrol ina hifa energopetrol gazprom shell omv nestro benzinska",
        "taxi" to "taxi taksi uber bolt",
        "parking" to "parking",
        "public_transport" to "centrotrans gras kentkart zeljeznice",
        "pharmacy" to "apoteka apoteke pharmacy ljekarna",
        "streaming" to "netflix hbo disney youtube",
        "music" to "spotify deezer",
        "games" to "steam playstation epic xbox nintendo riot roblox supercell",
        "phone" to "bh telecom mtel haloo eronet",
        "internet" to "telemach",
        "electricity" to "elektroprivreda epbih",
        "water" to "vodovod",
        "heating" to "toplane sarajevogas",
        "subscriptions" to "oracle google apple microsoft github openai anthropic adobe",
        "clothes" to "zara h&m waikiki yorker deichmann bershka pull&bear ccc",
        "cosmetics" to "dm drogerie cm kozmetika",
        "furniture" to "ikea jysk",
        "electronics" to "domod technoshop imtec",
        "cinema" to "cineplexx cinema kino",
        "accommodation" to "booking airbnb hotel hostel",
        "flights" to "ryanair wizz airlines lufthansa",
        "betting" to "maxbet mozzart premier bet",
        "shopping" to "amazon aliexpress temu ebay olx",
    ).flatMap { (key, words) -> words.split(' ').map { it to key } }

    /** The default-category key suggested by a normalised merchant name, if any. */
    fun keywordKey(normalisedMerchant: String): String? {
        val tokens = normalisedMerchant.split(' ')
        return keywords.firstOrNull { (word, _) -> word in tokens || (word.length >= 5 && tokens.any { it.startsWith(word) }) }?.second
    }
}
