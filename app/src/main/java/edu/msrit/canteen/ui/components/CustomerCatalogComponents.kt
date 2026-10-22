package edu.msrit.canteen.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.R
import edu.msrit.canteen.customer.Dietary
import edu.msrit.canteen.customer.FoodKind
import edu.msrit.canteen.customer.FoodPhoto
import edu.msrit.canteen.customer.ItemEligibility
import edu.msrit.canteen.customer.MenuCategory
import edu.msrit.canteen.customer.MenuItem
import edu.msrit.canteen.customer.Seller
import edu.msrit.canteen.customer.SellerPhoto
import edu.msrit.canteen.customer.itemEligibility
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

@Composable
fun CatalogHeader(title: String, onBack: () -> Unit) {
    val colors = RitThemeTokens.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).background(colors.warmCard)
            .clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) {
            RitIcon(RitIconKind.Back, colors.foreground)
        }
        Spacer(Modifier.width(12.dp))
        RitText(title, RitType.title)
    }
}

@Composable
fun CatalogCategories(categories: List<MenuCategory>, selectedId: String?, onSelect: (String?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp)) {
        item(key = "all") { RitChip("All", selectedId == null, { onSelect(null) }) }
        items(categories, key = { it.id }) { category ->
            RitChip(category.name, selectedId == category.id, { onSelect(category.id) })
        }
    }
}

@Composable
fun FoodPhotoContent(photo: FoodPhoto?, description: String, modifier: Modifier = Modifier, localUri: String? = null) {
    val colors = RitThemeTokens.colors
    val bitmap = rememberLocalBitmap(localUri)
    val image = when (photo) {
        FoodPhoto.DOSA -> R.drawable.catalog_dosa
        FoodPhoto.BIRYANI -> R.drawable.catalog_biryani
        FoodPhoto.COFFEE -> R.drawable.catalog_coffee
        null -> null
    }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), contentDescription = "$description · locally selected demo image",
            modifier = modifier, contentScale = ContentScale.Crop)
    } else if (image != null) {
        Image(painterResource(image), contentDescription = description, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(colors.warmCard), contentAlignment = Alignment.Center) {
            RitIcon(RitIconKind.Food, colors.accent, size = 34.dp)
        }
    }
}

@Composable
fun SellerPhotoContent(photo: SellerPhoto?, modifier: Modifier = Modifier, localUri: String? = null) {
    val colors = RitThemeTokens.colors
    val bitmap = rememberLocalBitmap(localUri)
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), contentDescription = "Locally selected demo seller image", modifier = modifier,
            contentScale = ContentScale.Crop)
    } else if (photo == SellerPhoto.CAMPUS_CONTEXT) {
        Image(painterResource(R.drawable.rit_campus), contentDescription = "RIT campus concept image, not a verified seller photo", modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(colors.warmCard), contentAlignment = Alignment.Center) {
            RitIcon(RitIconKind.Store, colors.accent, size = 36.dp)
        }
    }
}

@Composable
private fun rememberLocalBitmap(uri: String?): android.graphics.Bitmap? {
    val context = LocalContext.current
    return remember(uri) {
        uri?.let { value -> runCatching {
            val parsed = android.net.Uri.parse(value)
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(parsed)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
            val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(parsed)?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull() }
    }
}

@Composable
fun CatalogPill(label: String, modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    Box(modifier.clip(RoundedCornerShape(RitRadii.pill)).background(colors.accentSoft).padding(horizontal = 10.dp, vertical = 6.dp)) {
        RitText(label, RitType.small, colors.accent)
    }
}

fun eligibilityLabel(seller: Seller, item: MenuItem): String = when (itemEligibility(seller, item)) {
    ItemEligibility.UNAVAILABLE -> "Unavailable"
    ItemEligibility.BULK_ONLY -> if (item.kind == FoodKind.PACKAGED) "Packaged · bulk only" else "Bulk only"
    ItemEligibility.FUTURE_INDIVIDUAL_AND_BULK -> "Individual planned · bulk"
}

fun dietaryLabel(item: MenuItem): String = when (item.dietary) {
    Dietary.VEGETARIAN -> "Vegetarian"
    Dietary.NON_VEGETARIAN -> "Non-vegetarian"
    Dietary.NOT_SPECIFIED -> "Dietary info not provided"
}

fun sellerOrderingBadge(seller: Seller): String =
    if (seller.isMainCanteen && seller.capabilities.futureIndividualPrepared) "Individual & Bulk" else "Bulk Only"

@Composable
fun CatalogFavoriteButton(name: String, selected: Boolean, onClick: () -> Unit, tag: String) {
    val colors = RitThemeTokens.colors
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp))
        .background(if (selected) colors.accentSoft else colors.surface)
        .clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = if (selected) "Remove $name from favorites" else "Add $name to favorites" }
        .testTag(tag), contentAlignment = Alignment.Center) {
        RitIcon(RitIconKind.Heart, if (selected) colors.accent else colors.muted, size = 22.dp)
    }
}

@Composable
fun CatalogFoodCard(item: MenuItem, seller: Seller, onClick: () -> Unit, modifier: Modifier = Modifier,
                    isFavorite: Boolean = false, onFavoriteClick: (() -> Unit)? = null) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.card)
    Column(
        modifier.fillMaxWidth().clip(shape).border(1.dp, colors.outline, shape)
            .background(colors.card).clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(158.dp)) {
            FoodPhotoContent(item.photo, item.name + " illustrative preview image",
                Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = RitRadii.card, topEnd = RitRadii.card)), item.localImageUri)
            CatalogPill(eligibilityLabel(seller, item), Modifier.align(Alignment.BottomStart).padding(10.dp))
            if (onFavoriteClick != null) {
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    CatalogFavoriteButton(item.name, isFavorite, onFavoriteClick, "favorite_item_${item.id}")
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                RitText(item.name, RitType.title, maxLines = 1)
                RitText("${seller.name} · ${dietaryLabel(item)}", RitType.small, colors.muted, maxLines = 1)
                Spacer(Modifier.height(5.dp))
                RitText(item.description, RitType.small, colors.muted, maxLines = 2)
            }
            Spacer(Modifier.width(12.dp))
            RitText(item.price.display, RitType.label, colors.accent)
        }
    }
}

@Composable
fun CatalogSellerCard(seller: Seller, categoryNames: String, onClick: () -> Unit,
                      isFavorite: Boolean = false, onFavoriteClick: (() -> Unit)? = null,
                      modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(RitRadii.card)).border(1.dp, colors.outline, RoundedCornerShape(RitRadii.card))
        .background(colors.card).clickable(role = Role.Button, onClick = onClick).then(modifier)) {
        Box(Modifier.fillMaxWidth().height(158.dp)) {
            SellerPhotoContent(seller.photo, Modifier.fillMaxSize(), seller.localImageUri)
            CatalogPill(if (seller.isMainCanteen) "Main Canteen" else "Campus seller",
                Modifier.align(Alignment.BottomStart).padding(10.dp))
        }
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RitText(seller.name, RitType.title, modifier = Modifier.weight(1f))
                if (onFavoriteClick != null) CatalogFavoriteButton(seller.name, isFavorite, onFavoriteClick, "favorite_seller_${seller.id}")
            }
            Spacer(Modifier.height(3.dp))
            RitText(seller.description, RitType.body, colors.muted, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            CatalogPill(sellerOrderingBadge(seller))
            Spacer(Modifier.height(8.dp))
            RitText(categoryNames, RitType.small, colors.muted, maxLines = 2)
            RitText(seller.hours?.display?.let { "Demo hours: $it · live status unverified" }
                ?: "Hours and live availability not verified", RitType.small, colors.muted, maxLines = 2)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RitText("View Menu", RitType.label, colors.accent)
                Spacer(Modifier.width(5.dp))
                RitIcon(RitIconKind.Arrow, colors.accent, size = 17.dp)
            }
        }
    }
}
