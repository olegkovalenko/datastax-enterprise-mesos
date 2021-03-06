package net.elodina.mesos.dse

import joptsimple.{OptionException, OptionSet, OptionParser}
import scala.concurrent.duration.Duration
import java.io.File
import Cli.{out, printLine, handleGenericOptions, Error}

object SchedulerCli {
  def isEnabled: Boolean = System.getenv("DM_NO_SCHEDULER") == null

  def handle(args: Array[String], help: Boolean = false): Unit = {
    val parser = new OptionParser()

    parser.accepts("master", "Mesos Master addresses.").withRequiredArg().required().ofType(classOf[String])
    parser.accepts("user", "Mesos user. Defaults to current system user.").withRequiredArg().ofType(classOf[String])
    parser.accepts("principal", "Principal (username) used to register framework.").withRequiredArg().ofType(classOf[String])
    parser.accepts("secret", "Secret (password) used to register framework.").withRequiredArg().ofType(classOf[String])

    parser.accepts("framework-name", "Framework name. Defaults to dse.").withRequiredArg().ofType(classOf[String])
    parser.accepts("framework-role", "Framework role. Defaults to *.").withRequiredArg().ofType(classOf[String])
    parser.accepts("framework-timeout", "Framework failover timeout. Defaults to 30 days.").withRequiredArg().ofType(classOf[String])

    parser.accepts("storage", "Storage for nodes state. Examples: file:dse-mesos.json; zk:master:2181/dse-mesos; cassandra:9042:master.").withRequiredArg().required().ofType(classOf[String])
    parser.accepts("cassandra-keyspace", "Cassandra keyspace used to find framework state table. Required if storage=cassandra.").withRequiredArg().ofType(classOf[String])
    parser.accepts("cassandra-table", "Cassandra table where framework state to be persisted. Required if storage=cassandra").withRequiredArg().ofType(classOf[String])
    parser.accepts("debug", "Run in debug mode.").withRequiredArg().ofType(classOf[Boolean]).defaultsTo(false)
    parser.accepts("jre", "Path to JRE archive.").withRequiredArg().ofType(classOf[String])

    parser.accepts("namespace", "A grouping label for mesos frameworks. Optional.").withRequiredArg().ofType(classOf[String])

    if (help) {
      printLine("Start scheduler \nUsage: scheduler [options]\n")
      parser.printHelpOn(out)

      printLine()
      handleGenericOptions(args, help = true)
      return
    }

    var options: OptionSet = null
    try { options = parser.parse(args: _*) }
    catch {
      case e: OptionException =>
        parser.printHelpOn(out)
        printLine()
        throw new Cli.Error(e.getMessage)
    }

    val api = options.valueOf("api").asInstanceOf[String]
    Cli.resolveApi(api)

    if (options.has("namespace")) Config.namespace = options.valueOf("namespace").asInstanceOf[String]

    Config.master = options.valueOf("master").asInstanceOf[String]
    Config.user = options.valueOf("user").asInstanceOf[String]
    Config.principal = options.valueOf("principal").asInstanceOf[String]
    Config.secret = options.valueOf("secret").asInstanceOf[String]

    if (options.has("framework-name")) Config.frameworkName = options.valueOf("framework-name").asInstanceOf[String]
    if (options.has("framework-role")) Config.frameworkRole = options.valueOf("framework-role").asInstanceOf[String]
    if (options.has("framework-timeout")) Config.frameworkTimeout = Duration(options.valueOf("framework-timeout").asInstanceOf[String])

    Config.storage = options.valueOf("storage").asInstanceOf[String]

    if (Config.storage.startsWith("cassandra")){
      if (!options.has("cassandra-keyspace"))
        throw new Error("cassandra-keyspace is required if storage=cassandra")
      else Config.cassandraKeyspace = options.valueOf("cassandra-keyspace").asInstanceOf[String]

      if (!options.has("cassandra-table"))
        throw new Error("cassandra-table is required if storage=cassandra")
      else Config.cassandraTable = options.valueOf("cassandra-table").asInstanceOf[String]
    }
    Config.debug = options.valueOf("debug").asInstanceOf[Boolean]

    val jre = options.valueOf("jre").asInstanceOf[String]
    if (jre != null) {
      Config.jre = new File(jre)
      if (!Config.jre.exists() || !Config.jre.isFile) throw new IllegalStateException("JRE file doesn't exist")
    }

    Scheduler.start()
  }
}
