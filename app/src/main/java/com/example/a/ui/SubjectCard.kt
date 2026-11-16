package com.example.a.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.a.R

// Display regions of the supplied illustration sheet. Keep the original artwork
// intact; each subject remains an independent, accessible navigation control.
private data class SubjectArtwork(val origin: IntOffset, val size: IntSize)
private val subjectArtwork = listOf(
    SubjectArtwork(IntOffset(18, 18), IntSize(311, 310)),
    SubjectArtwork(IntOffset(342, 18), IntSize(311, 311)),
    SubjectArtwork(IntOffset(18, 335), IntSize(311, 297)),
    SubjectArtwork(IntOffset(342, 335), IntSize(311, 297)),
    SubjectArtwork(IntOffset(17, 644), IntSize(312, 324)),
    SubjectArtwork(IntOffset(342, 644), IntSize(311, 324)),
)

@Composable
internal fun SubjectCard(subject: Subject, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val artwork = ImageBitmap.imageResource(R.drawable.subject_artwork)
    val region = subjectArtwork[subject.ordinal]
    val painter = remember(artwork, region) {
        BitmapPainter(artwork, region.origin, region.size, FilterQuality.High)
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = 0.88f, stiffness = 750f),
        label = "subject_press",
    )
    BoxWithConstraints(modifier.aspectRatio(0.98f).graphicsLayer{scaleX=scale;scaleY=scale}.clip(RoundedCornerShape(22.dp))
        .clickable(interactionSource=interaction,indication=null,role=Role.Button,onClick=onClick).testTag("learn_subject_${subject.name.lowercase()}")) {
        Image(painter,null,Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)
        val top=when(subject){Subject.Math->.29f;Subject.Literature->.25f;Subject.English->.34f;Subject.Biology->.30f;Subject.Science->.29f;Subject.ForeignLanguage->.20f}
        val labelHeight=if(subject==Subject.ForeignLanguage).34f else .24f
        val font=if(subject==Subject.Science)16.sp else if(subject==Subject.Math)18.sp else 21.sp
        Box(Modifier.padding(horizontal=10.dp).offset(y=maxHeight*top).fillMaxWidth().height(maxHeight*labelHeight)
            .background(if(subject==Subject.Literature)Color(0xFFFFF4DE) else subject.color,RoundedCornerShape(9.dp)),contentAlignment=Alignment.Center){
            Text(if(subject==Subject.Science)"Természet-\ntudomány" else androidx.compose.ui.res.stringResource(subject.label),color=if(subject==Subject.Literature)Color(0xFF6229A7) else Color.White,fontWeight=FontWeight.Black,fontSize=font,lineHeight=22.sp,textAlign=TextAlign.Center,maxLines=2)
        }
    }
}
