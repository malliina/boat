package pccs.chronos.services.v1.target_soc_service

trait TargetSocServiceFs2Grpc[F[_], A]:
  def getTargetSoc(
    request: pccs.chronos.messages.targetsoc.v1.target_soc.GetTargetSocRequest,
    ctx: A
  ): _root_.fs2.Stream[F, pccs.chronos.messages.targetsoc.v1.target_soc.GetTargetSocResponse]
  def setTargetSoc(
    request: pccs.chronos.messages.targetsoc.v1.target_soc.SetTargetSocRequest,
    ctx: A
  ): _root_.fs2.Stream[F, pccs.chronos.messages.targetsoc.v1.target_soc.SetTargetSocResponse]

object TargetSocServiceFs2Grpc extends _root_.fs2.grpc.GeneratedCompanion[TargetSocServiceFs2Grpc]:

  def serviceDescriptor: _root_.io.grpc.ServiceDescriptor =
    pccs.chronos.services.v1.target_soc_service.TargetSocServiceGrpc.SERVICE

  def mkClientFull[F[_], G[_]: _root_.cats.effect.Async, A](
    dispatcher: _root_.cats.effect.std.Dispatcher[G],
    channel: _root_.io.grpc.Channel,
    clientAspect: _root_.fs2.grpc.client.ClientAspect[F, G, A],
    clientOptions: _root_.fs2.grpc.client.ClientOptions
  ): TargetSocServiceFs2Grpc[F, A] = new TargetSocServiceFs2Grpc[F, A]:
    def getTargetSoc(
      request: pccs.chronos.messages.targetsoc.v1.target_soc.GetTargetSocRequest,
      ctx: A
    ): _root_.fs2.Stream[F, pccs.chronos.messages.targetsoc.v1.target_soc.GetTargetSocResponse] =
      clientAspect.visitUnaryToStreamingCall[
        pccs.chronos.messages.targetsoc.v1.target_soc.GetTargetSocRequest,
        pccs.chronos.messages.targetsoc.v1.target_soc.GetTargetSocResponse
      ](
        _root_.fs2.grpc.client.ClientCallContext(
          ctx,
          pccs.chronos.services.v1.target_soc_service.TargetSocServiceGrpc.METHOD_GET_TARGET_SOC
        ),
        request,
        (req, m) =>
          _root_.fs2.Stream
            .eval(
              _root_.fs2.grpc.client.Fs2ClientCall[G](
                channel,
                pccs.chronos.services.v1.target_soc_service.TargetSocServiceGrpc.METHOD_GET_TARGET_SOC,
                dispatcher,
                clientOptions
              )
            )
            .flatMap(_.unaryToStreamingCall(req, m))
      )
    def setTargetSoc(
      request: pccs.chronos.messages.targetsoc.v1.target_soc.SetTargetSocRequest,
      ctx: A
    ): _root_.fs2.Stream[F, pccs.chronos.messages.targetsoc.v1.target_soc.SetTargetSocResponse] =
      clientAspect.visitUnaryToStreamingCall[
        pccs.chronos.messages.targetsoc.v1.target_soc.SetTargetSocRequest,
        pccs.chronos.messages.targetsoc.v1.target_soc.SetTargetSocResponse
      ](
        _root_.fs2.grpc.client.ClientCallContext(
          ctx,
          pccs.chronos.services.v1.target_soc_service.TargetSocServiceGrpc.METHOD_SET_TARGET_SOC
        ),
        request,
        (req, m) =>
          _root_.fs2.Stream
            .eval(
              _root_.fs2.grpc.client.Fs2ClientCall[G](
                channel,
                pccs.chronos.services.v1.target_soc_service.TargetSocServiceGrpc.METHOD_SET_TARGET_SOC,
                dispatcher,
                clientOptions
              )
            )
            .flatMap(_.unaryToStreamingCall(req, m))
      )

  protected def serviceBindingFull[F[_], G[_]: _root_.cats.effect.Async, A](
    dispatcher: _root_.cats.effect.std.Dispatcher[G],
    serviceImpl: TargetSocServiceFs2Grpc[F, A],
    serviceAspect: _root_.fs2.grpc.server.ServiceAspect[F, G, A],
    serverOptions: _root_.fs2.grpc.server.ServerOptions
  ) =
    _root_.io.grpc.ServerServiceDefinition
      .builder(pccs.chronos.services.v1.target_soc_service.TargetSocServiceGrpc.SERVICE)
      .addMethod(
        pccs.chronos.services.v1.target_soc_service.TargetSocServiceGrpc.METHOD_GET_TARGET_SOC,
        _root_.fs2.grpc.server
          .Fs2ServerCallHandler[G](dispatcher, serverOptions)
          .unaryToStreamingCall[
            pccs.chronos.messages.targetsoc.v1.target_soc.GetTargetSocRequest,
            pccs.chronos.messages.targetsoc.v1.target_soc.GetTargetSocResponse
          ]: (r, m) =>
            serviceAspect.visitUnaryToStreamingCall[
              pccs.chronos.messages.targetsoc.v1.target_soc.GetTargetSocRequest,
              pccs.chronos.messages.targetsoc.v1.target_soc.GetTargetSocResponse
            ](
              _root_.fs2.grpc.server.ServiceCallContext(
                m,
                pccs.chronos.services.v1.target_soc_service.TargetSocServiceGrpc.METHOD_GET_TARGET_SOC
              ),
              r,
              (r, m) => serviceImpl.getTargetSoc(r, m)
            )
      )
      .addMethod(
        pccs.chronos.services.v1.target_soc_service.TargetSocServiceGrpc.METHOD_SET_TARGET_SOC,
        _root_.fs2.grpc.server
          .Fs2ServerCallHandler[G](dispatcher, serverOptions)
          .unaryToStreamingCall[
            pccs.chronos.messages.targetsoc.v1.target_soc.SetTargetSocRequest,
            pccs.chronos.messages.targetsoc.v1.target_soc.SetTargetSocResponse
          ]: (r, m) =>
            serviceAspect.visitUnaryToStreamingCall[
              pccs.chronos.messages.targetsoc.v1.target_soc.SetTargetSocRequest,
              pccs.chronos.messages.targetsoc.v1.target_soc.SetTargetSocResponse
            ](
              _root_.fs2.grpc.server.ServiceCallContext(
                m,
                pccs.chronos.services.v1.target_soc_service.TargetSocServiceGrpc.METHOD_SET_TARGET_SOC
              ),
              r,
              (r, m) => serviceImpl.setTargetSoc(r, m)
            )
      )
      .build()
