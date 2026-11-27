package com.finance.splitup.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finance.splitup.BuildConfig
import com.finance.splitup.R
import com.finance.splitup.data.UpdateChecker
import com.finance.splitup.ui.components.LogoStyle
import com.finance.splitup.ui.components.SplitUpLogo
import com.finance.splitup.ui.theme.SplitUpType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onStartDemoTour: () -> Unit = {}
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About SplitUp", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // App Logo & Version
            SplitUpLogo(size = 100.dp, style = LogoStyle.REFINED_CARD_SPLITUP)

            Text(
                text = "SplitUp",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Text(
                    text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Text(
                text = "Smart Expense Splitting & Personal Tracking",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            // Quick Actions Row (GitHub, Website, Updates)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // GitHub Item
                QuickActionItem(
                    iconPainter = painterResource(id = R.drawable.ic_github),
                    imageVector = null,
                    label = "GitHub",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = {
                        UpdateChecker.openReleaseUrl(context, "https://github.com/sreejith19931/SplitUp")
                    }
                )

                // Website Item
                QuickActionItem(
                    iconPainter = null,
                    imageVector = Icons.Rounded.Language,
                    label = "Website",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = {
                        UpdateChecker.openReleaseUrl(context, "https://splitupfin.vercel.app")
                    }
                )

                // Play Store / Updates Item
                QuickActionItem(
                    iconPainter = null,
                    imageVector = Icons.Rounded.SystemUpdate,
                    label = "Play Store",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = {
                        val packageName = context.packageName
                        UpdateChecker.openReleaseUrl(context, "https://play.google.com/store/apps/details?id=$packageName")
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Tech Stack & Architecture Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "Architecture & Build", style = SplitUpType.SectionTitle, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Engineered with modern Android best practices: Jetpack Compose, Material 3, Navigation 3, Kotlin Coroutines & StateFlow, Room Database, and WorkManager.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Privacy Guarantee Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "Privacy & Local-First Security", style = SplitUpType.SectionTitle, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Your data stays private. SplitUp operates on a local-first model where your records remain securely on your device unless you explicitly enable cloud sync.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Legal & Financial Disclaimer Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "Legal & Financial Disclaimers", style = SplitUpType.CardTitle, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = "• Financial Disclaimer: SplitUp is a personal expense calculation and tracking tool. SplitUp is not a bank, escrow service, or licensed money transmitter, and does not hold, transmit, or process financial payments directly.\n\n• Trademark Notice: PayPal, Venmo, Revolut, and UPI are registered trademarks of their respective owners. SplitUp is an independent app and is not affiliated with or endorsed by these entities.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { UpdateChecker.openReleaseUrl(context, "https://splitupfin.vercel.app/privacy") },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.PrivacyTip, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Privacy Policy", style = SplitUpType.Caption)
                }

                OutlinedButton(
                    onClick = { UpdateChecker.openReleaseUrl(context, "https://splitupfin.vercel.app/terms") },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Gavel, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Terms", style = SplitUpType.Caption)
                }
            }

            var showLicensesDialog by remember { mutableStateOf(false) }

            Button(
                onClick = onStartDemoTour,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Replay Interactive Product Tour", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { showLicensesDialog = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open-Source Licenses")
            }

            if (showLicensesDialog) {
                LicensesDialog(onDismiss = { showLicensesDialog = false })
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun QuickActionItem(
    iconPainter: Painter?,
    imageVector: ImageVector?,
    label: String,
    containerColor: Color,
    contentColor: Color,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = containerColor,
            modifier = Modifier.size(52.dp),
            tonalElevation = 2.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = contentColor
                    )
                } else {
                    if (iconPainter != null) {
                        Icon(
                            painter = iconPainter,
                            contentDescription = label,
                            modifier = Modifier.size(24.dp),
                            tint = contentColor
                        )
                    } else if (imageVector != null) {
                        Icon(
                            imageVector = imageVector,
                            contentDescription = label,
                            modifier = Modifier.size(24.dp),
                            tint = contentColor
                        )
                    }
                }
            }
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open-Source Licenses", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LicenseItem("Jetpack Compose", "Apache 2.0", "Modern declarative UI toolkit for Android.")
                LicenseItem("Material 3", "Apache 2.0", "Material Design 3 components and theming.")
                LicenseItem("Jetpack Room", "Apache 2.0", "Robust SQLite database persistence library.")
                LicenseItem("CameraX", "Apache 2.0", "Consistent camera capture and live analysis.")
                LicenseItem("ZXing (Zebra Crossing)", "Apache 2.0", "QR code generation and barcode scanning.")
                LicenseItem("Coil", "Apache 2.0", "Image loading for Jetpack Compose.")
                LicenseItem("Retrofit & OkHttp", "Apache 2.0", "Type-safe HTTP client for network requests.")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun LicenseItem(name: String, license: String, desc: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = name, style = SplitUpType.ItemTitle, modifier = Modifier.weight(1f))
            Text(
                text = license,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false
            )
        }
        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
