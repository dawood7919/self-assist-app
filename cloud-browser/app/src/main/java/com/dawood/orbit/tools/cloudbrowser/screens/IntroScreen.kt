package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.dawood.orbit.tools.cloudbrowser.CloudBody
import com.dawood.orbit.tools.cloudbrowser.CloudCard
import com.dawood.orbit.tools.cloudbrowser.CloudColors
import com.dawood.orbit.tools.cloudbrowser.CloudPrimaryButton
import com.dawood.orbit.tools.cloudbrowser.CloudSmall
import com.dawood.orbit.tools.cloudbrowser.CloudSpacing
import com.dawood.orbit.tools.cloudbrowser.CloudTitle

/**
 * First-run intro for the Cloud Browser tool (mockup slot 14).
 *
 * Exact visual copy of the mockup: gradient hero, 2x2 feature grid,
 * Get Started button. [onStart] persists `introSeen` and routes Home;
 * the tool owns that wiring, this screen only reports the tap.
 */
@Composable
fun IntroScreen(onStart: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadMd),
    ) {
        Hero()
        Row(horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadMd)) {
            FeatureCard(
                emoji = "🖥",
                title = "Remote Browser",
                detail = "Browse using your VPS",
                modifier = Modifier.weight(1f),
            )
            FeatureCard(
                emoji = "🌐",
                title = "VPS Internet",
                detail = "Use your server's IP",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadMd)) {
            FeatureCard(
                emoji = "⚙",
                title = "Remote Processing",
                detail = "Runs on the server",
                modifier = Modifier.weight(1f),
            )
            FeatureCard(
                emoji = "🔒",
                title = "Secure Connection",
                detail = "Encrypted end to end",
                modifier = Modifier.weight(1f),
            )
        }
        CloudPrimaryButton(text = "Get Started", onClick = onStart)
    }
}

@Composable
private fun Hero(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CloudSpacing.HeroHeight)
            .clip(RoundedCornerShape(CloudColors.CardRadius))
            .background(
                Brush.verticalGradient(
                    listOf(CloudColors.HeroTop, CloudColors.HeroMid, CloudColors.Bg),
                ),
            )
            .padding(CloudColors.CardPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadXs),
        ) {
            BasicText(
                text = "☁️",
                style = TextStyle(fontSize = CloudColors.HeroEmojiSize),
            )
            CloudTitle(text = "Cloud Browser", align = TextAlign.Center)
            CloudSmall(
                text = "Your VPS. Your Internet. Your Browser.",
                align = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FeatureCard(emoji: String, title: String, detail: String, modifier: Modifier = Modifier) {
    CloudCard(modifier = modifier) {
        BasicText(
            text = emoji,
            style = TextStyle(fontSize = CloudColors.WordmarkSize),
        )
        CloudBody(text = title, bold = true)
        CloudSmall(text = detail)
    }
}
