package org.equeim.spacer.donki.data.model.units

import kotlinx.serialization.Serializable

data class Coordinates(
    val latitude: Latitude,
    val longitude: Longitude
)

@JvmInline
@Serializable
value class Latitude(val value: Float)

@JvmInline
@Serializable
value class Longitude(val value: Float)
