package com.kadraa.novka.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.kadraa.novka.domain.AccountType

/**
 * Icons a category or goal can use, by the key stored in the database.
 * Keys are never shown to the user; removing one makes its rows fall back to the generic icon.
 */
val categoryIcons: Map<String, ImageVector> = linkedMapOf(
    "category" to Icons.Rounded.Category,
    // Food
    "restaurant" to Icons.Rounded.Restaurant,
    "shopping_cart" to Icons.Rounded.ShoppingCart,
    "restaurant_menu" to Icons.Rounded.RestaurantMenu,
    "local_cafe" to Icons.Rounded.LocalCafe,
    "fastfood" to Icons.Rounded.Fastfood,
    "delivery_dining" to Icons.Rounded.DeliveryDining,
    "bakery_dining" to Icons.Rounded.BakeryDining,
    "local_bar" to Icons.Rounded.LocalBar,
    // Transport
    "directions_car" to Icons.Rounded.DirectionsCar,
    "local_gas_station" to Icons.Rounded.LocalGasStation,
    "directions_bus" to Icons.Rounded.DirectionsBus,
    "local_taxi" to Icons.Rounded.LocalTaxi,
    "local_parking" to Icons.Rounded.LocalParking,
    "build" to Icons.Rounded.Build,
    "assignment" to Icons.AutoMirrored.Rounded.Assignment,
    "toll" to Icons.Rounded.Toll,
    // Housing and bills
    "home" to Icons.Rounded.Home,
    "key" to Icons.Rounded.Key,
    "home_work" to Icons.Rounded.HomeWork,
    "handyman" to Icons.Rounded.Handyman,
    "chair" to Icons.Rounded.Chair,
    "cleaning_services" to Icons.Rounded.CleaningServices,
    "receipt_long" to Icons.AutoMirrored.Rounded.ReceiptLong,
    "lightbulb" to Icons.Rounded.Lightbulb,
    "water_drop" to Icons.Rounded.WaterDrop,
    "local_fire_department" to Icons.Rounded.LocalFireDepartment,
    "language" to Icons.Rounded.Language,
    "smartphone" to Icons.Rounded.Smartphone,
    "tv" to Icons.Rounded.Tv,
    "autorenew" to Icons.Rounded.Autorenew,
    // Health
    "medical_services" to Icons.Rounded.MedicalServices,
    "medication" to Icons.Rounded.Medication,
    "local_hospital" to Icons.Rounded.LocalHospital,
    "health_and_safety" to Icons.Rounded.HealthAndSafety,
    "fitness_center" to Icons.Rounded.FitnessCenter,
    "content_cut" to Icons.Rounded.ContentCut,
    // Shopping
    "shopping_bag" to Icons.Rounded.ShoppingBag,
    "checkroom" to Icons.Rounded.Checkroom,
    "devices" to Icons.Rounded.Devices,
    "brush" to Icons.Rounded.Brush,
    "menu_book" to Icons.AutoMirrored.Rounded.MenuBook,
    "palette" to Icons.Rounded.Palette,
    // Entertainment and travel
    "movie" to Icons.Rounded.Movie,
    "sports_esports" to Icons.Rounded.SportsEsports,
    "confirmation_number" to Icons.Rounded.ConfirmationNumber,
    "music_note" to Icons.Rounded.MusicNote,
    "sports_soccer" to Icons.Rounded.SportsSoccer,
    "nightlife" to Icons.Rounded.Nightlife,
    "casino" to Icons.Rounded.Casino,
    "flight" to Icons.Rounded.Flight,
    "flight_takeoff" to Icons.Rounded.FlightTakeoff,
    "hotel" to Icons.Rounded.Hotel,
    "map" to Icons.Rounded.Map,
    // Education, family, pets, gifts
    "school" to Icons.Rounded.School,
    "apartment" to Icons.Rounded.Apartment,
    "laptop" to Icons.Rounded.LaptopMac,
    "edit" to Icons.Rounded.Edit,
    "family_restroom" to Icons.Rounded.FamilyRestroom,
    "child_care" to Icons.Rounded.ChildCare,
    "child_friendly" to Icons.Rounded.ChildFriendly,
    "payments" to Icons.Rounded.Payments,
    "pets" to Icons.Rounded.Pets,
    "shopping_basket" to Icons.Rounded.ShoppingBasket,
    "vaccines" to Icons.Rounded.Vaccines,
    "card_giftcard" to Icons.Rounded.CardGiftcard,
    "redeem" to Icons.Rounded.Redeem,
    "volunteer_activism" to Icons.Rounded.VolunteerActivism,
    // Money
    "account_balance" to Icons.Rounded.AccountBalance,
    "local_atm" to Icons.Rounded.LocalAtm,
    "request_quote" to Icons.Rounded.RequestQuote,
    "warning" to Icons.Rounded.Warning,
    "shield" to Icons.Rounded.Shield,
    "work" to Icons.Rounded.Work,
    "undo" to Icons.AutoMirrored.Rounded.Undo,
    "trending_up" to Icons.AutoMirrored.Rounded.TrendingUp,
    "sell" to Icons.Rounded.Sell,
    "elderly" to Icons.Rounded.Elderly,
    "add_circle" to Icons.Rounded.AddCircle,
    "savings" to Icons.Rounded.Savings,
    "credit_card" to Icons.Rounded.CreditCard,
    "flag" to Icons.Rounded.Flag,
    "star" to Icons.Rounded.Star,
    "favorite" to Icons.Rounded.Favorite,
    "more_horiz" to Icons.Rounded.MoreHoriz,
)

fun categoryIcon(key: String): ImageVector = categoryIcons[key] ?: Icons.Rounded.Category

fun accountIcon(type: AccountType): ImageVector = when (type) {
    AccountType.CASH -> Icons.Rounded.Payments
    AccountType.BANK -> Icons.Rounded.AccountBalance
    AccountType.SAVINGS -> Icons.Rounded.Savings
    AccountType.CARD -> Icons.Rounded.CreditCard
}
