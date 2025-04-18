package app.aaps.ui.dialogs

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.fragment.app.FragmentManager
import app.aaps.core.data.aps.ApsMode
import app.aaps.core.data.model.OE
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.ui.R
import app.aaps.ui.databinding.DialogLoopBinding
import dagger.android.HasAndroidInjector
import dagger.android.support.DaggerDialogFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class LoopDialog : DaggerDialogFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var ctx: Context
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var loop: Loop
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var injector: HasAndroidInjector

    private var queryingProtection = false
    private var showOkCancel: Boolean = true
    private var _binding: DialogLoopBinding? = null
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshDialog: Runnable

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    val disposable = CompositeDisposable()

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putInt("showOkCancel", if (showOkCancel) 1 else 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            showOkCancel = bundle.getInt("showOkCancel", 1) == 1
        }
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)

        _binding = DialogLoopBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        updateGUI("LoopDialogOnViewCreated")

        binding.overviewCloseloop.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewLgsloop.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewOpenloop.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewDisable.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewEnable.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewResume.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewReconnect.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewSuspend1h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewSuspend2h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewSuspend3h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewSuspend10h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewDisconnect15m.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewDisconnect30m.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewDisconnect1h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewDisconnect3h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }
        binding.overviewDisconnect6h.setOnClickListener { if (showOkCancel) onClickOkCancelEnabled(it) else onClick(it); dismiss() }

        // cancel button
        binding.cancel.setOnClickListener { dismiss() }

        refreshDialog = Runnable {
            runOnUiThread { updateGUI("refreshDialog") }
            handler.postDelayed(refreshDialog, 15 * 1000L)
        }
        handler.postDelayed(refreshDialog, 15 * 1000L)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        handler.removeCallbacksAndMessages(null)
        disposable.clear()
    }

    @Synchronized
    fun updateGUI(from: String) {
        if (_binding == null) return
        aapsLogger.debug("UpdateGUI from $from")
        val pumpDescription: PumpDescription = activePlugin.activePump.pumpDescription
        val closedLoopAllowed = constraintChecker.isClosedLoopAllowed(ConstraintObject(true, aapsLogger))
        val closedLoopAllowed2 = activePlugin.activeObjectives?.isAccomplished(Objectives.MAXIOB_OBJECTIVE) == true
        val lgsEnabled = constraintChecker.isLgsAllowed(ConstraintObject(true, aapsLogger))
        val apsMode = ApsMode.fromString(preferences.get(StringKey.LoopApsMode))
        val pump = activePlugin.activePump

        binding.overviewDisconnect15m.visibility = pumpDescription.tempDurationStep15mAllowed.toVisibility()
        binding.overviewDisconnect30m.visibility = pumpDescription.tempDurationStep30mAllowed.toVisibility()
        when {
            pump.isSuspended()                                     -> {
                binding.overviewLoop.visibility = View.GONE
                binding.overviewSuspend.visibility = View.GONE
                binding.overviewPump.visibility = View.GONE
            }

            !profileFunction.isProfileValid("LoopDialogUpdateGUI") -> {
                binding.overviewLoop.visibility = View.GONE
                binding.overviewSuspend.visibility = View.GONE
                binding.overviewPump.visibility = View.GONE
            }

            loop.isDisconnected                                    -> {
                binding.overviewLoop.visibility = View.GONE
                binding.overviewSuspend.visibility = View.GONE
                binding.overviewPump.visibility = View.VISIBLE
                binding.overviewPumpHeader.text = rh.gs(R.string.reconnect)
                binding.overviewDisconnectButtons.visibility = View.VISIBLE
                binding.overviewReconnect.visibility = View.VISIBLE
            }

            !loop.isEnabled()                                      -> {
                binding.overviewLoop.visibility = View.VISIBLE
                binding.overviewEnable.visibility = View.VISIBLE
                binding.overviewDisable.visibility = View.GONE
                binding.overviewSuspend.visibility = View.GONE
                binding.overviewPump.visibility = View.VISIBLE
                binding.overviewReconnect.visibility = View.GONE
            }

            loop.isSuspended                                       -> {
                binding.overviewLoop.visibility = View.GONE
                binding.overviewSuspend.visibility = View.VISIBLE
                binding.overviewSuspendHeader.text = rh.gs(app.aaps.core.ui.R.string.resumeloop)
                binding.overviewSuspendButtons.visibility = View.VISIBLE
                binding.overviewResume.visibility = View.VISIBLE
                binding.overviewPump.visibility = View.GONE
                binding.overviewReconnect.visibility = View.GONE
            }

            else                                                   -> {
                binding.overviewLoop.visibility = View.VISIBLE
                binding.overviewEnable.visibility = View.GONE
                when (apsMode) {
                    ApsMode.CLOSED -> {
                        binding.overviewCloseloop.visibility = View.GONE
                        binding.overviewLgsloop.visibility = View.VISIBLE
                        binding.overviewOpenloop.visibility = View.VISIBLE
                    }

                    ApsMode.LGS    -> {
                        binding.overviewCloseloop.visibility = closedLoopAllowed.value().toVisibility()   //show Close loop button only if Close loop allowed
                        binding.overviewLgsloop.visibility = View.GONE
                        binding.overviewOpenloop.visibility = View.VISIBLE
                    }

                    ApsMode.OPEN   -> {
                        binding.overviewCloseloop.visibility =
                            closedLoopAllowed2.toVisibility()          //show CloseLoop button only if Objective 6 is completed (closedLoopAllowed always false in open loop mode)
                        binding.overviewLgsloop.visibility = lgsEnabled.value().toVisibility()
                        binding.overviewOpenloop.visibility = View.GONE
                    }

                    else           -> {
                        binding.overviewCloseloop.visibility = View.GONE
                        binding.overviewLgsloop.visibility = View.GONE
                        binding.overviewOpenloop.visibility = View.GONE
                    }
                }
                binding.overviewSuspend.visibility = View.VISIBLE
                binding.overviewSuspendHeader.text = rh.gs(app.aaps.core.ui.R.string.suspendloop)
                binding.overviewSuspendButtons.visibility = View.VISIBLE
                binding.overviewResume.visibility = View.GONE

                binding.overviewPump.visibility = View.VISIBLE
                binding.overviewPumpHeader.text = rh.gs(R.string.disconnectpump)
                binding.overviewDisconnectButtons.visibility = View.VISIBLE
                binding.overviewReconnect.visibility = View.GONE

            }
        }
    }

    private fun onClickOkCancelEnabled(v: View): Boolean {
        var description = ""
        when (v.id) {
            R.id.overview_closeloop      -> description = rh.gs(app.aaps.core.ui.R.string.closedloop)
            R.id.overview_lgsloop        -> description = rh.gs(app.aaps.core.ui.R.string.lowglucosesuspend)
            R.id.overview_openloop       -> description = rh.gs(app.aaps.core.ui.R.string.openloop)
            R.id.overview_disable        -> description = rh.gs(app.aaps.core.ui.R.string.disableloop)
            R.id.overview_enable         -> description = rh.gs(app.aaps.core.ui.R.string.enableloop)
            R.id.overview_resume         -> description = rh.gs(R.string.resume)
            R.id.overview_reconnect      -> description = rh.gs(R.string.reconnect)
            R.id.overview_suspend_1h     -> description = rh.gs(R.string.suspendloopfor1h)
            R.id.overview_suspend_2h     -> description = rh.gs(R.string.suspendloopfor2h)
            R.id.overview_suspend_3h     -> description = rh.gs(R.string.suspendloopfor3h)
            R.id.overview_suspend_10h    -> description = rh.gs(R.string.suspendloopfor10h)
            R.id.overview_disconnect_15m -> description = rh.gs(R.string.disconnectpumpfor15m)
            R.id.overview_disconnect_30m -> description = rh.gs(R.string.disconnectpumpfor30m)
            R.id.overview_disconnect_1h  -> description = rh.gs(R.string.disconnectpumpfor1h)
            R.id.overview_disconnect_3h  -> description = rh.gs(R.string.disconnectpumpfor3h)
            R.id.overview_disconnect_6h  -> description = rh.gs(R.string.disconnectpumpfor6h)
        }
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.confirm), description, Runnable {
                onClick(v)
            })
        }
        return true
    }

    private fun onClick(v: View): Boolean {
        when (v.id) {
            R.id.overview_closeloop                       -> {
                uel.log(Action.CLOSED_LOOP_MODE, Sources.LoopDialog)
                preferences.put(StringKey.LoopApsMode, ApsMode.CLOSED.name)
                rxBus.send(EventPreferenceChange(rh.gs(app.aaps.core.ui.R.string.closedloop)))
                return true
            }

            R.id.overview_lgsloop                         -> {
                uel.log(Action.LGS_LOOP_MODE, Sources.LoopDialog)
                preferences.put(StringKey.LoopApsMode, ApsMode.LGS.name)
                rxBus.send(EventPreferenceChange(rh.gs(app.aaps.core.ui.R.string.lowglucosesuspend)))
                return true
            }

            R.id.overview_openloop                        -> {
                uel.log(Action.OPEN_LOOP_MODE, Sources.LoopDialog)
                preferences.put(StringKey.LoopApsMode, ApsMode.OPEN.name)
                rxBus.send(EventPreferenceChange(rh.gs(app.aaps.core.ui.R.string.lowglucosesuspend)))
                return true
            }

            R.id.overview_disable                         -> {
                (loop as PluginBase).setPluginEnabled(PluginType.LOOP, false)
                (loop as PluginBase).setFragmentVisible(PluginType.LOOP, false)
                configBuilder.storeSettings("DisablingLoop")
                rxBus.send(EventRefreshOverview("suspend_menu"))
                commandQueue.cancelTempBasal(true, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            ToastUtils.errorToast(ctx, rh.gs(app.aaps.core.ui.R.string.temp_basal_delivery_error))
                        }
                    }
                })
                disposable += persistenceLayer.insertAndCancelCurrentOfflineEvent(
                    offlineEvent = OE(timestamp = dateUtil.now(), duration = T.days(365).msecs(), reason = OE.Reason.DISABLE_LOOP),
                    action = Action.LOOP_DISABLED,
                    source = Sources.LoopDialog,
                    note = null,
                    listValues = listOf()
                ).subscribe()
                return true
            }

            R.id.overview_enable                          -> {
                (loop as PluginBase).setPluginEnabled(PluginType.LOOP, true)
                (loop as PluginBase).setFragmentVisible(PluginType.LOOP, true)
                configBuilder.storeSettings("EnablingLoop")
                rxBus.send(EventRefreshOverview("suspend_menu"))
                disposable += persistenceLayer.cancelCurrentOfflineEvent(dateUtil.now(), Action.LOOP_ENABLED, Sources.LoopDialog).subscribe()
                return true
            }

            R.id.overview_resume, R.id.overview_reconnect -> {
                disposable += persistenceLayer.cancelCurrentOfflineEvent(dateUtil.now(), if (v.id == R.id.overview_resume) Action.RESUME else Action.RECONNECT, Sources.LoopDialog).subscribe()
                rxBus.send(EventRefreshOverview("suspend_menu"))
                commandQueue.cancelTempBasal(true, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            uiInteraction.runAlarm(result.comment, rh.gs(app.aaps.core.ui.R.string.temp_basal_delivery_error), app.aaps.core.ui.R.raw.boluserror)
                        }
                    }
                })
                preferences.put(BooleanNonKey.ObjectivesReconnectUsed, true)
                return true
            }

            R.id.overview_suspend_1h                      -> {
                loop.suspendLoop(T.hours(1).mins().toInt(), Action.SUSPEND, Sources.LoopDialog, listValues = listOf(ValueWithUnit.Hour(1)))
                rxBus.send(EventRefreshOverview("suspend_menu"))
                return true
            }

            R.id.overview_suspend_2h                      -> {
                loop.suspendLoop(T.hours(2).mins().toInt(), Action.SUSPEND, Sources.LoopDialog, listValues = listOf(ValueWithUnit.Hour(2)))
                rxBus.send(EventRefreshOverview("suspend_menu"))
                return true
            }

            R.id.overview_suspend_3h                      -> {
                loop.suspendLoop(T.hours(3).mins().toInt(), Action.SUSPEND, Sources.LoopDialog, listValues = listOf(ValueWithUnit.Hour(3)))
                rxBus.send(EventRefreshOverview("suspend_menu"))
                return true
            }

            R.id.overview_suspend_10h                     -> {
                loop.suspendLoop(T.hours(10).mins().toInt(), Action.SUSPEND, Sources.LoopDialog, listValues = listOf(ValueWithUnit.Hour(10)))
                rxBus.send(EventRefreshOverview("suspend_menu"))
                return true
            }

            R.id.overview_disconnect_15m                  -> {
                profileFunction.getProfile()?.let { profile ->
                    loop.goToZeroTemp(T.mins(15).mins().toInt(), profile, OE.Reason.DISCONNECT_PUMP, Action.DISCONNECT, Sources.LoopDialog, listOf(ValueWithUnit.Minute(15)))
                    rxBus.send(EventRefreshOverview("suspend_menu"))
                }
                return true
            }

            R.id.overview_disconnect_30m                  -> {
                profileFunction.getProfile()?.let { profile ->
                    loop.goToZeroTemp(T.mins(30).mins().toInt(), profile, OE.Reason.DISCONNECT_PUMP, Action.DISCONNECT, Sources.LoopDialog, listOf(ValueWithUnit.Minute(30)))
                    rxBus.send(EventRefreshOverview("suspend_menu"))
                }
                return true
            }

            R.id.overview_disconnect_1h                   -> {
                profileFunction.getProfile()?.let { profile ->
                    loop.goToZeroTemp(T.hours(1).mins().toInt(), profile, OE.Reason.DISCONNECT_PUMP, Action.DISCONNECT, Sources.LoopDialog, listOf(ValueWithUnit.Hour(1)))
                    rxBus.send(EventRefreshOverview("suspend_menu"))
                }
                preferences.put(BooleanNonKey.ObjectivesDisconnectUsed, true)
                return true
            }

            R.id.overview_disconnect_3h                   -> {
                profileFunction.getProfile()?.let { profile ->
                    loop.goToZeroTemp(T.hours(3).mins().toInt(), profile, OE.Reason.DISCONNECT_PUMP, Action.DISCONNECT, Sources.LoopDialog, listOf(ValueWithUnit.Hour(3)))
                    rxBus.send(EventRefreshOverview("suspend_menu"))
                }
                return true
            }

            R.id.overview_disconnect_6h                   -> {
                profileFunction.getProfile()?.let { profile ->
                    loop.goToZeroTemp(T.hours(6).mins().toInt(), profile, OE.Reason.DISCONNECT_PUMP, Action.DISCONNECT, Sources.LoopDialog, listOf(ValueWithUnit.Hour(6)))
                    rxBus.send(EventRefreshOverview("suspend_menu"))
                }
                return true
            }
        }
        return false
    }

    override fun show(manager: FragmentManager, tag: String?) {
        try {
            manager.beginTransaction().let {
                it.add(this, tag)
                it.commitAllowingStateLoss()
            }
        } catch (e: IllegalStateException) {
            aapsLogger.debug(e.localizedMessage ?: e.toString())
        }
    }

    override fun onResume() {
        super.onResume()
        if (!queryingProtection) {
            queryingProtection = true
            activity?.let { activity ->
                val cancelFail = {
                    queryingProtection = false
                    aapsLogger.debug(LTag.APS, "Dialog canceled on resume protection: ${this.javaClass.simpleName}")
                    ToastUtils.warnToast(ctx, R.string.dialog_canceled)
                    dismiss()
                }
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, { queryingProtection = false }, cancelFail, cancelFail)
            }
        }
    }
}