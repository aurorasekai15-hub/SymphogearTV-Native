package com.symphogear.tv.extension

import android.view.View
import android.view.animation.AnimationUtils
import com.symphogear.tv.App
import com.symphogear.tv.R

fun View?.startAnimation(hasFocus: Boolean) {
    val animation = AnimationUtils.loadAnimation(
        App.context, if (hasFocus) R.anim.zoom_120 else R.anim.zoom_100)
    this?.startAnimation(animation)
    animation.fillAfter = true
}
