package com.geckour.q.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.geckour.q.databinding.FragmentDetailSongBinding

class SongDetailFragment : Fragment() {

    companion object {
        fun newInstance(): SongDetailFragment = SongDetailFragment()
    }

    private lateinit var binding: FragmentDetailSongBinding

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentDetailSongBinding.inflate(inflater, container, false)
        return binding.root
    }
}