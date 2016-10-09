package net.logstash.logging.handler;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.util.logging.Level;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.handlers.ConsoleHandler;
import org.junit.Test;

import net.logstash.logging.formatter.LogstashUtilFormatter;
import net.logstash.logging.handler.SocketHandler.Protocol;

public class SockerHandlerTest {

  @Test
  public void testSocketHandler() throws Exception {
    try (SocketHandler handler = new SocketHandler(Protocol.TCP, "127.0.0.1", 5555)) {
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      handler.setLevel(Level.FINE);
      handler.setAutoFlush(true);
      handler.setFormatter(new LogstashUtilFormatter());
      
      assertEquals(Protocol.TCP, handler.getProtocol());
      assertEquals(5555, handler.getPort());
      assertEquals("127.0.0.1", handler.getAddress().getHostAddress());
      handler.setPort(5556);
      assertEquals(5556, handler.getPort());

      ConsoleHandler target = new ConsoleHandler();
      target.setAutoFlush(true);
      target.setLevel(Level.ALL);
      target.setOutputStream(os);
      target.setFormatter(new LogstashUtilFormatter());
      handler.setSubHandler(target);
      assertEquals(target, handler.getSubHandler());

      ExtLogRecord record = new ExtLogRecord(Level.FINE, "test", this.getClass().getName());
      record.setLoggerName("testSocketHandler");
      handler.doPublish(record);
      String log = new String(os.toByteArray());
      assertTrue(log.length() > 0);
      assertTrue(log.contains("\"@message\":\"test\""));
      assertTrue(log.contains("\"@source\":\"testSocketHandler\""));

      os.reset();
      record = new ExtLogRecord(Level.FINE, null, this.getClass().getName());
      record.setLoggerName("testSocketHandler");
      handler.doPublish(record);
      log = new String(os.toByteArray());
      assertTrue(log.length() == 0);

      os.reset();
      record = new ExtLogRecord(Level.FINE, "", this.getClass().getName());
      record.setLoggerName("testSocketHandler");
      handler.doPublish(record);
      log = new String(os.toByteArray());
      assertTrue(log.length() > 0);
      assertTrue(log.contains("\"@message\":\"\""));
      assertTrue(log.contains("\"@source\":\"testSocketHandler\""));
    }

  }

}
