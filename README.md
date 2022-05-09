[![Actions Status](https://github.com/kifj/wildfly-logstash/workflows/Java%20CI/badge.svg)](https://github.com/kifj/wildfly-logstash/actions) ![Licence](https://img.shields.io/github/license/kifj/wildfly-logstash) ![Issues](https://img.shields.io/github/issues/kifj/wildfly-logstash) ![Stars](https://img.shields.io/github/stars/kifj/wildfly-logstash)

wildfly-logstash
================

Logstash module for Wildfly (http://wildfly.org), using https://github.com/SYNAXON/logstash-util-formatter as formatter. The SocketAppender is based on jboss-logmanager-ext (https://github.com/jamezp/jboss-logmanager-ext). Both deserve credits for the original work. This module has been tested with Wildfly 8 to 18. 

Compile the jar file with maven: `mvn package`

To create a ZIP file containing the module: `mvn package -P zip`

Unzip the archive created in the `target/zip` folder at `$JBOSS_HOME/modules/system/layers/base`.

To create a RPM package containing the module: `mvn package -P rpm

Modify the JBoss configuration in `standalone/configuration/standalone.xml` by adding a formatter to the logging configuration and reference the formatter in the handler.

<pre>
/subsystem=logging/custom-formatter=LOGSTASH-PATTERN:add(\
  class=net.logstash.logging.formatter.LogstashUtilFormatter,\
  module=x1.wildfly-logstash)

/subsystem=logging/periodic-rotating-file-handler=LOGSTASH:add(\
  autoflush=true,\ 
  suffix=".yyyy-MM-dd",\ 
  append=true, \
  file={path=logstash.log, relative-to=jboss.server.log.dir})

/subsystem=logging/periodic-rotating-file-handler=LOGSTASH:write-attribute(\
  name=named-formatter,value=LOGSTASH-PATTERN)

/subsystem=logging/root-logger=ROOT:add-handler(name=LOGSTASH)
</pre>

You can define special tags by setting the system property `net.logstash.logging.formatter.LogstashUtilFormatter.tags` to a comma-separated list of tags.

In the logstash shipper configuration you have to add a input configuration pointing at the outfile with code json.

<pre>
input {
  file {
    type => "wildfly"
    path => "/opt/wildfly/standalone/log/logstash.log"
    codec => "json"
  }
}
</pre>

If logstash is running in a docker container, you need to mount the log file into the container.

If you use filebeat to ship the logfiles it should contain a files section like this

<pre>
- type: log
  enabled: true
  paths:
    - /opt/wildfly/standalone/log/logstash.log
  ignore_older: 24h
  json.keys_under_root: true
  json.add_error_key: true
  fields:
    type: wildfly
</pre>

Instead of writing to file, you can also enable direct sending of log messages to logstash. You need to setup a logstash input plugin for TCP with json codec.

<pre>
input {
  tcp {
    codec => "json"
    port => 9996
  }
}
</pre>

The logging configuration for Wildfly needs to be adapted with a custom handler, setting the hostname and port to the values needed for your logstash server. The file handler with the JSON output is set as subHandler to the new handler. This handler will be used if server is not available (with a retry every 5 seconds). We wrap everything in an async-handler which will queue up the logging events and pushes them asynchronously. If the transport is blocked or slow this will not cause trouble to the application. The async-handler will be used in the logger configuration.

<pre>
/subsystem=logging/custom-handler=LOGSTASH-SOCKET:add(level=DEBUG, class=net.logstash.logging.handler.SocketHandler,module=x1.wildfly-logstash,named-formatter=LOGSTASH-PATTERN,properties={protocol=TCP, hostname=logstash, port=9996, subHandler=LOGSTASH})
/subsystem=logging/custom-handler=LOGSTASH-SOCKET:add-handler(LOGSTASH)
/subsystem=logging/async-handler=LOGSTASH-ASYNC:add(queue-length=512, subhandlers=[LOGSTASH-SOCKET])
/subsystem=logging/root-logger=ROOT:add-handler(name=LOGSTASH-ASYNC)
</pre>
