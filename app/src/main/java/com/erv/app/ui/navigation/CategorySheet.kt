package com.erv.app.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class Category(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val route: String
)

val categories = listOf(
    Category("stretching", "Stretching", Icons.Default.FavoriteBorder, "category/stretching"),
    Category("weight_training", "Weight Training", Icons.Default.FitnessCenter, "category/weight_training"),
    Category("cardio", "Cardio", Icons.Default.DirectionsRun, "category/cardio"),
    Category("sauna", "Sauna", Icons.Default.Thermostat, "category/sauna"),
    Category("cold_plunge", "Cold Plunge", Icons.Default.AcUnit, "category/cold_plunge"),
    Category("light_therapy", "Light Therapy", Icons.Default.WbSunny, "category/light_therapy"),
    Category("supplements", "Supplements", Icons.Default.LocalPharmacy, "category/supplements"),
    Category("sleep", "Sleep", Icons.Default.Bedtime, "category/sleep")
)

@Composable
fun CategorySheet(
    onCategoryClick: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
    ) {
        // 2 rows x 4 columns
        for (row in 0..1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (col in 0..3) {
                    val index = row * 4 + col
                    if (index < categories.size) {
                        val cat = categories[index]
                        CategoryTile(
                            icon = cat.icon,
                            label = cat.label,
                            onClick = { onCategoryClick(cat) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (row == 0) Spacer(Modifier.height(10.dp))
        }
    }
}
