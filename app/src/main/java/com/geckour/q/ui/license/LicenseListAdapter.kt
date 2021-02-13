package com.geckour.q.ui.license

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.geckour.q.databinding.ItemLicenseBinding
import com.geckour.q.domain.model.LicenseItem

class LicenseListAdapter(private val items: List<LicenseItem>) :
        RecyclerView.Adapter<LicenseListAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
            ItemLicenseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.onBind(items[position])
    }

    class ViewHolder(private val binding: ItemLicenseBinding) :
            RecyclerView.ViewHolder(binding.root) {

        fun onBind(item: LicenseItem) {
            binding.item = item
            binding.name.setOnClickListener {
                item.stateOpen = item.stateOpen.not()
                binding.item = item
            }
        }
    }
}