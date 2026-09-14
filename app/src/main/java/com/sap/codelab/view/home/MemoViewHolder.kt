package com.sap.codelab.view.home

import androidx.recyclerview.widget.RecyclerView
import com.sap.codelab.databinding.RecyclerviewMemoBinding
import com.sap.codelab.model.Memo

/**
 * View holder for Memos.
 */
internal class MemoViewHolder(private val binding: RecyclerviewMemoBinding) : RecyclerView.ViewHolder(binding.root) {

    /**
     * Updates the memo view with the given memo.
     */
    fun update(
        memo: Memo,
        onMemoClicked: (Memo) -> Unit,
        onDoneChanged: (Memo, Boolean) -> Unit
    ) {
        binding.run {
            memoTitle.text = memo.title
            memoText.text = memo.description
        }
        updateCheckbox(memo, onDoneChanged)
        itemView.setOnClickListener { onMemoClicked(memo) }
    }

    /**
     * Updates the checkbox view.
     */
    private fun updateCheckbox(memo: Memo, onDoneChanged: (Memo, Boolean) -> Unit) {
        // if the view is reused it will already have a listener already set on it. So in order this not to be called when the value is initialized
        // we remove the listener and set it back.
        binding.checkBox.apply {
            setOnCheckedChangeListener(null)
            isChecked = memo.isDone
            // We only let the user edit the checkbox if the item has not been marked as "done"
            isEnabled = !memo.isDone
            setOnCheckedChangeListener { _, isChecked -> onDoneChanged(memo, isChecked) }
        }
    }
}
