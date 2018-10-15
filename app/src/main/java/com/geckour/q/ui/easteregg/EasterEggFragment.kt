package com.geckour.q.ui.easteregg

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProviders
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.FragmentEasterEggBinding
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.*
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.IO
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import java.util.*

class EasterEggFragment : ScopedFragment() {

    companion object {
        fun newInstance(): EasterEggFragment = EasterEggFragment()
    }

    private lateinit var binding: FragmentEasterEggBinding
    private val viewModel: EasterEggViewModel by lazy {
        ViewModelProviders.of(this)[EasterEggViewModel::class.java]
    }
    private lateinit var mainViewModel: MainViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentEasterEggBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        FirebaseAnalytics.getInstance(requireContext())
                .logEvent(
                        FirebaseAnalytics.Event.SELECT_CONTENT,
                        Bundle().apply {
                            putString(FirebaseAnalytics.Param.ITEM_NAME, "Show easter egg screen")
                        }
                )

        launch(Dispatchers.IO) {
            val db = DB.getInstance(requireContext())
            val trackList = db.trackDao().getAll()
            if (trackList.isEmpty()) return@launch

            val max = trackList.size
            val seed = Calendar.getInstance(TimeZone.getDefault())
                    .let { it.get(Calendar.DAY_OF_YEAR) * 1000 + it.get(Calendar.YEAR) }
            val random = Random(seed.toLong())
            while (true) {
                val index = random.nextInt(max)
                val song = trackList[index].let { getSong(db, it) }
                if (song != null) {
                    viewModel.song = song

                    withContext(Dispatchers.Main) { setSong() }
                    return@launch
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        mainViewModel.resumedFragmentId.value = R.layout.fragment_easter_egg

        if (viewModel.song != null) setSong()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        mainViewModel = ViewModelProviders.of(requireActivity())[MainViewModel::class.java]
        observeEvents()
    }

    private fun setSong() {
        binding.viewModel = viewModel
        Glide.with(binding.artwork)
                .load(viewModel.song?.thumbUriString ?: R.drawable.ic_empty)
                .into(binding.artwork)
    }

    private fun observeEvents() {
        viewModel.tap.observe(this) {
            FirebaseAnalytics.getInstance(requireContext())
                    .logEvent(
                            FirebaseAnalytics.Event.SELECT_CONTENT,
                            Bundle().apply {
                                putString(FirebaseAnalytics.Param.ITEM_NAME, "Tapped today's song")
                            }
                    )

            viewModel.song?.apply {
                mainViewModel.onNewQueue(listOf(this),
                        InsertActionType.NEXT, OrientedClassType.SONG)
            }
        }
    }
}