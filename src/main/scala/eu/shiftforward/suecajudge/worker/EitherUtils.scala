package eu.shiftforward.suecajudge.worker

import scala.concurrent.{ ExecutionContext, Future }

object EitherUtils {
  implicit class EitherOps[Err, A](val either: Either[Err, A]) extends AnyVal {
    def getOrThrow: A = either.fold({ err => throw new RuntimeException(err.toString) }, identity)
  }

  implicit class EitherFutureOps[Err, A](futEither: Either[Err, Future[A]]) {

    def sequence(implicit ec: ExecutionContext): Future[Either[Err, A]] = futEither match {
      case Left(err) => Future.successful(Left(err))
      case Right(aFut) => aFut.map(Right.apply)
    }

    def flatSequence[B](implicit ev: A <:< Either[Err, B], ec: ExecutionContext): Future[Either[Err, B]] = futEither match {
      case Left(err) => Future.successful(Left(err))
      case Right(aFut) => aFut.map { a => a }
    }
  }
}
