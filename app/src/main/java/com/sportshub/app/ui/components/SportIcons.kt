package com.sportshub.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SportsBaseball
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.SportsFootball
import androidx.compose.material.icons.outlined.SportsHockey
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.ui.graphics.vector.ImageVector
import com.sportshub.app.model.Sport

/** The ball for each sport, for empty states and the sport menu. */
val Sport.icon: ImageVector
    get() = when (this) {
        Sport.NFL -> Icons.Outlined.SportsFootball
        Sport.NBA -> Icons.Outlined.SportsBasketball
        Sport.MLB -> Icons.Outlined.SportsBaseball
        Sport.NHL -> Icons.Outlined.SportsHockey
        Sport.SOCCER -> Icons.Outlined.SportsSoccer
    }
