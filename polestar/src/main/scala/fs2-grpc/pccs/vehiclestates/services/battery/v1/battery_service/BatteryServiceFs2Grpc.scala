package pccs.vehiclestates.services.battery.v1.battery_service

import _root_.cats.syntax.all._

/** gRPC service: pccs.vehiclestates.services.battery.v1.BatteryService
  * Methods: GetBattery (server streaming), GetLatestBattery (unary)
  */
trait BatteryServiceFs2Grpc[F[_], A] {
  def getBattery(request: pccs.vehiclestates.services.battery.v1.battery_service.GetBatteryRequest, ctx: A): _root_.fs2.Stream[F, pccs.vehiclestates.services.battery.v1.battery_service.GetBatteryResponse]
  def getLatestBattery(request: pccs.vehiclestates.services.battery.v1.battery_service.GetBatteryRequest, ctx: A): F[pccs.vehiclestates.services.battery.v1.battery_service.GetBatteryResponse]
}

object BatteryServiceFs2Grpc extends _root_.fs2.grpc.GeneratedCompanion[BatteryServiceFs2Grpc] {
  
  def serviceDescriptor: _root_.io.grpc.ServiceDescriptor = pccs.vehiclestates.services.battery.v1.battery_service.BatteryServiceGrpc.SERVICE
  
  def mkClientFull[F[_], G[_]: _root_.cats.effect.Async, A](
    dispatcher: _root_.cats.effect.std.Dispatcher[G],
    channel: _root_.io.grpc.Channel,
    clientAspect: _root_.fs2.grpc.client.ClientAspect[F, G, A],
    clientOptions: _root_.fs2.grpc.client.ClientOptions
  ): BatteryServiceFs2Grpc[F, A] = new BatteryServiceFs2Grpc[F, A] {
    def getBattery(request: pccs.vehiclestates.services.battery.v1.battery_service.GetBatteryRequest, ctx: A): _root_.fs2.Stream[F, pccs.vehiclestates.services.battery.v1.battery_service.GetBatteryResponse] =
      clientAspect.visitUnaryToStreamingCall[pccs.vehiclestates.services.battery.v1.battery_service.GetBatteryRequest, pccs.vehiclestates.services.battery.v1.battery_service.GetBatteryResponse](
        _root_.fs2.grpc.client.ClientCallContext(ctx, pccs.vehiclestates.services.battery.v1.battery_service.BatteryServiceGrpc.METHOD_GET_BATTERY),
        request,
        (req, m) => _root_.fs2.Stream.eval(_root_.fs2.grpc.client.Fs2ClientCall[G](channel, pccs.vehiclestates.services.battery.v1.battery_service.BatteryServiceGrpc.METHOD_GET_BATTERY, dispatcher, clientOptions)).flatMap(_.unaryToStreamingCall(req, m))
      )
    def getLatestBattery(request: pccs.vehiclestates.services.battery.v1.battery_service.GetBatteryRequest, ctx: A): F[pccs.vehiclestates.services.battery.v1.battery_service.GetBatteryResponse] =
      clientAspect.visitUnaryToUnaryCall[pccs.vehiclestates.services.battery.v1.battery_service.GetBatteryRequest, pccs.vehiclestates.services.battery.v1.battery_service.GetBatteryResponse](
        _root_.fs2.grpc.client.ClientCallContext(ctx, pccs.vehiclestates.services.battery.v1.battery_service.BatteryServiceGrpc.METHOD_GET_LATEST_BATTERY),
        request,
        (req, m) => _root_.fs2.grpc.client.Fs2ClientCall[G](channel, pccs.vehiclestates.services.battery.v1.battery_service.BatteryServiceGrpc.METHOD_GET_LATEST_BATTERY, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(req, m))
      )
  }
  
  protected def serviceBindingFull[F[_], G[_]: _root_.cats.effect.Async, A](
    dispatcher: _root_.cats.effect.std.Dispatcher[G],
    serviceImpl: BatteryServiceFs2Grpc[F, A],
    serviceAspect: _root_.fs2.grpc.server.ServiceAspect[F, G, A],
    serverOptions: _root_.fs2.grpc.server.ServerOptions
  ) = {
    _root_.io.grpc.ServerServiceDefinition
      .builder(pccs.vehiclestates.services.battery.v1.battery_service.BatteryServiceGrpc.SERVICE)
      .addMethod(
        pccs.vehiclestates.services.battery.v1.battery_service.BatteryServiceGrpc.METHOD_GET_BATTERY,
        _root_.fs2.grpc.server.Fs2ServerCallHandler[G](dispatcher, serverOptions).unaryToStreamingCall[pccs.vehiclestates.services.battery.v1.battery_service.GetBatteryRequest, pccs.vehiclestates.services.battery.v1.battery_service.GetBatteryResponse]{ (r, m) => 
          serviceAspect.visitUnaryToStreamingCall[pccs.vehiclestates.services.battery.v1.battery_service.GetBatteryRequest, pccs.vehiclestates.services.battery.v1.battery_service.GetBatteryResponse](
            _root_.fs2.grpc.server.ServiceCallContext(m, pccs.vehiclestates.services.battery.v1.battery_service.BatteryServiceGrpc.METHOD_GET_BATTERY),
            r,
            (r, m) => serviceImpl.getBattery(r, m)
          )
        }
      )
      .addMethod(
        pccs.vehiclestates.services.battery.v1.battery_service.BatteryServiceGrpc.METHOD_GET_LATEST_BATTERY,
        _root_.fs2.grpc.server.Fs2ServerCallHandler[G](dispatcher, serverOptions).unaryToUnaryCall[pccs.vehiclestates.services.battery.v1.battery_service.GetBatteryRequest, pccs.vehiclestates.services.battery.v1.battery_service.GetBatteryResponse]{ (r, m) => 
          serviceAspect.visitUnaryToUnaryCall[pccs.vehiclestates.services.battery.v1.battery_service.GetBatteryRequest, pccs.vehiclestates.services.battery.v1.battery_service.GetBatteryResponse](
            _root_.fs2.grpc.server.ServiceCallContext(m, pccs.vehiclestates.services.battery.v1.battery_service.BatteryServiceGrpc.METHOD_GET_LATEST_BATTERY),
            r,
            (r, m) => serviceImpl.getLatestBattery(r, m)
          )
        }
      )
      .build()
  }

}