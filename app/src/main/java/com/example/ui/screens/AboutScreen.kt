package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.ui.components.ExpandableText
import com.example.ui.components.PreferenceGroup
import com.example.ui.components.PreferenceRow
import com.example.ui.components.SectionLabel
import com.example.ui.i18n.LocalStrings
import com.example.ui.platform.FileOpener
import com.example.ui.theme.NoteaPalette

const val SOURCE_URL = "https://github.com/Hussein199800/Notea"

/** About page in the spirit of Seal: big mark, version chip, description, developer card, links, credits. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    PageScaffold(s.aboutNotea, onBack = onBack) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(112.dp).clip(RoundedCornerShape(32.dp))
                        .background(Brush.linearGradient(listOf(NoteaPalette.Emerald700, NoteaPalette.Emerald900))),
                    contentAlignment = Alignment.Center
                ) { NoteaMark(Modifier.size(84.dp)) }
                Spacer(Modifier.height(16.dp))
                Text("Notea", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(s.versionLabel(BuildConfig.VERSION_NAME), Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(Modifier.height(16.dp))
                ExpandableText(s.aboutDescription, collapsedLines = 4, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionLabel(s.designedBy)
            PreferenceGroup {
                PreferenceRow(Icons.Rounded.Person, s.developerName, "GitHub · Hussein199800")
                PreferenceRow(Icons.Rounded.Code, s.sourceCode, SOURCE_URL, onClick = { FileOpener.openUrl(context, SOURCE_URL) })
            }
        }
        item {
            SectionLabel(s.credits)
            PreferenceGroup {
                PreferenceRow(Icons.Rounded.FontDownload, s.fontCredit, "IBM Plex Sans Arabic · SIL Open Font License")
                PreferenceRow(Icons.Rounded.Widgets, s.libsCredit, "Jetpack Compose · Room · WorkManager · Glance · Coil · Firebase")
            }
            DeveloperSignature(Modifier.padding(top = 28.dp, bottom = 8.dp))
        }
    }
}

/** The signature line, centered with a gold heart (like Seal's "made by" footer). */
@Composable
fun DeveloperSignature(modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Favorite, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
        }
        Text(s.signature, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text(s.versionLabel(BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}
