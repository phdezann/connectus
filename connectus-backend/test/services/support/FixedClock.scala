package services.support

import java.time.{Clock, ZoneId, Instant}

class FixedClock(val instant: Instant, val zone: ZoneId) extends Clock {
  override def millis = instant.toEpochMilli
  override def getZone = zone
  override def withZone(zoneId: ZoneId) = {
    instant.atZone(zoneId)
    this
  }
}
