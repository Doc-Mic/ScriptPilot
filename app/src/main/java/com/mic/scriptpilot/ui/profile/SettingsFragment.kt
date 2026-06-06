package com.mic.scriptpilot.ui.profile

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Filter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.mic.scriptpilot.AuthActivity
import com.mic.scriptpilot.R
import com.mic.scriptpilot.data.repository.CreatorPreferences
import com.mic.scriptpilot.databinding.FragmentSettingsBinding
import com.mic.scriptpilot.ui.common.AppTheme
import com.mic.scriptpilot.ui.common.playCardPressAnimation
import com.mic.scriptpilot.ui.common.setupScreenHeader
import com.mic.scriptpilot.ui.common.ThemeController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()
    private var themePopup: ListPopupWindow? = null
    private val themeOptions = listOf(AppTheme.DARK, AppTheme.LIGHT)
    private var systemNotificationsEnabled = false
    private var latestPreferences: CreatorPreferences = CreatorPreferences()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (_binding == null) return@registerForActivityResult
            refreshNotificationPermissionState()
            if (granted && areSystemNotificationsEnabled()) {
                viewModel.setPushNotifications(true)
            } else {
                viewModel.setPushNotifications(false)
                viewModel.setWeeklyCreatorTips(false)
                Snackbar.make(
                    binding.root,
                    R.string.settings_notifications_permission_required,
                    Snackbar.LENGTH_LONG,
                ).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.setupScreenHeader(R.string.profile_menu_settings, showBack = true) {
            findNavController().navigateUp()
        }
        setupDropdowns()
        setupActions()
        observeState()
        refreshNotificationPermissionState()
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationPermissionState()
    }

    private fun setupDropdowns() {
        setupThemeDropdown()
        binding.inputDefaultTone.configureDropdown(resources.getStringArray(R.array.script_tones).toList()) {
            viewModel.setDefaultTone(it)
        }
    }

    private fun setupThemeDropdown() {
        binding.inputTheme.apply {
            setAdapter(null)
            inputType = InputType.TYPE_NULL
            keyListener = null
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = true
            isCursorVisible = false
            setOnFocusChangeListener(null)
            setOnItemClickListener(null)
            setOnClickListener { showThemeMenu() }
        }
        binding.layoutTheme.apply {
            isClickable = true
            setOnClickListener { showThemeMenu() }
            setEndIconOnClickListener { showThemeMenu() }
        }
    }

    private fun showThemeMenu() {
        if (_binding == null) return
        val labels = themeOptions.map { it.label }
        val popup =
            themePopup ?: ListPopupWindow(requireContext()).also { created ->
                created.anchorView = binding.layoutTheme
                created.isModal = true
                created.setOnItemClickListener(
                    AdapterView.OnItemClickListener { _, _, position, _ ->
                        val theme = themeOptions[position]
                        created.dismiss()
                        binding.inputTheme.setText(theme.label, false)
                        viewModel.setThemeMode(theme.name)
                        ThemeController.apply(theme.name)
                    },
                )
                themePopup = created
            }

        popup.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
        popup.anchorView = binding.layoutTheme
        popup.width = binding.layoutTheme.width.takeIf { it > 0 } ?: ListPopupWindow.WRAP_CONTENT
        popup.height = ListPopupWindow.WRAP_CONTENT
        popup.show()
        popup.listView?.choiceMode = ListView.CHOICE_MODE_SINGLE
    }

    private fun MaterialAutoCompleteTextView.configureDropdown(
        values: List<String>,
        onSelected: (String) -> Unit,
    ) {
        setAdapter(NoFilterArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, values))
        threshold = 0
        inputType = InputType.TYPE_NULL
        keyListener = null
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        isCursorVisible = false
        setOnClickListener { showDropDown() }
        setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showDropDown()
            }
        }
        setOnItemClickListener { parent, _, position, _ ->
            dismissDropDown()
            onSelected(parent.getItemAtPosition(position).toString())
        }
    }

    private fun setupActions() {
        binding.switchAutoSave.setOnCheckedChangeListener { _, checked ->
            viewModel.setAutoSaveProjects(checked)
        }
        binding.buttonOpenNotificationSettings.setOnClickListener {
            openNotificationSettings()
        }
        binding.rowResetPreferences.setOnClickListener { view ->
            view.playCardPressAnimation()
            confirmResetPreferences()
        }
        binding.buttonLogout.setOnClickListener {
            confirmLogout()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        val preferences = state.preferences
                        latestPreferences = preferences
                        binding.inputTheme.setText(ThemeController.labelFor(preferences.themeMode), false)
                        binding.inputDefaultTone.setText(preferences.defaultScriptTone, false)
                        bindSwitch(binding.switchAutoSave, preferences.autoSaveProjects) {
                            viewModel.setAutoSaveProjects(it)
                        }
                        bindNotificationControls(preferences)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is ProfileEvent.Message ->
                                Snackbar.make(binding.root, event.messageRes, Snackbar.LENGTH_SHORT).show()
                            ProfileEvent.SignedOut -> openAuth()
                        }
                    }
                }
            }
        }
    }

    private fun bindSwitch(switch: MaterialSwitch, checked: Boolean, onChanged: (Boolean) -> Unit) {
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = checked
        switch.setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
    }

    private fun bindNotificationControls(preferences: CreatorPreferences) {
        val pushEnabled = preferences.pushNotifications && systemNotificationsEnabled
        binding.textNotificationsDisabled.isVisible = !systemNotificationsEnabled
        binding.buttonOpenNotificationSettings.isVisible = !systemNotificationsEnabled

        bindSwitch(binding.switchPushNotifications, pushEnabled) { checked ->
            onPushNotificationsChanged(checked)
        }

        binding.switchWeeklyTips.isEnabled = pushEnabled
        bindSwitch(binding.switchWeeklyTips, pushEnabled && preferences.weeklyCreatorTips) { checked ->
            if (pushEnabled) {
                viewModel.setWeeklyCreatorTips(checked)
            } else {
                viewModel.setWeeklyCreatorTips(false)
            }
        }

        if (!pushEnabled && preferences.weeklyCreatorTips) {
            viewModel.setWeeklyCreatorTips(false)
        }
    }

    private fun onPushNotificationsChanged(enabled: Boolean) {
        if (!enabled) {
            viewModel.setPushNotifications(false)
            viewModel.setWeeklyCreatorTips(false)
            return
        }

        if (areSystemNotificationsEnabled()) {
            systemNotificationsEnabled = true
            viewModel.setPushNotifications(true)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotificationsPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setPushNotifications(false)
            viewModel.setWeeklyCreatorTips(false)
            refreshNotificationPermissionState()
        }
    }

    private fun refreshNotificationPermissionState() {
        if (_binding == null) return
        systemNotificationsEnabled = areSystemNotificationsEnabled()
        bindNotificationControls(latestPreferences)
    }

    private fun areSystemNotificationsEnabled(): Boolean {
        if (_binding == null) return false
        val context = requireContext()
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        return notificationsEnabled && hasPostNotificationsPermission()
    }

    private fun hasPostNotificationsPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun openNotificationSettings() {
        val context = requireContext()
        val intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
            }
        startActivity(intent)
    }

    private fun confirmResetPreferences() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_dialog_reset_title)
            .setMessage(R.string.profile_dialog_reset_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.profile_dialog_confirm) { _, _ ->
                viewModel.resetPreferences()
            }
            .show()
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_dialog_logout_title)
            .setMessage(R.string.profile_dialog_logout_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.profile_logout) { _, _ ->
                viewModel.signOut()
            }
            .show()
    }

    private fun openAuth() {
        startActivity(
            Intent(requireContext(), AuthActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        requireActivity().finish()
    }

    override fun onDestroyView() {
        themePopup?.dismiss()
        themePopup = null
        super.onDestroyView()
        _binding = null
    }
}

private class NoFilterArrayAdapter(
    context: Context,
    resource: Int,
    private val values: List<String>,
) : ArrayAdapter<String>(context, resource, values) {
    private val noFilter =
        object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults =
                FilterResults().apply {
                    this.values = this@NoFilterArrayAdapter.values
                    count = this@NoFilterArrayAdapter.values.size
                }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                clear()
                addAll(values)
                notifyDataSetChanged()
            }

            override fun convertResultToString(resultValue: Any?): CharSequence =
                resultValue?.toString().orEmpty()
        }

    override fun getFilter(): Filter = noFilter
}
