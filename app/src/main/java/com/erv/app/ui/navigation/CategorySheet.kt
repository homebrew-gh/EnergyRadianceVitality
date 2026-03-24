package com.erv.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class Category(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val route: String,
    /** Optional second icon for combined tiles (e.g. hot + cold). */
    val iconSecondary: ImageVector? = null
)

/**
 * Categories with real screens in [ErvNavHost]. Others use Coming Soon.
 * When a feature ships, add its [Category.id] here and the menu tile returns to full color.
 */
val implementedCategoryIds: Set<String> = setOf(
    "supplements",
    "light_therapy",
    "cardio",
    "weight_training",
    "heat_cold",
    "stretching",
)

fun Category.isImplemented(): Boolean = id in implementedCategoryIds

val categories = listOf(
    Category("stretching", "Stretching", Icons.Default.FavoriteBorder, "category/stretching"),
    Category("weight_training", "Weight Training", Icons.Default.FitnessCenter, "category/weight_training"),
    Category("cardio", "Cardio", Icons.Default.DirectionsRun, "category/cardio"),
    Category(
        id = "heat_cold",
        label = "Hot + Cold",
        icon = Icons.Default.Thermostat,
        route = "category/heat_cold",
        iconSecondary = Icons.Default.AcUnit
    ),
    Category("light_therapy", "Light Therapy", Icons.Default.WbSunny, "category/light_therapy"),
    Category("supplements", "Supplements", Icons.Default.LocalPharmacy, "category/supplements"),
    Category("sleep", "Sleep", Icons.Default.Bedtime, "category/sleep"),
    Category("protocols", "Protocols", Icons.Default.Rule, "category/protocols"),
    Category("body_tracker", "Body tracker", Icons.Default.MonitorWeight, "category/body_tracker"),
    Category("programs", "Programs", Icons.Default.CalendarMonth, "category/programs")
)

@Composable
fun CategorySheet(
    onCategoryClick: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayCategories = remember(categories) {
        categories.sortedWith(
            compareByDescending<Category> { it.isImplemented() }
                .thenBy { it.label }
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 16.dp)
    ) {
        val columns = 4
        val rowCount = (displayCategories.size + columns - 1) / columns
        // 4 columns; rows grow with category count
        for (row in 0 until rowCount) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    ) {
                        if (index < displayCategories.size) {
                            val cat = displayCategories[index]
                            CategoryTile(
                                icon = cat.icon,
                                label = cat.label,
                                onClick = { onCategoryClick(cat) },
                                modifier = Modifier.fillMaxSize(),
                                secondaryIcon = cat.iconSecondary,
                                implemented = cat.isImplemented()
                            )
                        }
                    }
                }
            }
            if (row < rowCount - 1) Spacer(Modifier.height(10.dp))
        }
    }
}
