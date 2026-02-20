package com.extensionbox.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.extensionbox.app.BuildConfig
import com.extensionbox.app.R
import com.extensionbox.app.network.UpdateChecker
import kotlinx.coroutines.launch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import android.net.Uri
import android.content.Intent
import androidx.compose.ui.platform.LocalUriHandler

@Composable
fun AboutScreen() {
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var updateStatus by remember { mutableStateOf("Check for Updates") }
    var isChecking by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "Logo",
                    modifier = Modifier.size(100.dp)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Extension Box",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "A modern, lightweight system monitoring tool with a modular architecture built for Android enthusiasts.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 24.sp
                )
            }
        }

        Button(
            onClick = {
                if (!isChecking) {
                    isChecking = true
                    updateStatus = "Checking..."
                    coroutineScope.launch {
                        try {
                            val service = UpdateChecker.getGitHubService()
                            val releases = service.getReleases("suvojeet-sengupta", "ExtensionBox")
                            if (releases.isNotEmpty()) {
                                val latest = releases[0].tagName
                                updateStatus = if (latest.contains(BuildConfig.VERSION_NAME)) {
                                    "✨ You are up to date!"
                                } else {
                                    "New update: $latest"
                                }
                            }
                        } catch (e: Exception) {
                            updateStatus = "Check failed"
                        } finally {
                            isChecking = false
                        }
                    }
                }
            },
            enabled = !isChecking,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(updateStatus)
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Developers",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DeveloperChip(
                name = "Suvojeet",
                role = "Lead Developer",
                github = "https://github.com/suvojeet-sengupta",
                onCli = { uriHandler.openUri(it) }
            )
            DeveloperChip(
                name = "Omer",
                role = "Core Contributor",
                github = "https://github.com/omersusin",
                onCli = { uriHandler.openUri(it) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun DeveloperChip(name: String, role: String, github: String, onCli: (String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = CircleShape,
        modifier = Modifier.clickable { onCli(github) }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = role,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}
