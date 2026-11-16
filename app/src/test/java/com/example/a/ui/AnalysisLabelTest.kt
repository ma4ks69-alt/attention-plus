package com.example.a.ui
import org.junit.Assert.assertEquals
import org.junit.Test
class AnalysisLabelTest {
 @Test fun labelsFollowActualMaterial(){
  assertEquals("AI kép elemzés",analysisLabel("users/a/photo",""))
  assertEquals("AI szöveg elemzés",analysisLabel("","Jegyzet"))
  assertEquals("AI kép- és szövegelemzés",analysisLabel("users/a/photo","Jegyzet"))
 }
}
