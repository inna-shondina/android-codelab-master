package com.sap.codelab.view.home

import android.view.LayoutInflater
import android.view.ViewGroup
import com.sap.codelab.databinding.RecyclerviewMemoBinding
import com.sap.codelab.model.Memo
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter

/**
 * Adapter containing a set of memos.
 */
internal class MemoAdapter(
    private val onMemoClicked: (Memo) -> Unit,
    private val onDoneChanged: (Memo, Boolean) -> Unit
) : ListAdapter<Memo, MemoViewHolder>(MemoDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoViewHolder {
        return MemoViewHolder(newItemViewBinding(parent))
    }

    override fun onBindViewHolder(holder: MemoViewHolder, position: Int) {
        holder.update(getItem(position), onMemoClicked, onDoneChanged)
    }

    /**
     * Creates the view binding for a memo item displayed in the list.
     *
     * @param parent    - the parent view group of the item.
     * @return the view binding.
     */
    private fun newItemViewBinding(parent: ViewGroup): RecyclerviewMemoBinding {
        return RecyclerviewMemoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    }

    private object MemoDiffCallback : DiffUtil.ItemCallback<Memo>() {
        override fun areItemsTheSame(oldItem: Memo, newItem: Memo): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Memo, newItem: Memo): Boolean = oldItem == newItem
    }
}
