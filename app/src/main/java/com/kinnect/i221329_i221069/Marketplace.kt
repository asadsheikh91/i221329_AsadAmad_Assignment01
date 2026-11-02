package com.kinnect.i221329_i221069

import com.google.firebase.database.ServerValue
import java.text.NumberFormat
import java.util.Locale

// The marketplace part of the backend.
//
//   listings/{id}   sellerId, sellerName, title, price, place, category, description,
//                   imageBase64 (a small picture, about 480 pixels), sold, createdAt

val LISTING_CATEGORIES = arrayOf("Vehicles", "Furniture", "Electronics", "Home & garden", "Clothing", "Other")

data class Listing(
    val id: String,
    val sellerId: String,
    val sellerName: String,
    val title: String,
    val price: Long,
    val place: String,
    val category: String,
    val description: String,
    val imageBase64: String?,
    val sold: Boolean,
    val time: Long
)

/** 45000 -> "Rs 45,000". */
fun formatPrice(price: Long): String = "Rs " + NumberFormat.getIntegerInstance(Locale.US).format(price)

fun Backend.createListing(
    title: String, price: Long, place: String, category: String, description: String,
    imageBase64: String?, done: (Boolean) -> Unit
) {
    val uid = myUid
    val name = me?.name
    if (uid == null || name == null) {
        done(false)
        return
    }
    val listing = hashMapOf<String, Any?>(
        "sellerId" to uid,
        "sellerName" to name,
        "title" to title,
        "price" to price,
        "place" to place,
        "category" to category,
        "description" to description,
        "imageBase64" to imageBase64,
        "sold" to false,
        "createdAt" to ServerValue.TIMESTAMP
    )
    ref("listings").push().setValue(listing)
        .addOnSuccessListener { done(true) }
        .addOnFailureListener { done(false) }
}

/** The 60 newest listings, newest first. */
fun Backend.listenListings(onChange: (List<Listing>) -> Unit): ListenerRegistration {
    return listen(ref("listings").orderByChild("createdAt").limitToLast(60)) { snap ->
        val listings = snap.children.map { doc ->
            Listing(
                doc.key ?: "",
                doc.text("sellerId"),
                doc.text("sellerName").ifEmpty { "Kinnect user" },
                doc.text("title"),
                doc.number("price"),
                doc.text("place"),
                doc.text("category").ifEmpty { "Other" },
                doc.text("description"),
                doc.child("imageBase64").value as? String,
                doc.flag("sold"),
                doc.number("createdAt")
            )
        }
        onChange(listings.sortedByDescending { it.time })
    }
}

fun Backend.setListingSold(listingId: String, sold: Boolean) {
    ref("listings/$listingId/sold").setValue(sold)
}

fun Backend.deleteListing(listingId: String) {
    ref("listings/$listingId").removeValue()
}
