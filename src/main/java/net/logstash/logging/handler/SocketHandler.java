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
package net.logstash.logging.handler;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;

import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.handlers.SslTcpOutputStream;
import org.jboss.logmanager.handlers.TcpOutputStream;
import org.jboss.logmanager.handlers.UdpOutputStream;
import org.jboss.logmanager.handlers.UninterruptibleOutputStream;

/**
 * A handler used to communicate over a socket.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SocketHandler extends ExtHandler {

  /**
   * The type of socket
   */
  public enum Protocol {
    /**
     * Transmission Control Protocol
     */
    TCP,
    /**
     * User Datagram Protocol
     */
    UDP,
    /**
     * Transport Layer Security over TCP
     */
    SSL_TCP,
  }

  public static final int DEFAULT_PORT = 4560;

  // All the following fields are guarded by this
  private InetAddress address;
  private int port;
  private Protocol protocol;
  private Writer writer;
  private boolean initialize;
  private TransportErrorManager em;

  /**
   * Creates a socket handler with an address of
   * {@linkplain java.net.InetAddress#getLocalHost() localhost} and port of
   * {@linkplain #DEFAULT_PORT 4560}.
   *
   * @throws UnknownHostException
   *           if an error occurs attempting to retrieve the localhost
   */
  public SocketHandler() throws UnknownHostException {
    this(InetAddress.getLocalHost(), DEFAULT_PORT);
  }

  /**
   * Creates a socket handler.
   *
   * @param hostname
   *          the hostname to connect to
   * @param port
   *          the port to connect to
   *
   * @throws UnknownHostException
   *           if an error occurs resolving the address
   */
  public SocketHandler(final String hostname, final int port) throws UnknownHostException {
    this(InetAddress.getByName(hostname), port);
  }

  /**
   * Creates a socket handler.
   *
   * @param address
   *          the address to connect to
   * @param port
   *          the port to connect to
   */
  public SocketHandler(final InetAddress address, final int port) {
    this(Protocol.TCP, address, port);
  }

  /**
   * Creates a socket handler.
   *
   * @param protocol
   *          the protocol to connect with
   * @param hostname
   *          the hostname to connect to
   * @param port
   *          the port to connect to
   *
   * @throws UnknownHostException
   *           if an error occurs resolving the hostname
   */
  public SocketHandler(final Protocol protocol, final String hostname, final int port) throws UnknownHostException {
    this(protocol, InetAddress.getByName(hostname), port);
  }

  /**
   * Creates a socket handler.
   *
   * @param protocol
   *          the protocol to connect with
   * @param address
   *          the address to connect to
   * @param port
   *          the port to connect to
   */
  public SocketHandler(final Protocol protocol, final InetAddress address, final int port) {
    this.address = address;
    this.port = port;
    this.protocol = protocol;
    initialize = true;
    writer = null;
    em = new TransportErrorManager();
    setErrorManager(em);
  }

  @Override
  protected void doPublish(final ExtLogRecord record) {
    final String formatted = createFormattedMessage(record);
    if (formatted == null || formatted.isEmpty()) {
      // nothing to write; move along
      return;
    }
    try {
      synchronized (this) {
        if (initialize && (em.lastExceptionTimestamp < System.currentTimeMillis() - 5000)) {
          initialize();
          if (em.lastException == null) {
            initialize = false;
          }
        }
        if (writer == null) {
          for (Handler h : getHandlers()) {
            h.publish(record);
          }
          return;
        }
        writer.write(formatted);
        super.doPublish(record);
      }
    } catch (Exception e) {
      handleExceptionOnPublish(record, e);
    }
  }
  
  private String createFormattedMessage(final ExtLogRecord record) {
    final Formatter formatter = getFormatter();
    try {
      return formatter.format(record);
    } catch (Exception e) {
      reportError("Could not format message", e, ErrorManager.FORMAT_FAILURE);
      return null;
    }
  }

  private void handleExceptionOnPublish(final ExtLogRecord record, Exception e) {
    Handler[] handlers = getHandlers();
    if (handlers.length > 0) {
      // if we have a subhandler it will publish the record of the failed
      // transmission (to disk)
      for (Handler h : getHandlers()) {
        h.publish(record);
      }
    } else {
      reportError("Error writing log message", e, ErrorManager.WRITE_FAILURE);
    }
    closeSocketHandler();
  }

  @Override
  public void flush() {
    synchronized (this) {
      safeFlush(writer);
    }
    super.flush();
  }

  @Override
  public void close() {
    closeSocketHandler();
    super.close();
  }

  private void closeSocketHandler() {
    checkAccess(this);
    synchronized (this) {
      safeClose(writer);
      writer = null;
      initialize = true;
    }
  }

  /**
   * Returns the address being used.
   *
   * @return the address
   */
  public InetAddress getAddress() {
    return address;
  }

  /**
   * Sets the address to connect to.
   *
   * @param address
   *          the address
   */
  public void setAddress(final InetAddress address) {
    checkAccess(this);
    synchronized (this) {
      this.address = address;
      initialize = true;
    }
  }

  /**
   * Sets the address to connect to by doing a lookup on the hostname.
   *
   * @param hostname
   *          the host name used to resolve the address
   *
   * @throws UnknownHostException
   *           if an error occurs resolving the address
   */
  public void setHostname(final String hostname) throws UnknownHostException {
    checkAccess(this);
    setAddress(InetAddress.getByName(hostname));
  }

  /**
   * Returns the protocol being used.
   *
   * @return the protocol
   */
  public Protocol getProtocol() {
    return protocol;
  }

  /**
   * Sets the protocol to use.
   *
   * @param protocol
   *          the protocol to use
   */
  public void setProtocol(final Protocol protocol) {
    checkAccess(this);
    synchronized (this) {
      this.protocol = protocol;
      initialize = true;
    }
  }

  /**
   * Returns the port being used.
   *
   * @return the port
   */
  public int getPort() {
    return port;
  }

  /**
   * Sets the port to connect to.
   *
   * @param port
   *          the port
   */
  public void setPort(final int port) {
    checkAccess(this);
    synchronized (this) {
      this.port = port;
      initialize = true;
    }
  }

  public void setSubHandler(final Handler handler) {
    checkAccess(this);
    synchronized (this) {
      if (handler != null) {
        addHandler(handler);
      } else {
        Handler[] handlers = getHandlers();
        for (Handler h : handlers) {
          removeHandler(h);
        }
      }
    }
  }

  public Handler getSubHandler() {
    Handler[] handlers = getHandlers();
    return (handlers.length == 0) ? null : handlers[0];
  }

  /** internal ErrorManager with timestamps */
  private static class TransportErrorManager extends ErrorManager {
    private Exception lastException;
    private long lastExceptionTimestamp = 0;
    private long exceptionTimestamp = 0;

    @Override
    public void error(String msg, Exception ex, int code) {
      lastException = ex;
      lastExceptionTimestamp = exceptionTimestamp;
      exceptionTimestamp = System.currentTimeMillis();
    }
  }

  private void initialize() {
    em.lastException = null;
    final Writer current = this.writer;
    boolean okay = false;
    try {
      if (current != null) {
        writeTail(current);
        safeFlush(current);
      }
      final OutputStream out = createOutputStream();
      if (out == null) {
        return;
      }
      final String encoding = getEncoding();
      final UninterruptibleOutputStream outputStream = new UninterruptibleOutputStream(out);
      writer = new OutputStreamWriter(outputStream, (encoding != null) ? encoding : "UTF-8");
      writeHead(writer);
      okay = true;
    } catch (UnsupportedEncodingException e) {
      reportError("Error opening", e, ErrorManager.OPEN_FAILURE);
    } finally {
      safeClose(current);
      if (!okay) {
        safeClose(writer);
      }
    }

  }

  private OutputStream createOutputStream() {
    if (address != null || port >= 0) {
      try {
        switch (protocol) {
        case SSL_TCP:
          SslTcpOutputStream sos = new SslTcpOutputStream(address, port);
          if (sos.isConnected()) {
            return sos;
          }
        case UDP:
          return new UdpOutputStream(address, port);
        case TCP:
        default:
          TcpOutputStream tos = new TcpOutputStream(address, port);
          if (tos.isConnected()) {
            return tos;
          }
        }
      } catch (IOException e) {
        reportError("Failed to create socket output stream", e, ErrorManager.OPEN_FAILURE);
      }
    }
    return null;
  }

  private void writeHead(final Writer writer) {
    try {
      final Formatter formatter = getFormatter();
      if (formatter != null)
        writer.write(formatter.getHead(this));
    } catch (Exception e) {
      reportError("Error writing section header", e, ErrorManager.WRITE_FAILURE);
    }
  }

  private void writeTail(final Writer writer) {
    try {
      final Formatter formatter = getFormatter();
      if (formatter != null)
        writer.write(formatter.getTail(this));
    } catch (Exception ex) {
      reportError("Error writing section tail", ex, ErrorManager.WRITE_FAILURE);
    }
  }

  private void safeClose(Closeable c) {
    try {
      if (c != null)
        c.close();
    } catch (Exception e) {
      reportError("Error closing resource", e, ErrorManager.CLOSE_FAILURE);
    } catch (Throwable t) {
      // ignored
    }
  }

  private void safeFlush(Flushable f) {
    try {
      if (f != null)
        f.flush();
    } catch (Exception e) {
      reportError("Error on flush", e, ErrorManager.FLUSH_FAILURE);
    } catch (Throwable t) {
      // ignored
    }
  }
}
