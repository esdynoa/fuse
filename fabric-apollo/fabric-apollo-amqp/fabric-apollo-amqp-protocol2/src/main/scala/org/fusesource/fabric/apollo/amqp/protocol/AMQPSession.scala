/*
 * Copyright (C) 2010-2011, FuseSource Corp.  All rights reserved
 *
 *    http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license, a copy of which has been included with this distribution
 * in the license.txt file
 */

package org.fusesource.fabric.apollo.amqp.protocol

import interfaces.{ProtocolConnection, ProtocolSession}
import org.fusesource.fabric.apollo.amqp.protocol.api._
import org.fusesource.fabric.apollo.amqp.protocol.interfaces.Interceptor
import org.fusesource.fabric.apollo.amqp.codec.interfaces.AMQPFrame
import collection.mutable.Queue

class AMQPSession extends Interceptor with AbstractSession {

  def begin(onBegin: Runnable) {}

  def end() {}

  def end(t: Throwable) {}

  def end(reason: String) {}

  def send(frame: AMQPFrame, tasks: Queue[() => Unit]) = null

  def receive(frame: AMQPFrame, tasks: Queue[() => Unit]) = null

}
