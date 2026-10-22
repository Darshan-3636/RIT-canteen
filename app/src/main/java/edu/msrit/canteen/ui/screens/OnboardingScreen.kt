package edu.msrit.canteen.ui.screens

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import edu.msrit.canteen.onboarding.OnboardingManifest
import edu.msrit.canteen.onboarding.OnboardingPage
import edu.msrit.canteen.onboarding.ThemeId
import edu.msrit.canteen.ui.theme.RitThemeTokens
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = OnboardingManifest.pages
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    val view = LocalView.current
    val systemDark = RitThemeTokens.colors.isDark
    SideEffect {
        activity?.let {
            val controller = WindowCompat.getInsetsController(it.window, view)
            controller.isAppearanceLightStatusBars = pages[pager.currentPage].theme.id == ThemeId.Turing
            controller.isAppearanceLightNavigationBars = pages[pager.currentPage].theme.id == ThemeId.Turing
        }
    }
    DisposableEffect(activity, view) {
        onDispose {
            activity?.let {
                val controller = WindowCompat.getInsetsController(it.window, view)
                controller.isAppearanceLightStatusBars = !systemDark
                controller.isAppearanceLightNavigationBars = !systemDark
            }
        }
    }
    fun goTo(index: Int) {
        scope.launch {
            if (ValueAnimator.areAnimatorsEnabled()) pager.animateScrollToPage(index)
            else pager.scrollToPage(index)
        }
    }
    BackHandler(pager.currentPage > 0) { goTo(OnboardingManifest.previousIndex(pager.currentPage)) }
    Box(Modifier.fillMaxSize().background(Color(0xFF111925))) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
            OnboardingArtworkPage(pages[index])
        }
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 18.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TopAction(if (pager.currentPage > 0) "Back" else "", pager.currentPage > 0) {
                goTo(OnboardingManifest.previousIndex(pager.currentPage))
            }
            TopAction("Skip", true, onFinish)
        }
        val page = pages[pager.currentPage]
        val textColor = if (page.theme.id == ThemeId.Turing) Color(0xFF202633) else Color.White
        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 26.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                pages.indices.forEach { index ->
                    val current = index == pager.currentPage
                    Box(Modifier.width(if (current) 23.dp else 7.dp).height(7.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (current) Color(0xFFFF7047) else textColor.copy(alpha = .32f))
                        .semantics { contentDescription = "Page ${index + 1} of ${pages.size}${if (current) ", current" else ""}" })
                }
            }
            Box(
                Modifier.clip(RoundedCornerShape(100.dp)).background(Color(0xFFFF653D))
                    .clickable(role = Role.Button) {
                        if (pager.currentPage == pages.lastIndex) onFinish() else goTo(OnboardingManifest.nextIndex(pager.currentPage))
                    }.padding(horizontal = 24.dp, vertical = 15.dp)
                    .semantics { contentDescription = if (pager.currentPage == pages.lastIndex) "Finish onboarding" else "Next page" },
            ) {
                BasicText(if (pager.currentPage == pages.lastIndex) "Get Started" else "Next  →",
                    style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun TopAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(100.dp)).background(if (enabled) Color(0x990D1620) else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 11.dp),
    ) {
        BasicText(label, style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun OnboardingArtworkPage(page: OnboardingPage) {
    val paper = page.theme.id == ThemeId.Turing
    val surface = if (paper) Color(0xFFF6F0E5) else Color(0xFF111925)
    val foreground = if (paper) Color(0xFF202633) else Color.White
    val muted = if (paper) Color(0xFF575C62) else Color(0xFFD5DFE8)
    Column(Modifier.fillMaxSize().background(surface)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Image(
                painterResource(page.theme.artwork), page.theme.description,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
        }
        Column(Modifier.fillMaxWidth().background(surface).padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 116.dp)) {
            BasicText("0${OnboardingManifest.pages.indexOf(page) + 1}  /  0${OnboardingManifest.pages.size}",
                style = TextStyle(color = if (paper) Color(0xFFD05939) else Color(0xFFFFA484), fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp))
            Spacer(Modifier.height(10.dp))
            BasicText(page.content.title, style = TextStyle(color = foreground, fontSize = 34.sp,
                lineHeight = 38.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(9.dp))
            BasicText(page.content.subtitle, style = TextStyle(color = muted, fontSize = 15.sp, lineHeight = 21.sp))
        }
    }
}
