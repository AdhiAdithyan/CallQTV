package com.softland.callqtv.utils

import android.content.Context
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

object ThemeColorManager {
    fun getSelectedThemeColor(context: Context): Color {
        val hex = getSelectedThemeColorHex(context)
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (e: Exception) {
            Color(0xFF2196F3) // Default Blue
        }
    }

    fun getSelectedThemeColorHex(context: Context): String {
        return context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
            .getString("theme_color", "#2196F3") ?: "#2196F3"
    }

    fun setThemeColor(context: Context, hex: String) {
        context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
            .edit().putString("theme_color", hex).apply()
    }

    fun createDarkColorScheme(primaryColor: Color) = darkColorScheme(
        primary = primaryColor,
        onPrimary = Color.White,
        primaryContainer = primaryColor.copy(alpha = 0.15f),
        onPrimaryContainer = primaryColor,
        secondary = primaryColor,
        onSecondary = Color.White,
        secondaryContainer = primaryColor.copy(alpha = 0.1f),
        onSecondaryContainer = primaryColor,
        background = Color(0xFF121212),
        onBackground = Color.White,
        surface = Color(0xFF1E1E1E),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF2C2C2C),
        onSurfaceVariant = Color.White,
        outline = primaryColor.copy(alpha = 0.5f)
    )

    fun getBackgroundIntensity(context: Context): Float = 0.15f

    fun getCounterBackgroundColor(context: Context): String {
        return context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
            .getString("counter_bg_color", "#FFFFFF") ?: "#FFFFFF"
    }

    fun setCounterBackgroundColor(context: Context, hex: String) {
        context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
            .edit().putString("counter_bg_color", hex).apply()
    }

    fun getTokenBackgroundColor(context: Context): String {
        return context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
            .getString("token_bg_color", "#FFFFFF") ?: "#FFFFFF"
    }

    fun setTokenBackgroundColor(context: Context, hex: String) {
        context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
            .edit().putString("token_bg_color", hex).apply()
    }

    fun getNotificationSoundKey(context: Context): String {
        return context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
            .getString("notification_sound_key", "ding") ?: "ding"
    }

    fun setNotificationSoundKey(context: Context, key: String) {
        context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
            .edit().putString("notification_sound_key", key).apply()
    }

    val themeColorOptions = listOf(
        // Blue
        ThemeOption("Blue", "#2196F3"),
        ThemeOption("Light Blue", "#64B5F6"),
        ThemeOption("Dark Blue", "#1565C0"),
        // Branded Metallic
        ThemeOption("CallQ Silver", "#C0C0C0"),
        ThemeOption("CallQ Platinum", "#E5E4E2"),
        ThemeOption("CallQ Steel", "#778899"),
        // High-Contrast Highlights (To make silver logo pop)
        ThemeOption("Electric Blue", "#00E5FF"),
        ThemeOption("Neon Cyan", "#00B8D4"),
        ThemeOption("Cyber Purple", "#AA00FF"),
        // Red
        ThemeOption("Red", "#F44336"),
        ThemeOption("Light Red", "#EF5350"),
        ThemeOption("Dark Red", "#C62828"),
        // Green
        ThemeOption("Green", "#4CAF50"),
        ThemeOption("Light Green", "#81C784"),
        ThemeOption("Dark Green", "#2E7D32"),
        // Purple
        ThemeOption("Purple", "#9C27B0"),
        ThemeOption("Light Purple", "#BA68C8"),
        ThemeOption("Dark Purple", "#6A1B9A"),
        // Orange / Amber
        ThemeOption("Orange", "#FF9800"),
        ThemeOption("Light Orange", "#FFB74D"),
        ThemeOption("Dark Orange", "#EF6C00"),
        ThemeOption("Amber", "#FFC107"),
        // Teal / Cyan
        ThemeOption("Teal", "#009688"),
        ThemeOption("Light Teal", "#4DB6AC"),
        ThemeOption("Cyan", "#00BCD4"),
        // Pink
        ThemeOption("Pink", "#E91E63"),
        ThemeOption("Light Pink", "#F06292"),
        ThemeOption("Dark Pink", "#AD1457"),
        // Others
        ThemeOption("Indigo", "#3F51B5"),
        ThemeOption("Dark Indigo", "#283593"),
        ThemeOption("Lime", "#CDDC39"),
        ThemeOption("Brown", "#795548"),
        ThemeOption("Deep Orange", "#FF5722"),
        ThemeOption("Blue Grey", "#607D8B")
    )
    
    val backgroundOptions = listOf(
        // Grayscale
        ThemeOption("White", "#FFFFFF"),
        ThemeOption("Off White", "#FAFAFA"),
        ThemeOption("Cream", "#FFFDD0"),
        ThemeOption("Light Gray", "#EEEEEE"),
        ThemeOption("Gray", "#9E9E9E"),
        ThemeOption("Dark Gray", "#424242"),
        ThemeOption("Charcoal", "#212121"),
        ThemeOption("Black", "#000000"),
        
        // Solids - Light/Dark
        ThemeOption("Blue", "#2196F3"),
        ThemeOption("Pale Blue", "#BBDEFB"),
        ThemeOption("Navy", "#0D47A1"),
        
        ThemeOption("Red", "#F44336"),
        ThemeOption("Pale Red", "#FFCDD2"),
        ThemeOption("Dark Red", "#B71C1C"),
        
        ThemeOption("Green", "#4CAF50"),
        ThemeOption("Mint", "#C8E6C9"),
        ThemeOption("Dark Green", "#1B5E20"),
        
        ThemeOption("Yellow", "#FFEB3B"),
        ThemeOption("Light Yellow", "#FFF9C4"),
        
        ThemeOption("Orange", "#FF9800"),
        ThemeOption("Peach", "#FFE0B2"),
        
        ThemeOption("Purple", "#9C27B0"),
        ThemeOption("Lavender", "#E1BEE7"),
        ThemeOption("Deep Purple", "#673AB7"),
        
        ThemeOption("Pink", "#E91E63"),
        ThemeOption("Light Pink", "#F8BBD0"),
        
        ThemeOption("Teal", "#009688"),
        ThemeOption("Aqua", "#B2DFDB"),
        ThemeOption("Deep Teal", "#004D40"),
        ThemeOption("Emerald", "#1B5E20"),
        ThemeOption("Midnight Blue", "#0B1020"),
        ThemeOption("Steel Blue", "#1E3A5F"),
        ThemeOption("Wine", "#4A0E25"),
        ThemeOption("Plum", "#4A148C"),
        ThemeOption("Burnt Orange", "#BF360C"),
        ThemeOption("Olive", "#3E4D26"),
        ThemeOption("Charcoal Blue", "#263238"),
        ThemeOption("Graphite", "#1C1C1C"),
        ThemeOption("Ink Black", "#050505"),
        ThemeOption("Night Sky", "#101820"),
        
        ThemeOption("Brown", "#795548"),
        ThemeOption("Tan", "#D7CCC8"),
        ThemeOption("Sand", "#F5F5DC"),
        ThemeOption("Sky", "#E3F2FD"),
        ThemeOption("Powder Blue", "#B3E5FC"),
        ThemeOption("Sea Foam", "#E0F2F1"),
        ThemeOption("Soft Mint", "#E8F5E9"),
        ThemeOption("Blush", "#FCE4EC"),
        ThemeOption("Soft Peach", "#FFF3E0"),
        ThemeOption("Soft Lavender", "#F3E5F5"),
        ThemeOption("Light Teal", "#B2EBF2"),
        ThemeOption("Ice", "#E0F7FA"),
        ThemeOption("Soft Grey", "#F5F5F5"),
        ThemeOption("Cool Grey", "#ECEFF1"),
        ThemeOption("Pale Mint", "#F1F8E9"),
        ThemeOption("Pale Aqua", "#E0F2F1"),
        
        ThemeOption("Indigo", "#3F51B5"),
        ThemeOption("Cyan", "#00BCD4"),
        
        // Branded & Highlight Gradients
        ThemeOption("Silver Shine", "GRADIENT:#E0E0E0,#757575"),
        ThemeOption("Platinum Glow", "GRADIENT:#FFFFFF,#BDBDBD"),
        ThemeOption("Deep Space", "GRADIENT:#000000,#434343"),
        ThemeOption("Electric Stream", "GRADIENT:#2196F3,#00E5FF"),
        ThemeOption("Premium Dark", "GRADIENT:#121212,#242424,#121212"),

        // Standard Gradients
        ThemeOption("Blue Gradient", "GRADIENT:#1976D2,#64B5F6"),
        ThemeOption("Red Gradient", "GRADIENT:#D32F2F,#EF5350"),
        ThemeOption("Green Gradient", "GRADIENT:#388E3C,#81C784"),
        ThemeOption("Purple Gradient", "GRADIENT:#7B1FA2,#BA68C8"),
        ThemeOption("Orange Gradient", "GRADIENT:#F57C00,#FFB74D"),
        ThemeOption("Teal Gradient", "GRADIENT:#00796B,#4DB6AC"),
        ThemeOption("Pink Gradient", "GRADIENT:#EC407A,#F48FB1"),
        ThemeOption("Cyan Gradient", "GRADIENT:#00ACC1,#4DD0E1"),
        ThemeOption("Amber Gradient", "GRADIENT:#FFB300,#FFD54F"),
        ThemeOption("Indigo Gradient", "GRADIENT:#3949AB,#7986CB"),
        ThemeOption("Midnight Gradient", "GRADIENT:#1A237E,#3949AB"),
        ThemeOption("Sunset Gradient", "GRADIENT:#FF5722,#FFAB91"),
        ThemeOption("Forest Gradient", "GRADIENT:#2E7D32,#66BB6A"),
        ThemeOption("Slate Gradient", "GRADIENT:#455A64,#78909C"),
        ThemeOption("Dark Gradient", "GRADIENT:#212121,#424242"),
        ThemeOption("Deep Ocean Gradient", "GRADIENT:#0B1020,#1E3A5F"),
        ThemeOption("Midnight Forest Gradient", "GRADIENT:#1B5E20,#004D40"),
        ThemeOption("Wine Gradient", "GRADIENT:#4A0E25,#880E4F"),
        ThemeOption("Plum Gradient", "GRADIENT:#4A148C,#7B1FA2"),
        ThemeOption("Amber Night Gradient", "GRADIENT:#FF6F00,#E65100"),
        ThemeOption("Steel Gradient", "GRADIENT:#263238,#455A64"),
        ThemeOption("Charcoal Gradient", "GRADIENT:#1C1C1C,#424242"),
        ThemeOption("Indigo Night Gradient", "GRADIENT:#1A237E,#283593"),
        ThemeOption("Teal Night Gradient", "GRADIENT:#004D40,#00695C"),
        ThemeOption("Emerald Night Gradient", "GRADIENT:#1B5E20,#2E7D32"),
        ThemeOption("Soft Blue Gradient", "GRADIENT:#E3F2FD,#90CAF9"),
        ThemeOption("Soft Green Gradient", "GRADIENT:#E8F5E9,#A5D6A7"),
        ThemeOption("Soft Pink Gradient", "GRADIENT:#FCE4EC,#F48FB1"),
        ThemeOption("Soft Orange Gradient", "GRADIENT:#FFF3E0,#FFCC80"),
        ThemeOption("Soft Purple Gradient", "GRADIENT:#F3E5F5,#CE93D8"),
        ThemeOption("Aqua Gradient", "GRADIENT:#E0F7FA,#4DD0E1"),
        ThemeOption("Skyline Gradient", "GRADIENT:#BBDEFB,#64B5F6"),
        ThemeOption("Dawn Gradient", "GRADIENT:#FFFDE7,#FFE082"),
        ThemeOption("Mint Gradient", "GRADIENT:#E0F2F1,#80CBC4"),
        ThemeOption("Cloud Gradient", "GRADIENT:#FFFFFF,#ECEFF1"),
        ThemeOption("Shadow Gradient", "GRADIENT:#000000,#263238"),
        ThemeOption("Smoke Gradient", "GRADIENT:#212121,#616161"),
        ThemeOption("Rose Gold Gradient", "GRADIENT:#B76E79,#F7CAC9"),
        ThemeOption("Sand Dune Gradient", "GRADIENT:#C19A6B,#8D6E63"),
        ThemeOption("Frost Gradient", "GRADIENT:#E0F7FA,#FFFFFF"),
        // Multi-Color
        ThemeOption("Sunset Multi", "GRADIENT:#FF512F,#F09819,#FF512F"),
        ThemeOption("Berry Multi", "GRADIENT:#8E2DE2,#4A00E0,#8E2DE2"),
        ThemeOption("Ocean Multi", "GRADIENT:#2193b0,#6dd5ed,#2193b0"),
        ThemeOption("Lush Multi", "GRADIENT:#56ab2f,#a8e063,#56ab2f"),
        ThemeOption("Fire Multi", "GRADIENT:#CAC531,#F3F9A7"),
        ThemeOption("Instagram", "GRADIENT:#833ab4,#fd1d1d,#fcb045"),
        ThemeOption("Disco", "GRADIENT:#40E0D0,#FF8C00,#FF0080")
    )

    fun getBackgroundBrush(hexOrGradient: String): Brush {
        if (hexOrGradient.startsWith("GRADIENT:")) {
            val parts = hexOrGradient.removePrefix("GRADIENT:").split(",")
            if (parts.size >= 2) {
                return try {
                    val colors = parts.map { Color(android.graphics.Color.parseColor(it.trim())) }
                    Brush.verticalGradient(colors = colors)
                } catch (e: Exception) {
                    SolidColor(Color.White)
                }
            }
        }
        return try {
             SolidColor(Color(android.graphics.Color.parseColor(hexOrGradient)))
        } catch (e: Exception) {
             SolidColor(Color.White)
        }
    }
}

data class ThemeOption(val name: String, val hexCode: String)
