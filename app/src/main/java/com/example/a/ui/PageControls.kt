package com.example.a.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*

@Composable internal fun BackButton(onBack:()->Unit,modifier:Modifier=Modifier,color:Color=InkColor,background:Color=Color(0xFF454545),tag:String="page_back") {
    Box(modifier.size(48.dp).background(background,CircleShape).semantics{contentDescription="Vissza"}.testTag(tag).clickable(role=Role.Button,onClick=onBack),contentAlignment=Alignment.Center){
        Canvas(Modifier.size(22.dp)){val p=Offset(size.width*.32f,size.height*.5f);drawLine(color,Offset(size.width*.68f,size.height*.18f),p,3.dp.toPx(),StrokeCap.Round);drawLine(color,p,Offset(size.width*.68f,size.height*.82f),3.dp.toPx(),StrokeCap.Round)}
    }
}
@Composable internal fun PageHeading(title:String,onBack:()->Unit,modifier:Modifier=Modifier,color:Color=InkColor) {
    Row(modifier.fillMaxWidth().padding(bottom=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
        BackButton(onBack,tag="menu_page_back");Text(title,color=color,fontSize=25.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
    }
}
