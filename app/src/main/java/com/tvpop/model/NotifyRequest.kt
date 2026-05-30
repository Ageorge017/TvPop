package com.tvpop.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotifyRequest(
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("media_url") val mediaUrl: String? = null,
    val title: String = "",
    val message: String = "",
    val duration: Int = 15,
    val position: String = "bottom_right",
    @SerialName("width") val widthDp: Int = 320,
    @SerialName("corner_radius") val cornerRadiusDp: Float = 12.0f,
    @SerialName("background_color") val backgroundColor: String = "#CC000000",
    @SerialName("title_color") val titleColor: String = "#FFFFFF",
    @SerialName("message_color") val messageColor: String = "#CCCCCC"
)