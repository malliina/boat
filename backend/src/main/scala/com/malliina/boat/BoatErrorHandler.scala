package com.malliina.boat

//object BoatErrorHandler extends BoatErrorHandler
//
//class BoatErrorHandler extends HttpErrorHandler {
//  private val log = Logger(getClass)
//
//  override def onClientError(
//    request: RequestHeader,
//    statusCode: Int,
//    message: String
//  ): Future[Result] = {
//    log.warn(s"Client error with status $statusCode for $request: '$message'.")
//    fut(Status(statusCode)(Errors(message)))
//  }
//
//  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
//    log.error(s"Server error for $request.", exception)
//    fut(Status(500)(Errors("A server error occurred.")))
//  }
//
//  def fut[T](f: T) = Future.successful(f)
//}
