package com.mic.scriptpilot.ui.profile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.mic.scriptpilot.BuildConfig
import com.mic.scriptpilot.R
import com.mic.scriptpilot.databinding.FragmentSupportLegalBinding
import com.mic.scriptpilot.ui.common.playCardPressAnimation
import com.mic.scriptpilot.ui.common.setupScreenHeader

class SupportLegalFragment : Fragment() {
    private var _binding: FragmentSupportLegalBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSupportLegalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.setupScreenHeader(R.string.profile_menu_support, showBack = true) {
            findNavController().navigateUp()
        }
        binding.textVersion.text = getString(R.string.profile_version_format, BuildConfig.VERSION_NAME)

        binding.rowPrivacy.root.setOnClickListener { openUrl(PRIVACY_POLICY_URL, it) }
        binding.rowTerms.root.setOnClickListener { openUrl(TERMS_URL, it) }
        binding.rowContact.root.setOnClickListener { openEmail(it) }
        binding.rowRate.root.setOnClickListener { openPlayStore(it) }
        binding.rowShare.root.setOnClickListener { shareApp(it) }
    }

    private fun openUrl(url: String, source: View) {
        source.playCardPressAnimation()
        openIntent(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    private fun openEmail(source: View) {
        source.playCardPressAnimation()
        openIntent(Intent(Intent.ACTION_SENDTO, "mailto:$SUPPORT_EMAIL?subject=ScriptPilot%20Support".toUri()))
    }

    private fun openPlayStore(source: View) {
        source.playCardPressAnimation()
        val packageName = requireContext().packageName
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri()))
        }.onFailure {
            openIntent(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri()))
        }
    }

    private fun shareApp(source: View) {
        source.playCardPressAnimation()
        val packageName = requireContext().packageName
        openIntent(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=$packageName"),
        )
    }

    private fun openIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.profile_no_app_available, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val PRIVACY_POLICY_URL = "https://sites.google.com/view/scriptpilot-policy/home"
        const val TERMS_URL = PRIVACY_POLICY_URL
        const val SUPPORT_EMAIL = "info.scriptpilot@gmail.com"
    }
}
