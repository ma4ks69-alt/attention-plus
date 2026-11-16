package com.example.a.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.selection.selectable
import com.example.a.R

internal val HeaderHeight=64.dp
@Composable
internal fun AppHeader(modifier: Modifier=Modifier,onMenuItemSelected:(GlassMenuItem)->Unit={},active:GlassMenuItem?=null) {
    Row(modifier.fillMaxWidth().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal)).height(HeaderHeight),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
        GlassMenuItem.entries.forEach { item ->
            val color=if(active==item) AccentColor else InkColor
            Row(Modifier.selectable(active==item,role=Role.Tab){onMenuItemSelected(item)}.padding(16.dp).testTag("top_${item.name.lowercase()}"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                if(item==GlassMenuItem.Store) StoreIcon(Modifier.size(24.dp),color) else SettingsIcon(Modifier.size(24.dp),color)
                Text(if(item==GlassMenuItem.Store) "Bolt" else "Beállítások",color=color)
            }
        }
    }
}
@Composable
internal fun FlatBottomNavigation(selected:Int,onSelect:(Int)->Unit,modifier:Modifier=Modifier){
    Row(modifier.fillMaxWidth().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom+WindowInsetsSides.Horizontal)).height(72.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
        listOf(R.drawable.ic_home,R.drawable.ic_timer,R.drawable.ic_profile).forEachIndexed { index, icon ->
            val label=listOf("Főmenü","Fókusz","Profil")[index]
            Column(Modifier.weight(1f).fillMaxHeight().selectable(selected==index,role=Role.Tab){onSelect(index)}.testTag("tab_$index"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Icon(painterResource(icon),label,Modifier.size(26.dp),tint=if(selected==index) AccentColor else SecondaryInkColor);Text(label,color=if(selected==index) AccentColor else SecondaryInkColor,fontSize=12.sp)}
        }
    }
}
