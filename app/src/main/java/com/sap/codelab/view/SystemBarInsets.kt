package com.sap.codelab.view

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

private val SAFE_DRAWING_INSET_TYPES =
    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

/** Enables the same edge-to-edge behavior on every supported Android version. */
internal fun AppCompatActivity.enableEdgeToEdgeLayout() {
    WindowCompat.enableEdgeToEdge(window)
}

/** Keeps screen content clear of side cutouts and the navigation bar. */
internal fun View.applySideAndBottomInsetsToPadding() {
    val initialPadding = Rect(paddingLeft, paddingTop, paddingRight, paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val insets = windowInsets.getInsets(SAFE_DRAWING_INSET_TYPES)
        view.updatePadding(
            left = initialPadding.left + insets.left,
            top = initialPadding.top,
            right = initialPadding.right + insets.right,
            bottom = initialPadding.bottom + insets.bottom
        )
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}

/** Keeps floating controls clear of side cutouts and the navigation bar. */
internal fun View.applySideAndBottomInsetsToMargins() {
    val initialMargins = (layoutParams as MarginLayoutParams).run {
        Rect(leftMargin, topMargin, rightMargin, bottomMargin)
    }
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val insets = windowInsets.getInsets(SAFE_DRAWING_INSET_TYPES)
        view.updateLayoutParams<MarginLayoutParams> {
            leftMargin = initialMargins.left + insets.left
            topMargin = initialMargins.top
            rightMargin = initialMargins.right + insets.right
            bottomMargin = initialMargins.bottom + insets.bottom
        }
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}
