package org.kaloz.camel.spark

import akka.actor._
import akka.camel._
import org.apache.activemq.camel.component.ActiveMQComponent
import scala.reflect.ClassTag
import org.apache.spark.streaming.receivers.Receiver
import org.apache.spark.SparkConf
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.StreamingContext.toPairDStreamFunctions

class ActiveMqSparkFeederActor extends Actor with Consumer {

  import ActiveMqSparkFeederActor._

  var receivers = List.empty[ActorRef]

  def endpointUri: String = "mq:spark.in"

  def receive = {
    case SubscribeReceiver(receiverActor: ActorRef) =>
      println("received subscribe from %s".format(receiverActor.toString))
      receivers = receiverActor :: receivers
    case UnsubscribeReceiver(receiverActor: ActorRef) =>
      println("received unsubscribe from %s".format(receiverActor.toString))
      receivers = receivers.dropWhile(x => x eq receiverActor)
    case msg: CamelMessage =>
      println(s"Sending $msg to $receivers")
      receivers.foreach(_ ! msg)
  }
}

object ActiveMqSparkFeederActor extends App {

  val actorSystem = AkkaUtils.createActorSystem("SparkFeeder", "127.0.0.1", 8888, conf = new SparkConf)._1
  val system = CamelExtension(actorSystem)

  val amqUrl = "nio://localhost:61616"
  system.context.addComponent("mq", ActiveMQComponent.activeMQComponent(amqUrl))

  val feeder = actorSystem.actorOf(Props[ActiveMqSparkFeederActor], "FeederActor")

  println("Feeder started as:" + feeder)

  actorSystem.awaitTermination()

  case class SubscribeReceiver(receiverActor: ActorRef)

  case class UnsubscribeReceiver(receiverActor: ActorRef)

}

object ActorWordCount extends App {
  // Create the context and set the batch size
  val ssc = new StreamingContext("local", "ActorWordCount", Seconds(2), System.getenv("SPARK_HOME"), StreamingContext.jarOfClass(this.getClass))
  ssc.checkpoint(".")

  val lines = ssc.actorStream[String](SampleActorReceiver.props("akka.tcp://SparkFeeder@127.0.0.1:8888/user/FeederActor"), "SampleReceiver")

  val updateFunc = (values: Seq[Int], state: Option[Int]) => {
    val currentCount = values.foldLeft(0)(_ + _)

    val previousCount = state.getOrElse(0)

    Some(currentCount + previousCount)
  }

  //compute wordcount
  //  lines.flatMap(_.split("\\s+")).map(word => (word, 1)).reduceByKey(_ + _).print()
  lines.flatMap(_.split("\\s+")).map(word => (word, 1)).updateStateByKey[Int](updateFunc).print()

  ssc.start()
  ssc.awaitTermination()
}

class SampleActorReceiver[T: ClassTag](urlOfPublisher: String) extends Actor with Receiver {

  import ActiveMqSparkFeederActor._

  lazy private val remotePublisher = context.actorSelection(urlOfPublisher)

  override def preStart = remotePublisher ! SubscribeReceiver(context.self)

  def receive = {
    case CamelMessage(body, _) => pushBlock(body.asInstanceOf[T])
  }

  override def postStop() = remotePublisher ! UnsubscribeReceiver(context.self)
}

object SampleActorReceiver {

  def props(urlOfPublisher: String) = Props(classOf[SampleActorReceiver[String]], urlOfPublisher, implicitly[ClassTag[String]])
}


