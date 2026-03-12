package com.symphogear.tv.extension

import com.symphogear.tv.App
import com.symphogear.tv.R
import com.symphogear.tv.model.Category
import com.symphogear.tv.model.Channel

fun Category?.isFavorite(): Boolean {
    return this?.name == App.context.getString(R.string.favorite_channel)
}

fun ArrayList<Category>?.addFavorite(channels: ArrayList<Channel>) {
    val title = App.context.getString(R.string.favorite_channel)
    this?.add(0, Category().apply {
        this.name = title
        this.channels = channels
    })
}