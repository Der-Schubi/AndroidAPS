package app.aaps.plugins.constraints.safety

import app.aaps.core.data.aps.ApsMode
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.plugins.aps.openAPSAMA.DetermineBasalAMA
import app.aaps.plugins.aps.openAPSAMA.OpenAPSAMAPlugin
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalSMB
import app.aaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import app.aaps.plugins.source.GlimpPlugin
import app.aaps.pump.virtual.VirtualPumpPlugin
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class SafetyPluginTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock lateinit var glimpPlugin: GlimpPlugin
    @Mock lateinit var profiler: Profiler
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Mock lateinit var bgQualityCheck: BgQualityCheck
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var tddCalculator: TddCalculator
    @Mock lateinit var determineBasalAMA: DetermineBasalAMA
    @Mock lateinit var determineBasalSMB: DetermineBasalSMB

    private lateinit var safetyPlugin: SafetyPlugin
    private lateinit var openAPSAMAPlugin: OpenAPSAMAPlugin
    private lateinit var openAPSSMBPlugin: OpenAPSSMBPlugin

    private val pumpDescription = PumpDescription()

    @BeforeEach
    fun prepare() {
        `when`(rh.gs(app.aaps.plugins.constraints.R.string.hardlimit)).thenReturn("hard limit")
        `when`(rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)).thenReturn("it must be positive value")
        `when`(rh.gs(app.aaps.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        `when`(rh.gs(app.aaps.plugins.constraints.R.string.maxvalueinpreferences)).thenReturn("max value in preferences")
        `when`(rh.gs(app.aaps.plugins.aps.R.string.max_daily_basal_multiplier)).thenReturn("max daily basal multiplier")
        `when`(rh.gs(app.aaps.plugins.aps.R.string.max_basal_multiplier)).thenReturn("max basal multiplier")
        `when`(rh.gs(app.aaps.core.ui.R.string.limitingbolus)).thenReturn("Limiting bolus to %1\$.1f U because of %2\$s")
        `when`(rh.gs(app.aaps.core.ui.R.string.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        `when`(rh.gs(app.aaps.core.ui.R.string.limiting_iob)).thenReturn("Limiting IOB to %1\$.1f U because of %2\$s")
        `when`(rh.gs(app.aaps.plugins.constraints.R.string.limitingcarbs)).thenReturn("Limiting carbs to %1\$d g because of %2\$s")
        `when`(rh.gs(app.aaps.core.ui.R.string.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        `when`(rh.gs(app.aaps.plugins.constraints.R.string.pumpisnottempbasalcapable)).thenReturn("Pump is not temp basal capable")
        `when`(rh.gs(app.aaps.plugins.aps.R.string.increasing_max_basal)).thenReturn("Increasing max basal value because setting is lower than your max basal in profile")
        `when`(rh.gs(app.aaps.plugins.aps.R.string.smb_disabled_in_preferences)).thenReturn("SMB disabled in preferences")
        `when`(rh.gs(app.aaps.plugins.constraints.R.string.closedmodedisabledinpreferences)).thenReturn("Closed loop mode disabled in preferences")
        `when`(rh.gs(app.aaps.plugins.constraints.R.string.closed_loop_disabled_on_dev_branch)).thenReturn("Running dev version. Closed loop is disabled.")
        `when`(rh.gs(app.aaps.plugins.constraints.R.string.smbalwaysdisabled)).thenReturn("SMB always and after carbs disabled because active BG source doesn\\'t support advanced filtering")
        `when`(rh.gs(app.aaps.plugins.constraints.R.string.smbnotallowedinopenloopmode)).thenReturn("SMB not allowed in open loop mode")
        `when`(rh.gs(app.aaps.core.ui.R.string.lowglucosesuspend)).thenReturn("Low Glucose Suspend")

        `when`(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        `when`(virtualPumpPlugin.pumpDescription).thenReturn(pumpDescription)
        `when`(config.APS).thenReturn(true)
        safetyPlugin = SafetyPlugin(aapsLogger, rh, preferences, constraintChecker, activePlugin, hardLimits, config, persistenceLayer, dateUtil, uiInteraction, decimalFormatter)
        openAPSSMBPlugin =
            OpenAPSSMBPlugin(
                injector, aapsLogger, rxBus, constraintChecker, rh, profileFunction, profileUtil, config, activePlugin, iobCobCalculator,
                hardLimits, preferences, dateUtil, processedTbrEbData, persistenceLayer, glucoseStatusProvider, tddCalculator, bgQualityCheck,
                uiInteraction, determineBasalSMB, profiler
            )
        openAPSAMAPlugin =
            OpenAPSAMAPlugin(
                injector, aapsLogger, rxBus, constraintChecker, rh, config, profileFunction, activePlugin, iobCobCalculator, processedTbrEbData,
                hardLimits, dateUtil, persistenceLayer, glucoseStatusProvider, preferences, determineBasalAMA
            )
    }

    @Test
    fun pumpDescriptionShouldLimitLoopInvocation() {
        pumpDescription.isTempBasalCapable = false
        val c = safetyPlugin.isLoopInvocationAllowed(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).isEqualTo("Safety: Pump is not temp basal capable")
        assertThat(c.value()).isFalse()
    }

    @Test
    fun disabledEngineeringModeShouldLimitClosedLoop() {
        `when`(preferences.get(StringKey.LoopApsMode)).thenReturn(ApsMode.CLOSED.name)
        `when`(config.isEngineeringModeOrRelease()).thenReturn(false)
        val c = safetyPlugin.isClosedLoopAllowed(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).contains("Running dev version. Closed loop is disabled.")
        assertThat(c.value()).isFalse()
    }

    @Test
    fun setOpenLoopInPreferencesShouldLimitClosedLoop() {
        `when`(preferences.get(StringKey.LoopApsMode)).thenReturn(ApsMode.OPEN.name)
        val c = safetyPlugin.isClosedLoopAllowed(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).contains("Closed loop mode disabled in preferences")
        assertThat(c.value()).isFalse()
    }

    @Test
    fun notEnabledSMBInPreferencesDisablesSMB() {
        `when`(preferences.get(BooleanKey.ApsUseSmb)).thenReturn(false)
        `when`(constraintChecker.isClosedLoopAllowed(anyObject())).thenReturn(ConstraintObject(true, aapsLogger))
        val c = openAPSSMBPlugin.isSMBModeEnabled(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).contains("SMB disabled in preferences")
        assertThat(c.value()).isFalse()
    }

    @Test
    fun openLoopPreventsSMB() {
        `when`(preferences.get(BooleanKey.ApsUseSmb)).thenReturn(true)
        `when`(constraintChecker.isClosedLoopAllowed()).thenReturn(ConstraintObject(false, aapsLogger))
        val c = safetyPlugin.isSMBModeEnabled(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).contains("SMB not allowed in open loop mode")
        assertThat(c.value()).isFalse()
    }

    @Test
    fun bgSourceShouldPreventSMBAlways() {
        `when`(activePlugin.activeBgSource).thenReturn(glimpPlugin)
        val c = safetyPlugin.isAdvancedFilteringEnabled(ConstraintObject(true, aapsLogger))
        assertThat(c.getReasons()).isEqualTo("Safety: SMB always and after carbs disabled because active BG source doesn\\'t support advanced filtering")
        assertThat(c.value()).isFalse()
    }

    @Test
    fun basalRateShouldBeLimited() {
        `when`(preferences.get(DoubleKey.ApsMaxBasal)).thenReturn(1.0)
        `when`(preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier)).thenReturn(4.0)
        `when`(preferences.get(DoubleKey.ApsMaxDailyMultiplier)).thenReturn(3.0)
        `when`(preferences.get(StringKey.SafetyAge)).thenReturn("child")
        val c = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        safetyPlugin.applyBasalConstraints(c, validProfile)
        assertThat(c.value()).isWithin(0.01).of(2.0)
        assertThat(c.getReasons()).isEqualTo(
            """
    Safety: Limiting max basal rate to 2.00 U/h because of hard limit
    """.trimIndent()
        )
        assertThat(c.getMostLimitedReasons()).isEqualTo("Safety: Limiting max basal rate to 2.00 U/h because of hard limit")
    }

    @Test
    fun doNotAllowNegativeBasalRate() {
        `when`(preferences.get(StringKey.SafetyAge)).thenReturn("child")
        val d = ConstraintObject(-0.5, aapsLogger)
        safetyPlugin.applyBasalConstraints(d, validProfile)
        assertThat(d.value()).isWithin(0.01).of(0.0)
        assertThat(d.getReasons()).isEqualTo(
            "Safety: Limiting max basal rate to 0.00 U/h because of it must be positive value"
        )
    }

    @Test
    fun percentBasalRateShouldBeLimited() {
        // No limit by default
        `when`(preferences.get(DoubleKey.ApsMaxBasal)).thenReturn(1.0)
        `when`(preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier)).thenReturn(4.0)
        `when`(preferences.get(DoubleKey.ApsMaxDailyMultiplier)).thenReturn(3.0)
        `when`(preferences.get(StringKey.SafetyAge)).thenReturn("child")
        val i = ConstraintObject(Int.MAX_VALUE, aapsLogger)
        safetyPlugin.applyBasalPercentConstraints(i, validProfile)
        assertThat(i.value()).isEqualTo(200)
        assertThat(i.getReasons()).isEqualTo(
            """
Safety: Percent rate 2147483647% recalculated to 21474836.47 U/h with current basal 1.00 U/h
Safety: Limiting max basal rate to 2.00 U/h because of hard limit
Safety: Limiting max percent rate to 200% because of pump limit
Safety: Limiting max basal rate to 500.00 U/h because of pump limit
    """.trimIndent()
        )
        assertThat(i.getMostLimitedReasons()).isEqualTo(
            "Safety: Limiting max percent rate to 200% because of pump limit"
        )
    }

    @Test
    fun percentBasalShouldBeLimitedBySMB() {
        // No limit by default
        `when`(preferences.get(DoubleKey.ApsMaxBasal)).thenReturn(1.0)
        `when`(preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier)).thenReturn(4.0)
        `when`(preferences.get(DoubleKey.ApsMaxDailyMultiplier)).thenReturn(3.0)
        `when`(preferences.get(StringKey.SafetyAge)).thenReturn("child")
        openAPSSMBPlugin.setPluginEnabled(PluginType.APS, true)
        val i = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        openAPSSMBPlugin.applyBasalConstraints(i, validProfile)
        assertThat(i.value()).isWithin(0.01).of(1.0)
        assertThat(i.getReasons()).isEqualTo(
            """
            OpenAPSSMB: Limiting max basal rate to 1.00 U/h because of max value in preferences
            OpenAPSSMB: Limiting max basal rate to 4.00 U/h because of max basal multiplier
            OpenAPSSMB: Limiting max basal rate to 3.00 U/h because of max daily basal multiplier
            """.trimIndent()
        )
        assertThat(i.getMostLimitedReasons()).isEqualTo("OpenAPSSMB: Limiting max basal rate to 1.00 U/h because of max value in preferences")
    }

    @Test
    fun doNotAllowNegativePercentBasalRate() {
        `when`(preferences.get(StringKey.SafetyAge)).thenReturn("child")
        val i = ConstraintObject(-22, aapsLogger)
        safetyPlugin.applyBasalPercentConstraints(i, validProfile)
        assertThat(i.value()).isEqualTo(0)
        assertThat(i.getReasons()).isEqualTo(
            """
    Safety: Percent rate -22% recalculated to -0.22 U/h with current basal 1.00 U/h
    Safety: Limiting max basal rate to 0.00 U/h because of it must be positive value
    Safety: Limiting max percent rate to 0% because of pump limit
    """.trimIndent()
        )
        assertThat(i.getMostLimitedReasons()).isEqualTo("Safety: Limiting max percent rate to 0% because of pump limit")
    }

    @Test
    fun bolusAmountShouldBeLimited() {
        `when`(preferences.get(DoubleKey.SafetyMaxBolus)).thenReturn(3.0)
        `when`(preferences.get(StringKey.SafetyAge)).thenReturn("child")
        val d = safetyPlugin.applyBolusConstraints(ConstraintObject(Double.MAX_VALUE, aapsLogger))
        assertThat(d.value()).isWithin(0.01).of(3.0)
        assertThat(d.getReasons()).isEqualTo(
            """
    Safety: Limiting bolus to 3.0 U because of max value in preferences
    Safety: Limiting bolus to 5.0 U because of hard limit
    """.trimIndent()
        )
        assertThat(d.getMostLimitedReasons()).isEqualTo("Safety: Limiting bolus to 3.0 U because of max value in preferences")
    }

    @Test
    fun doNotAllowNegativeBolusAmount() {
        `when`(preferences.get(DoubleKey.SafetyMaxBolus)).thenReturn(3.0)
        `when`(preferences.get(StringKey.SafetyAge)).thenReturn("child")
        val d = safetyPlugin.applyBolusConstraints(ConstraintObject(-22.0, aapsLogger))
        assertThat(d.value()).isWithin(0.01).of(0.0)
        assertThat(d.getReasons()).isEqualTo("Safety: Limiting bolus to 0.0 U because of it must be positive value")
        assertThat(d.getMostLimitedReasons()).isEqualTo("Safety: Limiting bolus to 0.0 U because of it must be positive value")
    }

    @Test
    fun carbsAmountShouldBeLimited() {
        // No limit by default
        `when`(preferences.get(IntKey.SafetyMaxCarbs)).thenReturn(48)

        // Apply all limits
        val i = safetyPlugin.applyCarbsConstraints(ConstraintObject(Int.MAX_VALUE, aapsLogger))
        assertThat(i.value()).isEqualTo(48)
        assertThat(i.getReasons()).isEqualTo("Safety: Limiting carbs to 48 g because of max value in preferences")
    }

    @Test
    fun iobShouldBeLimited() {
        openAPSAMAPlugin.setPluginEnabled(PluginType.APS, true)
        openAPSSMBPlugin.setPluginEnabled(PluginType.APS, true)
        //`when`(openAPSSMBPlugin.isEnabled()).thenReturn(true)
        //`when`(openAPSAMAPlugin.isEnabled()).thenReturn(false)
        `when`(preferences.get(StringKey.LoopApsMode)).thenReturn(ApsMode.LGS.name)
        `when`(preferences.get(DoubleKey.ApsAmaMaxIob)).thenReturn(1.5)
        `when`(preferences.get(DoubleKey.ApsSmbMaxIob)).thenReturn(3.0)
        `when`(preferences.get(StringKey.SafetyAge)).thenReturn("teenage")

        // Apply all limits
        var d: Constraint<Double> = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        d = safetyPlugin.applyMaxIOBConstraints(d)
        assertThat(d.value()).isWithin(0.01).of(HardLimits.MAX_IOB_LGS)
        assertThat(d.getReasons()).isEqualTo("Safety: Limiting IOB to 0.0 U because of Low Glucose Suspend")
        assertThat(d.getMostLimitedReasons()).isEqualTo("Safety: Limiting IOB to 0.0 U because of Low Glucose Suspend")

        // Apply all limits
        d = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        val a = openAPSAMAPlugin.applyMaxIOBConstraints(d)
        assertThat(a.value()).isWithin(0.01).of(1.5)
        assertThat(d.getReasons()).isEqualTo("OpenAPSAMA: Limiting IOB to 1.5 U because of max value in preferences\nOpenAPSAMA: Limiting IOB to 5.0 U because of hard limit")
        assertThat(d.getMostLimitedReasons()).isEqualTo("OpenAPSAMA: Limiting IOB to 1.5 U because of max value in preferences")

        // Apply all limits
        d = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        val s = openAPSSMBPlugin.applyMaxIOBConstraints(d)
        assertThat(s.value()).isWithin(0.01).of(3.0)
        assertThat(d.getReasons()).isEqualTo("OpenAPSSMB: Limiting IOB to 3.0 U because of max value in preferences\nOpenAPSSMB: Limiting IOB to 13.0 U because of hard limit")
        assertThat(d.getMostLimitedReasons()).isEqualTo("OpenAPSSMB: Limiting IOB to 3.0 U because of max value in preferences")
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        safetyPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
