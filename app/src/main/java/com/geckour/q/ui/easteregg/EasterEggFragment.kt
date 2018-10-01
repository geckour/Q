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
import com.geckour.q.domain.model.Song
import com.geckour.q.ui.MainViewModel
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

    private var song: Song? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentEasterEggBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.viewModel = viewModel

        bgScope.launch {
            if (song == null) {
                val db = DB.getInstance(requireContext())
                val max = db.trackDao().count()
                val seed = Calendar.getInstance(TimeZone.getDefault())
                        .let { it.get(Calendar.DAY_OF_YEAR) * 1000 + it.get(Calendar.YEAR) }
                val trackId = Random(seed.toLong()).nextInt(max)
                song = db.trackDao().get(trackId.toLong())?.let { getSong(db, it).await() }
                setSong()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setSong()
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
        binding.song = song
        uiScope.launch {
            Glide.with(binding.artwork)
                    .load(song?.thumbUriString ?: R.drawable.ic_empty)
                    .into(binding.artwork)
        }
    }

    private fun observeEvents() {
        viewModel.tap.observe(this) {
            song?.apply {
                mainViewModel.onNewQueue(listOf(this),
                        InsertActionType.NEXT, OrientedClassType.SONG)
            }
        }
    }
}