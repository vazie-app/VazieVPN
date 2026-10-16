package app.vazie.vpn.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.vazie.vpn.MainActivity
import app.vazie.vpn.core.model.VazieAppIcon
import app.vazie.vpn.core.model.VazieAppIconPlate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One launcher entry: a mark and the plate it stands on. */
data class VazieLauncherFace(
    val icon: VazieAppIcon,
    val plate: VazieAppIconPlate,
) {
    init {
        require(icon.supports(plate)) {
            "${icon.id} is not offered on ${plate.id}: there is no alias for that pair"
        }
    }

    companion object {
        /** What a fresh install shows, and what `android:enabled="true"` names in the manifest. */
        val Default: VazieLauncherFace =
            VazieLauncherFace(VazieAppIcon.Default, VazieAppIcon.Default.signaturePlate)
    }
}

interface AppIconSwitcher {

    /** Make [face] the launcher entry, and stop being every other one. */
    suspend fun apply(face: VazieLauncherFace)

    /** Bring the system back in line with the stored choice. */
    suspend fun reconcile(face: VazieLauncherFace)

    companion object {
        /** Derived from `MainActivity`'s package; the mark+plate suffix must match the manifest
         * (`LauncherAliasTest`). */
        fun aliasClassName(face: VazieLauncherFace): String {
            val packageName = MainActivity::class.java.name.substringBeforeLast('.')
            val icon = when (face.icon) {
                VazieAppIcon.ORBIT -> "Orbit"
                VazieAppIcon.LIME -> "Lime"
                VazieAppIcon.EMERALD -> "Emerald"
                VazieAppIcon.MAGENTA -> "Magenta"
                VazieAppIcon.COTTON_CANDY -> "CottonCandy"
                VazieAppIcon.CRIMSON -> "Crimson"
                VazieAppIcon.GOLD -> "Gold"
                VazieAppIcon.PEARL -> "Pearl"
                VazieAppIcon.ONYX -> "Onyx"
                VazieAppIcon.MONOCHROME -> "Monochrome"
            }
            val plate = when (face.plate) {
                VazieAppIconPlate.DEEP -> "Deep"
                VazieAppIconPlate.LIGHT -> "Light"
                VazieAppIconPlate.INK -> "Ink"
                VazieAppIconPlate.FOREST -> "Forest"
                VazieAppIconPlate.ROSE -> "Rose"
                VazieAppIconPlate.AMBER -> "Amber"
                VazieAppIconPlate.MIST -> "Mist"
            }
            return "$packageName.MainActivityAlias$icon$plate"
        }

        /** Every launcher entry the manifest declares, in the order the manifest declares them. */
        val faces: List<VazieLauncherFace> = VazieAppIcon.entries.flatMap { icon ->
            icon.plates.map { plate -> VazieLauncherFace(icon, plate) }
        }
    }
}

/** The real one: the package manager, and nothing else. */
@Singleton
class ComponentAppIconSwitcher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AppIconSwitcher {

    /** `setComponentEnabledSetting` is a binder call, so it runs off the main thread. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default

    override suspend fun apply(face: VazieLauncherFace) = withContext(dispatcher) {
        val packages = context.packageManager
        runCatching {
            packages.setState(face, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            AppIconSwitcher.faces
                .filter { it != face }
                .forEach { packages.setState(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED) }
        }
        Unit
    }

    override suspend fun reconcile(face: VazieLauncherFace) {
        if (currentlyEnabled() == face) return
        apply(face)
    }

    private suspend fun currentlyEnabled(): VazieLauncherFace? = withContext(dispatcher) {
        runCatching {
            AppIconSwitcher.faces.firstOrNull { face ->
                when (context.packageManager.getComponentEnabledSetting(face.component())) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> face == VazieLauncherFace.Default
                    else -> false
                }
            }
        }.getOrNull()
    }

    private fun PackageManager.setState(face: VazieLauncherFace, state: Int) {
        setComponentEnabledSetting(face.component(), state, PackageManager.DONT_KILL_APP)
    }

    private fun VazieLauncherFace.component(): ComponentName =
        ComponentName(context.packageName, AppIconSwitcher.aliasClassName(this))
}
