package com.kinnect.i221329_i221069

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

// Screen 23: marketplace tab. Listings are live from Firestore (see Marketplace.kt) in the design's two-column grid.
//  - Sell opens a form to list something        - Categories filters the grid by category
//  - the place at the top filters by place      - tap a listing to see it, message the seller, or (your own) mark it sold / delete it
// The four sample listings of the design are templates: the real ones are built in the same shape.
class MarketplaceActivity : AppCompatActivity() {

    private var listener: ListenerRegistration? = null
    private var listings: List<Listing> = emptyList()
    private var category: String? = null          // null shows every category
    private var placeFilter = ""                  // empty shows every place

    private lateinit var column: LinearLayout     // the vertical list that holds the grid rows
    private var firstRowIndex = 0                 // where the grid rows start inside [column]
    private lateinit var picksTitle: TextView
    private lateinit var placeLink: TextView
    private lateinit var defaultPlaceLabel: String
    private lateinit var emptyText: TextView

    // The photo of the listing that is being written in the Sell form
    private var sellPhoto: String? = null
    private var sellPhotoLoading = false
    private var sellPreview: ImageView? = null
    private val photoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            sellPhotoLoading = true
            ImageTools.encode(this, uri, 480, 60_000, ImageTools.FILTER_NONE) { encoded ->
                sellPhotoLoading = false
                if (encoded == null) {
                    Toast.makeText(this, "Could not read that photo", Toast.LENGTH_SHORT).show()
                } else {
                    sellPhoto = encoded
                    sellPreview?.setImageBitmap(ImageTools.fromBase64(encoded))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_marketplace)

        setupTabs(this, TAB_MARKETPLACE)
        linkTo(this, R.id.btnSearch, SearchActivity::class.java)
        linkTo(this, R.id.btnProfile, ProfileActivity::class.java)

        // Take the sample grid apart: remember where it was, then remove its two rows
        val firstRow = findViewById<View>(R.id.listingBike).parent.parent as View
        val secondRow = findViewById<View>(R.id.listingDesk).parent.parent as View
        column = firstRow.parent as LinearLayout
        firstRowIndex = column.indexOfChild(firstRow)
        column.removeView(firstRow)
        column.removeView(secondRow)

        picksTitle = findTextExact(column, "Today's picks")!!
        placeLink = findTextView(column, "Karachi")!!
        defaultPlaceLabel = placeLink.text.toString()

        emptyText = styledText(this, "", R.style.Kin_Text_Secondary)
        emptyText.gravity = Gravity.CENTER
        emptyText.setPadding(dp(this, 32), dp(this, 48), dp(this, 32), dp(this, 32))
        column.addView(emptyText, firstRowIndex)

        // The buttons of the design: Sell and Categories (the chips around the texts), and the place
        (findTextExact(column, "Sell")!!.parent as View).setOnClickListener { showSellForm() }
        (findTextExact(column, "Categories")!!.parent as View).setOnClickListener { chooseCategory() }
        placeLink.setOnClickListener { choosePlace() }
    }

    override fun onStart() {
        super.onStart()
        listener = Backend.listenListings {
            listings = it
            render()
        }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
        listener = null
    }

    // ------------------------------------------------------------------ the grid

    private fun render() {
        // Throw away the rows of the last time
        for (i in column.childCount - 1 downTo 0) {
            if (column.getChildAt(i).tag == "listingRow") {
                column.removeViewAt(i)
            }
        }

        val shown = listings.filter {
            (category == null || it.category == category) &&
                (placeFilter.isBlank() || it.place.contains(placeFilter, ignoreCase = true))
        }

        var position = firstRowIndex
        for ((n, pair) in shown.chunked(2).withIndex()) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.tag = "listingRow"
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.marginStart = dp(this, 16)
            params.marginEnd = dp(this, 16)
            params.topMargin = dp(this, if (n == 0) 14 else 16)

            for (i in 0..1) {
                val cellParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (i == 1) {
                    cellParams.marginStart = dp(this, 8)
                }
                // An odd listing leaves an empty cell so the pictures keep their size
                row.addView(if (i < pair.size) makeCell(pair[i]) else View(this), cellParams)
            }
            column.addView(row, position, params)
            position++
        }

        if (shown.isEmpty()) {
            emptyText.text = if (listings.isEmpty()) {
                "Nothing for sale yet. Tap Sell to list the first thing."
            } else {
                "Nothing matches. Try another category or place."
            }
        }
        setVisible(emptyText, shown.isEmpty())
    }

    /** One listing: the design's listing layout plus the "JUST LISTED" / "SOLD" tag. */
    private fun makeCell(listing: Listing): View {
        val cell = RelativeLayout(this)
        val item = LayoutInflater.from(this).inflate(R.layout.item_marketplace, cell, false)

        // Listings without a photo get one of the design's soft pictures
        val art = item.findViewById<ImageView>(R.id.listingArt)
        if (listing.imageBase64 != null) {
            PostImages.show(art, "listing_${listing.id}", listing.imageBase64)
        } else {
            val soft = intArrayOf(
                R.drawable.art_sand, R.drawable.art_teal, R.drawable.art_lilac,
                R.drawable.art_blue, R.drawable.art_green, R.drawable.art_rose
            )
            art.setImageResource(soft[Math.abs(listing.id.hashCode()) % soft.size])
        }
        fillListingTexts(item, formatPrice(listing.price), listing.title, listing.place)
        item.setOnClickListener { showListing(listing) }
        cell.addView(item, RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val recent = System.currentTimeMillis() - listing.time < 24 * 60 * 60 * 1000
        if (listing.sold || recent) {
            val tag = TextView(this)
            tag.text = if (listing.sold) "SOLD" else "JUST LISTED"
            tag.setBackgroundResource(R.drawable.bg_round_8)
            tag.backgroundTintList = ColorStateList.valueOf(getColor(if (listing.sold) R.color.text_primary else R.color.rust))
            tag.letterSpacing = 0.05f
            tag.setPadding(dp(this, 8), dp(this, 4), dp(this, 8), dp(this, 4))
            tag.setTextColor(getColor(R.color.white))
            tag.textSize = 12f
            tag.setTypeface(null, Typeface.BOLD)
            val tagParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            tagParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            tagParams.setMargins(dp(this, 8), dp(this, 8), dp(this, 8), dp(this, 8))
            cell.addView(tag, tagParams)
        }
        cell.alpha = if (listing.sold) 0.6f else 1f
        return cell
    }

    /** Fills the texts of a listing (the picture is handled by the caller). */
    private fun fillListingTexts(item: View, price: String, title: String, place: String) {
        item.findViewById<TextView>(R.id.tvPrice).text = price
        item.findViewById<TextView>(R.id.tvName).text = title
        item.findViewById<TextView>(R.id.tvPlace).text = place
    }

    // ------------------------------------------------------------------ filters

    private fun chooseCategory() {
        val names = arrayOf("All categories") + LISTING_CATEGORIES
        val current = if (category == null) 0 else names.indexOf(category)
        AlertDialog.Builder(this)
            .setTitle("Categories")
            .setSingleChoiceItems(names, current) { dialog, which ->
                category = if (which == 0) null else names[which]
                picksTitle.text = category ?: "Today's picks"
                dialog.dismiss()
                render()
            }
            .show()
    }

    private fun choosePlace() {
        val field = EditText(this)
        field.hint = "A place, for example Clifton"
        field.setText(placeFilter)
        field.setPadding(dp(this, 20), dp(this, 20), dp(this, 20), dp(this, 20))
        AlertDialog.Builder(this)
            .setTitle("Show listings in")
            .setView(field)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Everywhere") { _, _ ->
                placeFilter = ""
                placeLink.text = defaultPlaceLabel
                render()
            }
            .setPositiveButton("Show") { _, _ ->
                placeFilter = field.text.toString().trim()
                placeLink.text = if (placeFilter.isEmpty()) defaultPlaceLabel else placeFilter
                render()
            }
            .show()
    }

    // ------------------------------------------------------------------ one listing

    private fun showListing(listing: Listing) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL

        if (listing.imageBase64 != null) {
            val picture = ImageView(this)
            picture.scaleType = ImageView.ScaleType.CENTER_CROP
            picture.setImageBitmap(ImageTools.fromBase64(listing.imageBase64))
            box.addView(picture, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 220)))
        }
        val details = LinearLayout(this)
        details.orientation = LinearLayout.VERTICAL
        details.setPadding(dp(this, 20), dp(this, 16), dp(this, 20), dp(this, 4))
        details.addView(styledText(this, formatPrice(listing.price) + if (listing.sold) "  (sold)" else "", R.style.Kin_Text_Title))
        details.addView(styledText(this, listing.title, R.style.Kin_Text))
        details.addView(styledText(this, listing.category + " · " + listing.place, R.style.Kin_Text_Secondary))
        if (listing.description.isNotBlank()) {
            val description = styledText(this, listing.description, R.style.Kin_Text)
            description.setPadding(0, dp(this, 12), 0, 0)
            details.addView(description)
        }
        val seller = styledText(this, "Listed by " + listing.sellerName + " · " + timeAgo(listing.time), R.style.Kin_Text_Secondary)
        seller.setPadding(0, dp(this, 12), 0, 0)
        details.addView(seller)
        box.addView(details)

        val scroll = ScrollView(this)
        scroll.addView(box)
        val builder = AlertDialog.Builder(this).setView(scroll).setPositiveButton("Close", null)

        if (listing.sellerId == Backend.myUid) {
            builder.setNeutralButton(if (listing.sold) "Mark as available" else "Mark as sold") { _, _ ->
                Backend.setListingSold(listing.id, !listing.sold)
            }
            builder.setNegativeButton("Delete") { _, _ ->
                AlertDialog.Builder(this)
                    .setMessage("Delete this listing?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ -> Backend.deleteListing(listing.id) }
                    .show()
            }
        } else {
            builder.setNegativeButton("Message seller") { _, _ -> openChat(this, listing.sellerId) }
        }
        builder.show()
    }

    // ------------------------------------------------------------------ Sell

    /** The form for a new listing: photo, title, price, place, category and a description. */
    private fun showSellForm() {
        sellPhoto = null
        sellPhotoLoading = false

        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(this, 20), dp(this, 12), dp(this, 20), dp(this, 4))

        val preview = ImageView(this)
        preview.scaleType = ImageView.ScaleType.CENTER_CROP
        preview.setBackgroundResource(R.drawable.bg_round_16)
        preview.backgroundTintList = ColorStateList.valueOf(getColor(R.color.chip))
        preview.clipToOutline = true
        sellPreview = preview
        form.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 150)))

        val addPhoto = styledButton(this, "Add a photo", true)
        addPhoto.setOnClickListener { photoPicker.launch("image/*") }
        form.addView(addPhoto, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 40)).apply { topMargin = dp(this@MarketplaceActivity, 8) })

        val title = formField("What are you selling?", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val price = formField("Price in Rs", InputType.TYPE_CLASS_NUMBER)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val place = formField("Where? For example Clifton", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        place.setText(prefs.getString("last_place", "") ?: "")
        val description = formField("Describe it (optional)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        description.minLines = 2
        for (field in listOf(title, price, place)) {
            form.addView(field, fieldParams())
        }
        val categories = Spinner(this)
        categories.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, LISTING_CATEGORIES)
        form.addView(categories, fieldParams())
        form.addView(description, fieldParams())

        val scroll = ScrollView(this)
        scroll.addView(form)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Sell something")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("List it", null)
            .create()
        dialog.show()

        // Set here (not in the builder) so the form stays open when something is missing
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = title.text.toString().trim()
            val amount = price.text.toString().trim().toLongOrNull()
            val where = place.text.toString().trim()
            if (name.isEmpty() || amount == null || amount <= 0 || where.isEmpty()) {
                Toast.makeText(this, "Add a title, a price and a place", Toast.LENGTH_SHORT).show()
            } else if (sellPhotoLoading) {
                Toast.makeText(this, "Preparing your photo...", Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit().putString("last_place", where).apply()
                Backend.createListing(
                    name, amount, where, categories.selectedItem.toString(),
                    description.text.toString().trim(), sellPhoto
                ) { saved ->
                    if (!saved) {
                        Toast.makeText(this, "Could not list it. Check your connection.", Toast.LENGTH_LONG).show()
                    }
                }
                dialog.dismiss()
                Toast.makeText(this, "Your listing is live", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formField(hint: String, type: Int): EditText {
        val field = EditText(this)
        field.hint = hint
        field.inputType = type
        field.textSize = 16f
        field.setBackgroundResource(R.drawable.bg_input)
        field.setPadding(dp(this, 12), dp(this, 10), dp(this, 12), dp(this, 10))
        return field
    }

    private fun fieldParams(): LinearLayout.LayoutParams {
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.topMargin = dp(this, 8)
        return params
    }
}
