/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.logstash.logging.formatter;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.ExtLogRecord.FormatStyle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LogstashUtilFormatterTest {

  public static final int LINE_NUMBER = 42;
  private LogRecord record = null;
  private LogstashUtilFormatter instance = new LogstashUtilFormatter();
  private String fullLogMessage = null;
  private JsonObjectBuilder fieldsBuilder;
  private JsonObjectBuilder builder;
  private JsonObjectBuilder exceptionBuilder;
  private Exception ex;
  private static String hostName;
  private static final String LINE_BREAK = System.lineSeparator();

  static {
    System.setProperty("net.logstash.logging.formatter.LogstashUtilFormatter.tags", "foo,bar");
    try {
      hostName = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      hostName = "unknown-host";
    }
  }

  @BeforeEach
  public void setUp() {
    long millis = System.currentTimeMillis();
    record = new LogRecord(Level.ALL, "Junit Test");
    record.setLoggerName(LogstashUtilFormatter.class.getName());
    record.setSourceClassName(LogstashUtilFormatter.class.getName());
    record.setSourceMethodName("testMethod");
    record.setMillis(millis);

    ex = new Exception("That is an exception");
    StackTraceElement[] stackTrace = new StackTraceElement[1];
    stackTrace[0] = new StackTraceElement("Test", "methodTest", "Test.class", LINE_NUMBER);
    ex.setStackTrace(stackTrace);
    record.setThrown(ex);

    builder = Json.createBuilderFactory(null).createObjectBuilder();
    final SimpleDateFormat dateFormat = new SimpleDateFormat(LogstashUtilFormatter.DATE_FORMAT);
    String dateString = dateFormat.format(new Date(millis));
    builder.add("@timestamp", dateString);
    builder.add("@message", "Junit Test");
    builder.add("@source", LogstashUtilFormatter.class.getName());
    builder.add("@source_host", hostName);

    fieldsBuilder = createFieldsBuilder(millis, stackTrace);

    exceptionBuilder = Json.createBuilderFactory(null).createObjectBuilder();
    exceptionBuilder.add("exception_class", ex.getClass().getName());
    exceptionBuilder.add("exception_message", ex.getMessage());
    exceptionBuilder.add("stacktrace", LINE_BREAK + ex.getClass().getName() + ": " + ex.getMessage() + LINE_BREAK
        + "\tat " + stackTrace[0].toString() + LINE_BREAK);

    builder.add("@fields", createFieldsBuilder(millis, stackTrace));

    JsonArrayBuilder tagsBuilder = Json.createArrayBuilder();
    tagsBuilder.add("foo");
    tagsBuilder.add("bar");
    builder.add("@tags", tagsBuilder.build());

    fullLogMessage = builder.build().toString() + LINE_BREAK;
  }
  
  private JsonObjectBuilder createFieldsBuilder(long millis, StackTraceElement[] stackTrace) {
    JsonObjectBuilder builder = Json.createBuilderFactory(null).createObjectBuilder();
    builder.add("timestamp", millis);
    builder.add("level", Level.ALL.toString());
    builder.add("line_number", LINE_NUMBER);
    builder.add("class", LogstashUtilFormatter.class.getName());
    builder.add("method", "testMethod");
    builder.add("exception_class", ex.getClass().getName());
    builder.add("exception_message", ex.getMessage());
    builder.add("stacktrace", LINE_BREAK + ex.getClass().getName() + ": " + ex.getMessage() + LINE_BREAK + "\tat "
        + stackTrace[0].toString() + LINE_BREAK);
    return builder;
  }

  /**
   * Test of format method, of class LogstashFormatter.
   */
  @Test
  public void testFormat() {
    String result = instance.format(record);
    assertEquals(fullLogMessage, result);
  }

  /**
   * Test of encodeFields method, of class LogstashFormatter.
   */
  @Test
  public void testEncodeFields() {
    JsonObjectBuilder result = instance.encodeFields(record);
    assertEquals(fieldsBuilder.build().toString(), result.build().toString());
  }

  /**
   * Test of addThrowableInfo method, of class LogstashFormatter.
   */
  @Test
  public void testAddThrowableInfo() {
    JsonObjectBuilder result = Json.createBuilderFactory(null).createObjectBuilder();
    instance.addThrowableInfo(record, result);
    assertEquals(exceptionBuilder.build().toString(), result.build().toString());
  }

  /**
   * Test of addThrowableInfo method, of class LogstashFormatter.
   */
  @Test
  public void testAddThrowableInfoNoThrowableAttached() {
    JsonObjectBuilder result = Json.createBuilderFactory(null).createObjectBuilder();
    instance.addThrowableInfo(new LogRecord(Level.OFF, hostName), result);
    assertEquals("{}", result.build().toString());
  }

  /**
   * Test of addThrowableInfo method, of class LogstashFormatter.
   */
  @Test
  public void testAddThrowableInfoThrowableAttachedButWithoutStackTrace() {
    JsonObjectBuilder result = Json.createBuilderFactory(null).createObjectBuilder();
    record.getThrown().setStackTrace(new StackTraceElement[0]);
    instance.addThrowableInfo(record, result);
    assertEquals("{\"exception_class\":\"java.lang.Exception\",\"exception_message\":\"That is an exception\"}",
        result.build().toString());
  }

  /**
   * Test of addThrowableInfo method, of class LogstashFormatter.
   */
  @Test
  public void testAddThrowableInfoThrowableAttachedButWithoutSourceClassName() {
    JsonObjectBuilder result = Json.createBuilderFactory(null).createObjectBuilder();
    record.getThrown().setStackTrace(new StackTraceElement[0]);
    record.setSourceClassName(null);
    instance.addThrowableInfo(record, result);
    assertEquals("{\"exception_message\":\"That is an exception\"}", result.build().toString());
  }

  /**
   * Test of addThrowableInfo method, of class LogstashFormatter.
   */
  @Test
  public void testAddThrowableInfoThrowableAttachedButWithoutMessage() {
    JsonObjectBuilder result = Json.createBuilderFactory(null).createObjectBuilder();
    record.setThrown(new Exception());
    record.getThrown().setStackTrace(new StackTraceElement[0]);
    instance.addThrowableInfo(record, result);
    assertEquals("{\"exception_class\":\"java.lang.Exception\"}", result.build().toString());
  }

  /**
   * Test of getLineNumber method, of class LogstashFormatter.
   */
  @Test
  public void testGetLineNumber() {
    int result = instance.getLineNumber(record);
    assertEquals(LINE_NUMBER, result);
  }

  /**
   * Test of getLineNumber method, of class LogstashFormatter.
   */
  @Test
  public void testGetLineNumberNoThrown() {
    assertEquals(0, instance.getLineNumber(new LogRecord(Level.OFF, "foo")));
  }

  /**
   * Test of getLineNumberFromStackTrace method, of class LogstashUtilFormatter.
   */
  @Test
  public void testGetLineNumberFromStackTrace() {
    assertEquals(0, instance.getLineNumberFromStackTrace(new StackTraceElement[0]));
    assertEquals(0, instance.getLineNumberFromStackTrace(new StackTraceElement[] { null }));
  }

  /**
   * Test of addValue method, of class LogstashUtilFormatter.
   */
  @Test
  public void testAddValue() {
    JsonObjectBuilder builder = Json.createBuilderFactory(null).createObjectBuilder();
    instance.addValue(builder, "key", "value");
    assertEquals("{\"key\":\"value\"}", builder.build().toString());
  }

  /**
   * Test of addValue method, of class LogstashUtilFormatter.
   */
  @Test
  public void testAddNullValue() {
    JsonObjectBuilder builder = Json.createBuilderFactory(null).createObjectBuilder();
    instance.addValue(builder, "key", null);
    assertEquals("{\"key\":\"null\"}", builder.build().toString());
  }

  /**
   * Test of message with parameters, of class LogstashUtilFormatter.
   */
  @Test
  public void testAddParameter() {
    Locale.setDefault(Locale.US);
    fullLogMessage = fullLogMessage.replace("Junit Test", "Junit Test [1] [2] [3.000000]");
    ExtLogRecord extLogRecord = new ExtLogRecord(record.getLevel(), "Junit Test [%d] [%s] [%f]", FormatStyle.PRINTF,
        record.getLoggerName());
    extLogRecord.setParameters(new Object[] { 1, "2", 3.0f });
    extLogRecord.setSourceClassName(record.getSourceClassName());
    extLogRecord.setSourceMethodName(record.getSourceMethodName());
    extLogRecord.setLoggerName(record.getLoggerName());
    extLogRecord.setMillis(record.getMillis());
    extLogRecord.setThrown(record.getThrown());

    String result = instance.format(extLogRecord);
    assertEquals(fullLogMessage, result);
  }
}
