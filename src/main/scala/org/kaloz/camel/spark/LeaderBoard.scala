package org.kaloz.camel.spark

import akka.actor._
import akka.camel._
import org.apache.activemq.camel.component.ActiveMQComponent
import org.apache.activemq.ScheduledMessage._
import scala.reflect.ClassTag
import org.apache.spark.streaming.receivers.Receiver
import org.apache.spark.streaming.{Seconds, StreamingContext}

object LeaderBoard extends App{

//  val actorSystem = ActorSystem("CamelTesting")
//  val system = CamelExtension(actorSystem)
//
//  val amqUrl = "nio://localhost:61616"
//  system.context.addComponent("activemq", ActiveMQComponent.activeMQComponent(amqUrl))

  // Create the context and set the batch size
  val ssc = new StreamingContext("local", "ActiveMQActorWordCount", Seconds(2),
    System.getenv("SPARK_HOME"), StreamingContext.jarOfClass(this.getClass))

  val lines = ssc.actorStream[String](Props(new SimpleConsumer[String]), "SimpleConsumer")

  //compute wordcount
  lines.flatMap(_.split("\\s+")).map(x => (x, 1)).print()

  ssc.start()
  ssc.awaitTermination()

//  // create consumer and producer
//  val simpleConsumer = actorSystem.actorOf(Props[SimpleConsumer])
//  val simpleProducer = actorSystem.actorOf(Props[SimpleProducer])

//
//  Thread.sleep(100) // wait for setup
//
//  simpleProducer ! Message("first")
//  simpleProducer ! Message("second")
//  simpleProducer ! Message("third")
//
//  val delayedMessage = CamelMessage(Message("delayed fourth"), Map(AMQ_SCHEDULED_DELAY -> 3000))
//  simpleProducer ! delayedMessage
//
//  Thread.sleep(5000) // wait for messages
//  actorSystem.shutdown()
}

class SimpleProducer extends Actor with Producer with Oneway {
  def endpointUri: String = "activemq:foo.bar"
}

//class SimpleConsumer extends Actor with Consumer {
//  def endpointUri: String = "activemq:foo.bar"
//
//  def receive = {
//    case msg: CamelMessage => println(msg)
//  }
//}

class SimpleConsumer[T: ClassTag] extends Actor with Consumer with Receiver {
  def endpointUri: String = "activemq:foo.bar"

  def receive = {
    case msg: CamelMessage => pushBlock(msg.asInstanceOf[T])
  }
}

case class Message(body: String)
case class SubscribeReceiver(receiverActor: ActorRef)
case class UnsubscribeReceiver(receiverActor: ActorRef)

//class SampleActorReceiver[T: ClassTag](publisher: ActorRef) extends Actor with Receiver {
//
//  override def preStart = publisher ! SubscribeReceiver(context.self)
//
//  def receive = {
//    case msg => pushBlock(msg.asInstanceOf[T])
//  }
//
//  override def postStop() = publisher ! UnsubscribeReceiver(context.self)
//}
