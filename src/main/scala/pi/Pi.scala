package pi

import akka.actor._
import akka.routing.RoundRobinRouter
import akka.util.Duration
import akka.util.duration._

object Pi extends App {

  println(Runtime.getRuntime().availableProcessors())
  calculate(nrOfWorkers = 4, nrOfElements = 10000, nrOfMessages = 100000)

  // actors and messages ...

  def calculate(nrOfWorkers: Int, nrOfElements: Int, nrOfMessages: Int) {
    // Create an Akka system
    val system = ActorSystem("PiSystem")

    // create the result listener, which will print the result and 
    // shutdown the system
    val listener = system.actorOf(Props[Listener], name = "listener")

    // create the master
    val master = system.actorOf(Props(new Master(
      nrOfWorkers, nrOfMessages, nrOfElements, listener)),
      name = "master")

    // start the calculation
    master ! Calculate

  }
}

sealed trait PiMessage
case object Calculate extends PiMessage
case class Work(start: Int, nrOfElements: Int) extends PiMessage
case class Result(value: Double) extends PiMessage
case class PiApproximation(pi: Double, duration: Duration) extends PiMessage


class Worker extends Actor {

  // calculatePiFor ...

  def receive = {
    case Work(start, nrOfElements) =>
      sender ! Result(calculatePiFor(start, nrOfElements)) // perform the work
  }

  def calculatePiFor(start: Int, nrOfElements: Int): Double = {
    var acc = 0.0
    for (i <- start until (start + nrOfElements))
      acc += 4.0 * (1 - (i % 2) * 2) / (2 * i + 1)
    acc
  }
}

class Master(nrOfWorkers: Int,
  nrOfMessages: Int,
  nrOfElements: Int,
  listener: ActorRef) extends Actor {

  var pi: Double = _
  var nrOfResults: Int = _
  val start: Long = System.currentTimeMillis

  val p = context.props
  
  p.routerConfig match {
    case x: RoundRobinRouter => println("hula hula: " + x.nrOfInstances)
    case _ => println("neco jineho")
  }
  
  val workerRouter = context.actorOf(
    Props[Worker].withRouter(RoundRobinRouter(nrOfWorkers)), name = "workerRouter")

  def receive = {
    case Calculate =>
      for (i <- 0 until nrOfMessages)
        workerRouter ! Work(i * nrOfElements, nrOfElements)
        
    case Result(value) =>
      pi += value
      nrOfResults += 1
      if (nrOfResults == nrOfMessages) {
        // Send the result to the listener
        listener ! PiApproximation(pi, duration = (System.currentTimeMillis - start).millis)
        // Stops this actor and all its supervised children
        context.stop(self)
      }
  }

}

class Listener extends Actor {
  def receive = {
    case PiApproximation(pi, duration) =>
      println("\n\tPi approximation: \t\t%s\n\tCalculation time: \t%s"
        .format(pi, duration))
      context.system.shutdown()
  }
}
  