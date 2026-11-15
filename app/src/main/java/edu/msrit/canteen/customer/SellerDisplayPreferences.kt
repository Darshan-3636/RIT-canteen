package edu.msrit.canteen.customer

import android.content.Context

/** Local seller display preferences; they never change order or audit history. */
object SellerDisplayPreferences {
    private const val FILE = "seller_display_preferences"

    fun showRequestJourney(context: Context, sellerId: String): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("request_journey_$sellerId", false)

    fun setShowRequestJourney(context: Context, sellerId: String, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("request_journey_$sellerId", enabled).apply()
    }
}
