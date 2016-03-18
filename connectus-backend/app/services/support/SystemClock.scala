package services.support

import java.time.{Clock, Instant, ZoneId}

class SystemClock(val zone: ZoneId) extends Clock {
  override def instant = Instant.ofEpochMilli(millis);
  override def millis = System.currentTimeMillis;
  override def getZone = zone
  override def withZone(zoneId: ZoneId) = {
    if (zone == this.zone) {
      this
    }
    new SystemClock(zone)
  }
}
