package com.mic.scriptpilot.ui.aitools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.mic.scriptpilot.databinding.FragmentAiToolsBinding
import com.mic.scriptpilot.ui.common.playCardPressAnimation
import com.mic.scriptpilot.ui.common.rootAppBarConfiguration
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AiToolsFragment : Fragment() {
    private var _binding: FragmentAiToolsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAiToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setupWithNavController(findNavController(), rootAppBarConfiguration())

        val nav = findNavController()
        binding.toolCardTrends.setOnClickListener { v ->
            v.playCardPressAnimation()
            nav.navigate(AiToolsFragmentDirections.actionAiToolsToTrendInput())
        }
        binding.toolCardIdeas.setOnClickListener { v ->
            v.playCardPressAnimation()
            nav.navigate(AiToolsFragmentDirections.actionAiToolsToIdea())
        }
        binding.toolCardScript.setOnClickListener { v ->
            v.playCardPressAnimation()
            nav.navigate(AiToolsFragmentDirections.actionAiToolsToScript())
        }
        binding.toolCardShorts.setOnClickListener { v ->
            v.playCardPressAnimation()
            nav.navigate(AiToolsFragmentDirections.actionAiToolsToShorts())
        }
        binding.toolCardSeo.setOnClickListener { v ->
            v.playCardPressAnimation()
            nav.navigate(AiToolsFragmentDirections.actionAiToolsToSeo())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
