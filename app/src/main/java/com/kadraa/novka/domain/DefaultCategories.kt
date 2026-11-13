package com.kadraa.novka.domain

data class DefaultCategory(
    val key: String,
    val en: String,
    val bs: String,
    /** Key of a Material icon; see `categoryIcons` in `ui/Icons.kt`. */
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
            "food", "Food", "Hrana", "restaurant",
            e("groceries", "Groceries", "Namirnice", "shopping_cart"),
            e("restaurants", "Restaurants", "Restorani", "restaurant_menu"),
            e("coffee", "Coffee", "Kafa", "local_cafe"),
            e("fastfood", "Fast food", "Brza hrana", "fastfood"),
            e("delivery", "Food delivery", "Dostava hrane", "delivery_dining"),
            e("bakery", "Bakery", "Pekara", "bakery_dining"),
            e("bars", "Bars and drinks", "Kafići i piće", "local_bar"),
        ),
        group(
            "transport", "Transport", "Prevoz", "directions_car",
            e("fuel", "Fuel", "Gorivo", "local_gas_station"),
            e("public_transport", "Public transport", "Javni prevoz", "directions_bus"),
            e("taxi", "Taxi", "Taksi", "local_taxi"),
            e("parking", "Parking", "Parking", "local_parking"),
            e("car_maintenance", "Car maintenance", "Održavanje auta", "build"),
            e("car_registration", "Registration and insurance", "Registracija i osiguranje", "assignment"),
            e("tolls", "Tolls", "Putarina", "toll"),
        ),
        group(
            "housing", "Housing", "Stanovanje", "home",
            e("rent", "Rent", "Kirija", "key"),
            e("mortgage", "Mortgage", "Stambeni kredit", "home_work"),
            e("home_maintenance", "Repairs", "Popravke", "handyman"),
            e("furniture", "Furniture", "Namještaj", "chair"),
            e("household", "Household supplies", "Kućne potrepštine", "cleaning_services"),
        ),
        group(
            "bills", "Bills", "Računi", "receipt_long",
            e("electricity", "Electricity", "Struja", "lightbulb"),
            e("water", "Water", "Voda", "water_drop"),
            e("heating", "Heating and gas", "Grijanje i plin", "local_fire_department"),
            e("internet", "Internet", "Internet", "language"),
            e("phone", "Phone", "Telefon", "smartphone"),
            e("streaming", "TV and streaming", "TV i streaming", "tv"),
            e("subscriptions", "Subscriptions", "Pretplate", "autorenew"),
        ),
        group(
            "health", "Health", "Zdravlje", "medical_services",
            e("pharmacy", "Pharmacy", "Apoteka", "medication"),
            e("doctor", "Doctor", "Ljekar", "local_hospital"),
            e("dentist", "Dentist", "Zubar", "health_and_safety"),
            e("fitness", "Fitness", "Fitnes", "fitness_center"),
            e("personal_care", "Personal care", "Lična njega", "content_cut"),
        ),
        group(
            "shopping", "Shopping", "Kupovina", "shopping_bag",
            e("clothes", "Clothes and shoes", "Odjeća i obuća", "checkroom"),
            e("electronics", "Electronics", "Elektronika", "devices"),
            e("cosmetics", "Cosmetics", "Kozmetika", "brush"),
            e("books", "Books", "Knjige", "menu_book"),
            e("hobby", "Hobbies", "Hobiji", "palette"),
        ),
        group(
            "entertainment", "Entertainment", "Zabava", "movie",
            e("games", "Games", "Igre", "sports_esports"),
            e("cinema", "Cinema and events", "Kino i događaji", "confirmation_number"),
            e("music", "Music", "Muzika", "music_note"),
            e("sports", "Sports", "Sport", "sports_soccer"),
            e("nightlife", "Nightlife", "Izlasci", "nightlife"),
            e("betting", "Betting", "Klađenje", "casino"),
        ),
        group(
            "travel", "Travel", "Putovanja", "flight",
            e("flights", "Flights", "Letovi", "flight_takeoff"),
            e("accommodation", "Accommodation", "Smještaj", "hotel"),
            e("activities", "Trips and activities", "Izleti i aktivnosti", "map"),
        ),
        group(
            "education", "Education", "Obrazovanje", "school",
            e("tuition", "Tuition", "Školarina", "apartment"),
            e("courses", "Courses", "Kursevi", "laptop"),
            e("school_supplies", "School supplies", "Školski pribor", "edit"),
        ),
        group(
            "family", "Family", "Porodica", "family_restroom",
            e("childcare", "Childcare", "Čuvanje djece", "child_care"),
            e("kids", "Kids", "Djeca", "child_friendly"),
            e("allowance", "Pocket money", "Džeparac", "payments"),
        ),
        group(
            "pets", "Pets", "Ljubimci", "pets",
            e("pet_food", "Pet food", "Hrana za ljubimce", "shopping_basket"),
            e("vet", "Vet", "Veterinar", "vaccines"),
        ),
        group(
            "gifts", "Gifts", "Pokloni", "card_giftcard",
            e("gifts_given", "Gifts given", "Dati pokloni", "redeem"),
            e("donations", "Donations", "Donacije", "volunteer_activism"),
        ),
        group(
            "fees", "Fees and taxes", "Naknade i porezi", "account_balance",
            e("bank_fees", "Bank fees", "Bankarske naknade", "local_atm"),
            e("taxes", "Taxes", "Porezi", "request_quote"),
            e("fines", "Fines", "Kazne", "warning"),
            e("insurance", "Insurance", "Osiguranje", "shield"),
        ),
        listOf(
            e("other_expense", "Other", "Ostalo", "more_horiz"),
            i("salary", "Salary", "Plata", "work"),
            i("freelance", "Freelance", "Honorar", "laptop"),
            i("gifts_received", "Gifts received", "Primljeni pokloni", "card_giftcard"),
            i("refunds", "Refunds", "Povrati", "undo"),
            i("interest", "Interest", "Kamata", "trending_up"),
            i("sales", "Sales", "Prodaja", "sell"),
            i("pension", "Pension", "Penzija", "elderly"),
            i("scholarship", "Scholarship", "Stipendija", "school"),
            i("other_income", "Other income", "Ostali prihodi", "add_circle"),
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
