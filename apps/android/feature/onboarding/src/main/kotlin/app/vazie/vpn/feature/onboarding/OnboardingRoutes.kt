package app.vazie.vpn.feature.onboarding

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.vazie.vpn.core.navigation.OnboardingNavigator
import kotlinx.serialization.Serializable

/** All pages in one destination, so back does not walk through the carousel. No deep link. */
@Serializable
data object OnboardingGraphRoute

@Serializable
data object OnboardingRoute

fun NavGraphBuilder.onboardingGraph(navigator: OnboardingNavigator) {
    navigation<OnboardingGraphRoute>(startDestination = OnboardingRoute) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onFinish = navigator::toMain,
                onAddConfig = navigator::toAddConfig,
            )
        }
    }
}
