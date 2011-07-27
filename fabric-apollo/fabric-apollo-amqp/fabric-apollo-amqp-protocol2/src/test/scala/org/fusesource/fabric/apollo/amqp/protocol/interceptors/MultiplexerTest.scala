/*
 * Copyright (C) 2010-2011, FuseSource Corp.  All rights reserved
 *
 *    http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license, a copy of which has been included with this distribution
 * in the license.txt file
 */

package org.fusesource.fabric.apollo.amqp.protocol.interceptors

import org.apache.activemq.apollo.util.FunSuiteSupport
import org.apache.activemq.apollo.util.Logging
import org.fusesource.fabric.apollo.amqp.codec.interfaces.AMQPFrame
import org.fusesource.fabric.apollo.amqp.protocol.interfaces.Interceptor
import org.scalatest.matchers.ShouldMatchers
import scala.collection.mutable.Queue
import test_interceptors.{TestSendInterceptor, FailInterceptor, TaskExecutingInterceptor, TerminationInterceptor}
import org.fusesource.fabric.apollo.amqp.codec.types.{End, AMQPTransportFrame, Begin}

/**
 *
 */
class MultiplexerTest extends FunSuiteSupport with ShouldMatchers with Logging {

  test("Create multiplexer, create some chains on the fly, remove a chain") {

    val multiplexer = new Multiplexer
    multiplexer.channel_selector = Option((frame:AMQPTransportFrame) => frame.getChannel)
    multiplexer.channel_mapper = Option((frame:AMQPTransportFrame) => None)
    multiplexer.outgoing_channel_setter = Option((channel:Int, frame:AMQPTransportFrame) => frame.setChannel(channel))
    multiplexer.check_release = Option((frame:AMQPTransportFrame) => false)

    var instances = 0
    var saved:Interceptor = null

    multiplexer.chain_attached = Option((chain:Interceptor) => {
      instances = instances + 1
      if (instances == 2) {
        saved = chain
      }
    })

    multiplexer.chain_released = Option((chain:Interceptor) => {
      instances = instances - 1
    })

    multiplexer.interceptor_factory = Option((frame:AMQPTransportFrame) => {
      val rc = new FrameLoggingInterceptor("Chain instance " + instances)
      rc.tail.incoming = new TerminationInterceptor
      rc
    })

    multiplexer.head.outgoing = new FrameLoggingInterceptor("Outgoing")
    multiplexer.head.outgoing = new TaskExecutingInterceptor

    multiplexer.head.receive(new AMQPTransportFrame(0, new Begin(0)), new Queue[() => Unit])
    multiplexer.head.receive(new AMQPTransportFrame(1, new Begin(1)), new Queue[() => Unit])
    multiplexer.head.receive(new AMQPTransportFrame(2, new Begin(2)), new Queue[() => Unit])

    instances should be (3)

    // now remove one
    saved.tail.remove
    saved.tail.incoming = new FailInterceptor

    multiplexer.release(saved.head)
    multiplexer.head.receive(new AMQPTransportFrame(0, new Begin(0)), new Queue[() => Unit])
    multiplexer.head.receive(new AMQPTransportFrame(1, new Begin(1)), new Queue[() => Unit])
    multiplexer.head.receive(new AMQPTransportFrame(2, new Begin(2)), new Queue[() => Unit])

    instances should be (3)
  }

  test("Add and release interceptors") {
    val multiplexer = new Multiplexer

    multiplexer.channel_selector = Option((frame:AMQPTransportFrame) => frame.getChannel)

    var instances = 0

    multiplexer.chain_attached = Option((chain:Interceptor) => {
      info("Attaching chain : %s", chain)
      instances = instances + 1
    })

    multiplexer.chain_released = Option((chain:Interceptor) => {
      info("Releasing chain : %s", chain)
      instances = instances - 1
    })

    multiplexer.outgoing_channel_setter = Option((channel:Int, frame:AMQPTransportFrame) => frame.setChannel(channel))
    multiplexer.check_release = Option((frame:AMQPTransportFrame) => false)

    multiplexer.channel_mapper = Option((frame:AMQPTransportFrame) => {
      frame.getPerformative match {
        case b:Begin =>
          Option(b.getRemoteChannel) match {
            case Some(i) =>
              Option(i.intValue)
            case None =>
              None
          }
        case _ =>
          None
      }
    })

    multiplexer.head.outgoing = new TestSendInterceptor( (frame:AMQPFrame, tasks:Queue[() => Unit]) => {
      frame match {
        case t:AMQPTransportFrame =>
          t.getChannel should be (5)
      }
    })
    multiplexer.outgoing = new TaskExecutingInterceptor

    val chain = new FrameLoggingInterceptor("My chain")
    chain.incoming = new TerminationInterceptor
    multiplexer.attach(chain)

    multiplexer.head.receive(new AMQPTransportFrame(5, new Begin(0)), new Queue[() => Unit])

    instances should be (1)
  }

  test("Release interceptor via frame") {
    val multiplexer = new Multiplexer

    multiplexer.channel_selector = Option((frame:AMQPTransportFrame) => frame.getChannel)

    var instances = 0

    multiplexer.chain_attached = Option((chain:Interceptor) => {
      info("Attaching chain : %s", chain)
      instances = instances + 1
    })

    multiplexer.chain_released = Option((chain:Interceptor) => {
      info("Releasing chain : %s", chain)
      instances = instances - 1
    })

    multiplexer.outgoing_channel_setter = Option((channel:Int, frame:AMQPTransportFrame) => frame.setChannel(channel))
    multiplexer.check_release = Option((frame:AMQPTransportFrame) => {
      frame.getPerformative match {
        case e:End =>
          true
        case _ =>
          false
      }
    })

    multiplexer.channel_mapper = Option((frame:AMQPTransportFrame) => {
      frame.getPerformative match {
        case b:Begin =>
          Option(b.getRemoteChannel) match {
            case Some(i) =>
              Option(i.intValue)
            case None =>
              None
          }
        case _ =>
          None
      }
    })

    multiplexer.head.outgoing = new TestSendInterceptor( (frame:AMQPFrame, tasks:Queue[() => Unit]) => {
      frame match {
        case t:AMQPTransportFrame =>
          t.getChannel should be (5)
      }
    })
    multiplexer.outgoing = new TaskExecutingInterceptor

    val chain = new FrameLoggingInterceptor("My chain")
    chain.incoming = new TerminationInterceptor
    multiplexer.attach(chain)

    multiplexer.head.receive(new AMQPTransportFrame(5, new Begin(0)), new Queue[() => Unit])

    instances should be (1)

    chain.tail.send(new AMQPTransportFrame(5, new End), new Queue[() => Unit])

    instances should be(0)
  }


}
