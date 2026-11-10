package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.RoomType
import com.example.data.repository.CinemaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read app name string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Planet Checker", appName)
  }

  @Test
  fun `verify room type classification`() {
    assertEquals(RoomType.IMAX, RoomType.fromRoom("אולם 5"))
    assertEquals(RoomType.SCREEN_X, RoomType.fromRoom("אולם 19"))
    assertEquals(RoomType.FOUR_DX, RoomType.fromRoom("אולם 20"))
    assertEquals(RoomType.VIP, RoomType.fromRoom("אולם 21 (VIP)"))
    assertEquals(RoomType.VIP, RoomType.fromRoom("אולם 24"))
    assertEquals(RoomType.TWO_D, RoomType.fromRoom("אולם 1"))
  }

  @Test
  fun `verify cinema day normalization`() {
    val repo = CinemaRepository()
    // 14:00 is 840 min
    assertEquals(840, repo.normalizeCinemaMinutes(840))
    // 01:00 (60 min) should be shifted to next day cinema (1500)
    assertEquals(1500, repo.normalizeCinemaMinutes(60))
  }

  @Test
  fun `verify fallback mock screenings are loaded`() {
    val repo = CinemaRepository()
    val list = repo.prepareScreenings(CinemaRepository.MOCK_SCREENINGS, 1000)
    assertEquals(15, list.size)
    assertNotNull(list.firstOrNull { it.roomType == RoomType.IMAX })
  }
}
