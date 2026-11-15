package dev.phenomena;

import static org.junit.jupiter.api.Assertions.*;

import dev.phenomena.config.*;
import org.junit.jupiter.api.Test;

class FoundationTest {



  @Test
  void invalidValuesBounded() {
    var c = new Config();
    c.analytics.x = Double.NaN;
    c.retention = -1;
    c.volume = 4;
    c.validate();
    assertEquals(0, c.analytics.x);
    assertEquals(1, c.retention);
    assertEquals(1, c.volume);
  }
}
