/*
 * Copyright 2013 karl spies.
 *
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObjectBuilder;

import org.jboss.logmanager.ExtLogRecord;

/**
 * Log formatter for the JSON format used by logstash
 */
public class LogstashUtilFormatter extends Formatter {
  private static final JsonBuilderFactory BUILDER = Json.createBuilderFactory(null);
  protected static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZZ";
  private static final String SYSTEM_PROPERTY_TAGS = "net.logstash.logging.formatter.LogstashUtilFormatter.tags";
  private static final String[] TAGS = System.getProperty(SYSTEM_PROPERTY_TAGS, "").split(",");
  private static String hostName;

  static {
    try {
      hostName = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      hostName = "localhost";
    }
  }

  @Override
  public final String format(final LogRecord record) {
    SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
    String dateString = dateFormat.format(new Date(record.getMillis()));
    JsonArrayBuilder tagsBuilder = BUILDER.createArrayBuilder();
    boolean hasTags = false;
    for (final String tag : TAGS) {
      if (!tag.isEmpty()) {
        hasTags = true;
        tagsBuilder.add(tag);
      }
    }
    String message = formatMessage(record);
    JsonObjectBuilder builder = BUILDER.createObjectBuilder().add("@timestamp", dateString).add("@message", message)
        .add("@source", record.getLoggerName()).add("@source_host", hostName).add("@fields", encodeFields(record));
    if (hasTags) {
      builder.add("@tags", tagsBuilder.build());
    }
    return builder.build().toString() + "\n";
  }

  @Override
  public synchronized String formatMessage(LogRecord record) {
    String format = record.getMessage();
    if (format == null) {
      return null;
    }
    ResourceBundle resourceBundle = record.getResourceBundle();
    if (resourceBundle != null) {
      try {
        format = resourceBundle.getString(format);
      } catch (MissingResourceException e) {
        // ignore
      }
    }
    Object[] parameters = record.getParameters();
    String msg = format;
    if (record instanceof ExtLogRecord) {
      ExtLogRecord extLogRecord = (ExtLogRecord) record;
      switch (extLogRecord.getFormatStyle()) {
      case MESSAGE_FORMAT:
        msg = format.indexOf('{') >= 0 ? MessageFormat.format(format, parameters) : format;
        break;
      case PRINTF:
        msg = (parameters == null) ? String.format(format) : String.format(format, parameters);
        break;
      case NO_FORMAT:
        break;
      default:
        break;
      }
    } else {
      msg = format.indexOf('{') >= 0 ? MessageFormat.format(format, parameters) : format;
    }
    record.setParameters(null);
    record.setMessage(msg);
    return super.formatMessage(record);
  }

  /**
   * Encode all additional fields.
   *
   * @param record
   *          the log record
   * @return objectBuilder
   */
  protected final JsonObjectBuilder encodeFields(final LogRecord record) {
    JsonObjectBuilder builder = BUILDER.createObjectBuilder();
    builder.add("timestamp", record.getMillis());
    builder.add("level", record.getLevel().toString());
    builder.add("line_number", getLineNumber(record));
    addSourceClassName(record, builder);
    addSourceMethodName(record, builder);
    addThrowableInfo(record, builder);
    return builder;
  }

  /**
   * Format the stackstrace.
   *
   * @param record
   *          the logrecord which contains the stacktrace
   * @param builder
   *          the json object builder to append
   */
  protected final void addThrowableInfo(final LogRecord record, final JsonObjectBuilder builder) {
    Throwable t = record.getThrown();
    if (t != null) {
      if (record.getSourceClassName() != null) {
        builder.add("exception_class", t.getClass().getName());
      }
      if (t.getMessage() != null) {
        builder.add("exception_message", t.getMessage());
      }
      addStacktraceElements(record, builder);
    }
  }

  /**
   * Get the line number of the exception.
   *
   * @param record
   *          the logrecord
   * @return the line number
   */
  protected final int getLineNumber(final LogRecord record) {
    final int lineNumber;
    Throwable t = record.getThrown();
    if (t != null) {
      lineNumber = getLineNumberFromStackTrace(t.getStackTrace());
    } else {
      lineNumber = 0;
    }
    return lineNumber;
  }

  /**
   * Gets line number from stack trace.
   * 
   * @param traces
   *          all stack trace elements
   * @return line number of the first stacktrace.
   */
  protected final int getLineNumberFromStackTrace(final StackTraceElement[] traces) {
    final int lineNumber;
    if (traces.length > 0 && traces[0] != null) {
      lineNumber = traces[0].getLineNumber();
    } else {
      lineNumber = 0;
    }
    return lineNumber;
  }

  protected final void addValue(final JsonObjectBuilder builder, final String key, final String value) {
    if (value != null) {
      builder.add(key, value);
    } else {
      builder.add(key, "null");
    }
  }

  private void addSourceMethodName(final LogRecord record, final JsonObjectBuilder builder) {
    addValue(builder, "method", record.getSourceMethodName());
  }

  private void addSourceClassName(final LogRecord record, final JsonObjectBuilder builder) {
    addValue(builder, "class", record.getSourceClassName());
  }

  private void addStacktraceElements(final LogRecord record, final JsonObjectBuilder builder) {
    Throwable t = record.getThrown();
    // print whole stacktrace including message, class and cause
    if (t != null && t.getStackTrace().length > 0) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      pw.println();
      t.printStackTrace(pw);
      pw.close();
      builder.add("stacktrace", sw.toString());
    }
  }
}
