package com.rediscombinators

import com.redis.RedisClient
import com.redis.serialization.Format

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.Breaks._
import scala.util.{Failure, Success, Try}

object RedisLockOps {

  type Lock = String

  implicit class RedisLock(rc: RedisClient) {

    def withLock[B](l: Lock)(body: => B)(implicit format: Format): B = {
      while (!rc.setnx(toKey(l), l)) Thread.sleep(250)
      @volatile var done = false
      setExpiration(l, done)(rc)
      val res = Try { body } match {
        case Success(s) => s
        case Failure(e) =>
          done = true
          throw e
      }
      done = true
      rc.del(toKey(l))
      res
    }
  }

  private def setExpiration(l: Lock, done: Boolean)(rc: RedisClient): Future[Unit] = Future {
    rc.expire(toKey(l), 2)
    breakable {
      while (!done) {
        Thread.sleep(1000)
        if (rc.exists(l)) rc.expire(toKey(l), 2) else break()
      }
    }
  }

  private def toKey(l: Lock): String = s"lock:$l"

}
