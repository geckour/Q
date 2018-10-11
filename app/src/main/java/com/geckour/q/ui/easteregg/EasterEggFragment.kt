package com.geckour.q.ui.easteregg

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.bumptech.glide.Glide
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.FragmentEasterEggBinding
import com.geckour.q.ui.main.MainViewModel
import com.geckour.q.util.InsertActionType
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.getSong
import com.geckour.q.util.observe
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.launch
import java.util.*
import kotlin.coroutines.experimental.CoroutineContext

class EasterEggFragment : Fragment() {

    companion object {
        fun newInstance(): EasterEggFragment = EasterEggFragment()
    }

    private lateinit var binding: FragmentEasterEggBinding
    private val viewModel: EasterEggViewModel by lazy {
        ViewModelProviders.of(this)[EasterEggViewModel::class.java]
    }
    private lateinit var mainViewModel: MainViewModel
    private var parentJob = Job()
    private val bgScope = object : CoroutineScope {
        override val coroutineContext: CoroutineContext get() = parentJob
    }
    private val uiScope = object : CoroutineScope {
        override val coroutineContext: CoroutineContext get() = Dispatchers.Main + parentJob
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentEasterEggBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bgScope.launch {
            val db = DB.getInstance(requireContext())
            val trackList = db.trackDao().getAll()
            if (trackList.isEmpty()) return@launch

            val max = trackList.size
            val seed = Calendar.getInstance(TimeZone.getDefault())
                    .let { it.get(Calendar.DAY_OF_YEAR) * 1000 + it.get(Calendar.YEAR) }
            val random = Random(seed.toLong())
            while (true) {
                val index = random.nextInt(max)
                val song = trackList[index].let { getSong(db, it).await() }
                if (song != null) {
                    viewModel.song = song

                    setSong()
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

    override fun onStart() {
        super.onStart()
        parentJob = Job()
    }

    override fun onStop() {
        super.onStop()
        parentJob.cancel()
    }

    private fun setSong() {
        binding.viewModel = viewModel
        uiScope.launch {
            Glide.with(binding.artwork)
                    .load(viewModel.song?.thumbUriString ?: R.drawable.ic_empty)
                    .into(binding.artwork)
        }
    }

    private fun observeEvents() {
        viewModel.tap.observe(this) {
            viewModel.song?.apply {
                mainViewModel.onNewQueue(listOf(this),
                        InsertActionType.NEXT, OrientedClassType.SONG)
            }
        }
    }
}