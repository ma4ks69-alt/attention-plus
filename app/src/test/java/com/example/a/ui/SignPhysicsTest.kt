package com.example.a.ui
import org.junit.Test
import org.junit.Assert.*
class SignPhysicsTest {
 @Test fun accelerationDrivesSwingAndDampingSettlesIt(){
  val sign=SignPhysics();repeat(30){sign.advance(0f,1f/60)};assertEquals(0f,sign.angle,0f)
  var peak=0f;repeat(36){n->val t=n/35f;val x=838f*t*t*(3-2*t);sign.advance(x,1f/60);peak=maxOf(peak,kotlin.math.abs(sign.angle))}
  assertTrue(peak>.03f);repeat(600){sign.advance(838f,1f/60)};assertEquals(0f,sign.angle,.002f)
 }
}
