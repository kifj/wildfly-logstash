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

import java.net.InetAddress;
import java.net.UnknownHostException;
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

/**
 * Log formatter for the JSON format used by logstash 
 */
public class LogstashUtilFormatter extends Formatter {
  private static final JsonBuilderFactory BUILDER = Json.createBuilderFactory(null);
  private static String hostName;
  private static final String[] TAGS = System.getProperty("net.logstash.logging.formatter.LogstashUtilFormatter.tags",
      "").split(",");
  protected static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZZ";

  static {
    try {
      hostName = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      hostName = "localhost";
    }
  }

  @Override
  public final String format(final LogRecord record) {
    final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
    final String dateString = dateFormat.format(new Date(record.getMillis()));
    final JsonArrayBuilder tagsBuilder = BUILDER.createArrayBuilder();
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
    final ResourceBundle resourceBundle = record.getResourceBundle();
    if (resourceBundle != null) {
      try {
        format = resourceBundle.getString(format);
      } catch (MissingResourceException e) {
        // ignore
      }
    }
    Object[] parameters = record.getParameters();
    final String msg = parameters == null ? String.format(format) : String.format(format, parameters);
    record.setParameters(null);
    record.setMessage(msg);
    return super.formatMessage(record);
  }

  /**
   * Enocde all additional fields.
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
    if (record.getThrown() != null) {
      if (record.getSourceClassName() != null) {
        builder.add("exception_class", record.getThrown().getClass().getName());
      }
      if (record.getThrown().getMessage() != null) {
        builder.add("exception_message", record.getThrown().getMessage());
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
    if (record.getThrown() != null) {
      lineNumber = getLineNumberFromStackTrace(record.getThrown().getStackTrace());
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
    final StackTraceElement[] traces = record.getThrown().getStackTrace();
    if (traces.length > 0) {
      StringBuilder strace = new StringBuilder();
      for (StackTraceElement trace : traces) {
        strace.append("\t").append(trace.toString()).append("\n");
      }
      builder.add("stacktrace", strace.toString());
    }
  }
}
