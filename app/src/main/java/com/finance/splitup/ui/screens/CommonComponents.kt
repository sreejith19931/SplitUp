package com.finance.splitup.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

fun getInitials(name: String): String {
    val parts = name.trim().split("\\s+".toRegex())
    return if (parts.size >= 2) {
        "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
    } else if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
        parts[0].take(2).uppercase()
    } else {
        "U"
    }
}

fun String.parseAmount(): Double {
    if (this.isBlank()) return 0.0
    val cleaned = this.replace(Regex("""[$€£₹\s]"""), "")
    val normalized = if (cleaned.contains(",") && cleaned.contains(".")) {
        val lastComma = cleaned.lastIndexOf(',')
        val lastDot = cleaned.lastIndexOf('.')
        if (lastComma > lastDot) {
            cleaned.replace(".", "").replace(",", ".")
        } else {
            cleaned.replace(",", "")
        }
    } else if (cleaned.contains(",")) {
        cleaned.replace(",", ".")
    } else {
        cleaned
    }
    return normalized.toDoubleOrNull() ?: 0.0
}

fun formatInputNumber(value: Double): String {
    if (value <= 0.0) return ""
    return if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", value)
    }
}

fun detectCategoryFromTitle(title: String): String {
    return com.finance.splitup.data.CategoryDetector.detectCategoryFromTitle(title)
}

@Composable
fun AnimatedAmountText(
    targetAmount: Double,
    currencySymbol: String = "€",
    prefix: String = "",
    suffix: String = "",
    fontSize: androidx.compose.ui.unit.TextUnit = 22.sp,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    decimalPlaces: Int = 2,
    durationMillis: Int = 850,
    modifier: Modifier = Modifier
) {
    var isLaunched by remember { mutableStateOf(false) }
    val animatedValue by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isLaunched) targetAmount.toFloat() else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = durationMillis,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "animatedAmountText"
    )

    LaunchedEffect(targetAmount) {
        isLaunched = true
    }

    val formatString = if (decimalPlaces == 0) "%.0f" else "%.${decimalPlaces}f"
    val formattedText = remember(animatedValue, currencySymbol, prefix, suffix, decimalPlaces) {
        val amountStr = String.format(Locale.US, formatString, animatedValue.toDouble())
        "$prefix$currencySymbol$amountStr$suffix"
    }

    Text(
        text = formattedText,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        style = style,
        modifier = modifier
    )
}

@Composable
fun MemberAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    textColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = backgroundColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = getInitials(name),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableItemContainer(
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()

    androidx.compose.runtime.LaunchedEffect(dismissState.targetValue) {
        when (dismissState.targetValue) {
            SwipeToDismissBoxValue.EndToStart -> {
                onDelete()
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }
            SwipeToDismissBoxValue.StartToEnd -> {
                onEdit()
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }
            SwipeToDismissBoxValue.Settled -> {}
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                else -> Color.Transparent
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.Center
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> Icons.Rounded.Delete
                SwipeToDismissBoxValue.StartToEnd -> Icons.Rounded.Edit
                else -> null
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                color = color
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = alignment
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            }
        },
        content = {
            content()
        }
    )
}

fun getCategoryColor(category: String): Color {
    return when (category) {
        com.finance.splitup.data.ExpenseCategories.FOOD -> com.finance.splitup.ui.theme.LogoPrimaryBlue // Cobalt Blue
        com.finance.splitup.data.ExpenseCategories.TRANSPORT -> com.finance.splitup.ui.theme.LogoEmeraldTeal // Emerald Teal
        com.finance.splitup.data.ExpenseCategories.SHOPPING -> com.finance.splitup.ui.theme.LogoCyan // Cyan
        com.finance.splitup.data.ExpenseCategories.BILLS -> com.finance.splitup.ui.theme.LogoLightBlue // Light Blue
        com.finance.splitup.data.ExpenseCategories.ENTERTAINMENT -> com.finance.splitup.ui.theme.LogoGold // Gold
        com.finance.splitup.data.ExpenseCategories.INCOME -> com.finance.splitup.ui.theme.EmeraldSuccess
        else -> Color(0xFF475569) // Slate
    }
}

fun getCategoryIcon(category: String): ImageVector {
    return when (category) {
        com.finance.splitup.data.ExpenseCategories.FOOD -> Icons.Rounded.Restaurant
        com.finance.splitup.data.ExpenseCategories.TRANSPORT -> Icons.Rounded.DirectionsCar
        com.finance.splitup.data.ExpenseCategories.SHOPPING -> Icons.Rounded.ShoppingBag
        com.finance.splitup.data.ExpenseCategories.BILLS -> Icons.AutoMirrored.Rounded.ReceiptLong
        com.finance.splitup.data.ExpenseCategories.ENTERTAINMENT -> Icons.Rounded.Movie
        com.finance.splitup.data.ExpenseCategories.INCOME -> Icons.Rounded.AttachMoney
        else -> Icons.Rounded.Category
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerFieldToModal(
    selectedDateText: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Expense Date"
) {
    var showDatePickerDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = selectedDateText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Rounded.CalendarToday,
                    contentDescription = "Calendar",
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                IconButton(onClick = { showDatePickerDialog = true }) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.DateRange,
                        contentDescription = "Select Date from Calendar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.primary
            )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showDatePickerDialog = true }
        )
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis <= System.currentTimeMillis()
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy", java.util.Locale.US)
                            val instant = java.time.Instant.ofEpochMilli(millis)
                            val localDate = instant.atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                            val formatted = localDate.format(formatter)
                            onDateSelected(formatted)
                        }
                        showDatePickerDialog = false
                    }
                ) {
                    Text("Select", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun ReceiptImageViewerDialog(
    imagePath: String,
    onDismiss: () -> Unit
) {
    val bitmap = remember(imagePath) {
        try {
            android.graphics.BitmapFactory.decodeFile(imagePath)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Receipt Photo (Zoom & Pan)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.05f))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Zoomable Scanned Receipt Photo",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Text("Could not load receipt photo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (scale > 1f) {
                    TextButton(onClick = { scale = 1f; offsetX = 0f; offsetY = 0f }) {
                        Text("Reset")
                    }
                }
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
fun WhatsAppInviteDialog(
    memberName: String,
    groupName: String,
    groupId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val inviteUrl = "splitup://invite?groupId=$groupId"
    val inviteMessage = "Hey $memberName! I added you to our SplitUp group '$groupName'. Download SplitUp and join here: $inviteUrl"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send WhatsApp Invite", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Since '$memberName' is a custom member or not registered on SplitUp, send them an invite link via WhatsApp!")
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clickable {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(inviteUrl))
                            Toast.makeText(context, "Invite link copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = inviteMessage,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "🔗 Click to copy invite link: $inviteUrl",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        setPackage("com.whatsapp")
                        putExtra(Intent.EXTRA_TEXT, inviteMessage)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val fallback = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, inviteMessage)
                        }
                        context.startActivity(Intent.createChooser(fallback, "Share App Invite"))
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Send via WhatsApp", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        }
    )
}

@Composable
fun ProfileQRCodeDialog(
    name: String,
    email: String,
    onDismiss: () -> Unit
) {
    val qrText = "splitup://profile?email=$email&name=$name"
    val qrBitmap = remember(qrText) {
        try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val hints = mapOf(com.google.zxing.EncodeHintType.MARGIN to 1)
            val bitMatrix = writer.encode(qrText, com.google.zxing.BarcodeFormat.QR_CODE, 256, 256, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("My SplitUp Profile QR", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MemberAvatar(name = name, size = 64.dp)
                Text(name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                if (qrBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Profile QR Code",
                            modifier = Modifier.size(200.dp).padding(12.dp)
                        )
                    }
                } else {
                    Text("Could not generate QR code", color = MaterialTheme.colorScheme.error)
                }
                Text("Friends can scan this QR code to add you instantly.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}




