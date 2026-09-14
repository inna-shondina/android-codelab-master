package com.sap.codelab.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.sap.codelab.repository.MemoRepository
import com.sap.codelab.view.create.CreateMemoViewModel
import com.sap.codelab.view.detail.ViewMemoViewModel
import com.sap.codelab.view.home.HomeViewModel

internal class MemoViewModelFactory(
    private val memoRepository: MemoRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when {
            modelClass.isAssignableFrom(CreateMemoViewModel::class.java) ->
                CreateMemoViewModel(memoRepository) as T

            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(memoRepository) as T

            modelClass.isAssignableFrom(ViewMemoViewModel::class.java) ->
                ViewMemoViewModel(memoRepository) as T

            else -> error("Unsupported ViewModel: ${modelClass.name}")
        }
    }
}
