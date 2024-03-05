import org.cassandraunit.utils.EmbeddedCassandraServerHelper

object EmbeddedDatabase {

  def startTimeout: Long = EmbeddedCassandraServerHelper.DEFAULT_STARTUP_TIMEOUT * 3

  def start(): Unit = Runtime.getRuntime.synchronized {
    EmbeddedCassandraServerHelper.startEmbeddedCassandra(startTimeout)
  }

}
