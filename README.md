wildfly-logstash
================

Logstash module for JBoss Wildfly, using https://github.com/SYNAXON/logstash-util-formatter as formatter.

Compile the jar file with maven: `mvn package`

Unzip the archive created in the `target/zip` folder at `$JBOSS_HOME/modules/system/layers/base`.

Modify the JBoss configuration in `standalone/configuration/standalone.xml` by adding a formatter to the logging configuration and reference the formatter in the handler.

<pre>
/subsystem=logging/custom-formatter=LOGSTASH-PATTERN:add(\
  class=net.logstash.logging.formatter.LogstashUtilFormatter,\
  module=x1.wildfly-logstash)

/subsystem=logging/periodic-rotating-file-handler=LOGSTASH:add(\
  autoflush=true,\ 
  level=INFO,\ 
  suffix=".yyyy-MM-dd",\ 
  append=true, \
  file={path=logstash.log, relative-to=jboss.server.log.dir})

/subsystem=logging/periodic-rotating-file-handler=LOGSTASH:write-attribute(\
  name=named-formatter,value=LOGSTASH-PATTERN)

/subsystem=logging/root-logger=ROOT:add-handler(name=LOGSTASH)
</pre>

In the logstash configuration you have to add a input configuration pointing at the outfile with format json_event.

<pre>
input {
  file {
    type => "wildfly-server"
    path => "/opt/wildfly/standalone/log/logstash.log"
    format => "json_event"
  }
}
</pre>
