package com.tvpop.model

data class OverlayState(
    val mediaUrl: String?,
    val mediaType: String,
    val title: String,
    val message: String,
    val durationSeconds: Int,
    val position: String,
    val widthDp: Int,
    val cornerRadiusDp: Float,
    val backgroundColor: String,
    val titleColor: String,
    val messageColor: String
)