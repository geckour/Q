package com.geckour.q.presentation.main

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dropbox.core.v2.files.FolderMetadata
import com.geckour.q.R
import com.geckour.q.databinding.DialogMetadataListBinding
import com.geckour.q.databinding.ItemListMetadataBinding

class DropboxChooserDialog(
    context: Context,
    private val onClickItem: (FolderMetadata) -> Unit,
    private val onChoose: (String) -> Unit
) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<FolderMetadata>() {
            override fun areItemsTheSame(
                oldItem: FolderMetadata,
                newItem: FolderMetadata
            ): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: FolderMetadata,
                newItem: FolderMetadata
            ): Boolean =
                oldItem.pathDisplay == newItem.pathDisplay
        }
    }

    private val adapter = MetadataListAdapter()

    private var currentChoice = "/"

    val binding = DialogMetadataListBinding.inflate(
        LayoutInflater.from(context),
        null,
        false
    ).apply { recyclerView.adapter = adapter }

    private val dialog: AlertDialog = AlertDialog.Builder(context)
        .setTitle(R.string.dialog_title_dropbox_choose_folder)
        .setMessage(R.string.dialog_desc_dropbox_choose_folder)
        .setView(binding.root)
        .setPositiveButton(R.string.dialog_ok) { dialog, _ ->
            onChoose(currentChoice)
            dialog.dismiss()
        }
        .create()

    fun show(currentDirTitle: String, items: List<FolderMetadata>) {
        if (dialog.isShowing.not()) dialog.show()

        binding.currentDir.text = currentDirTitle
        adapter.submitList(items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }))
    }

    private inner class MetadataListAdapter :
        ListAdapter<FolderMetadata, MetadataListAdapter.ViewHolder>(DIFF_CALLBACK) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                ItemListMetadataBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        private inner class ViewHolder(private val binding: ItemListMetadataBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(metadata: FolderMetadata) {
                binding.metadata = metadata
                binding.root.setOnClickListener {
                    currentChoice = metadata.pathLower
                    onClickItem(metadata)
                }
            }
        }
    }
}