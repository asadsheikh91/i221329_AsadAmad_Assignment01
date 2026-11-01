package com.asadamad.i221329

import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
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
    val listing = hashMapOf(
        "sellerId" to uid,
        "sellerName" to name,
        "title" to title,
        "price" to price,
        "place" to place,
        "category" to category,
        "description" to description,
        "imageBase64" to imageBase64,
        "sold" to false,
        "createdAt" to FieldValue.serverTimestamp()
    )
    db.collection("listings").add(listing)
        .addOnSuccessListener { done(true) }
        .addOnFailureListener { done(false) }
}

/** The 60 newest listings, newest first. */
fun Backend.listenListings(onChange: (List<Listing>) -> Unit): ListenerRegistration {
    return db.collection("listings")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(60)
        .addSnapshotListener { snap, _ ->
            if (snap != null) {
                onChange(snap.documents.map { doc ->
                    val time = doc.getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE)
                    Listing(
                        doc.id,
                        doc.getString("sellerId") ?: "",
                        doc.getString("sellerName") ?: "Kinnect user",
                        doc.getString("title") ?: "",
                        doc.getLong("price") ?: 0L,
                        doc.getString("place") ?: "",
                        doc.getString("category") ?: "Other",
                        doc.getString("description") ?: "",
                        doc.getString("imageBase64"),
                        doc.getBoolean("sold") == true,
                        time?.toDate()?.time ?: 0L
                    )
                })
            }
        }
}

fun Backend.setListingSold(listingId: String, sold: Boolean) {
    db.collection("listings").document(listingId).update("sold", sold)
}

fun Backend.deleteListing(listingId: String) {
    db.collection("listings").document(listingId).delete()
}
