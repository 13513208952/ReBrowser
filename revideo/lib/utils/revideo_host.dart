import 'package:PiliPlus/plugin/pl_player/controller.dart';
import 'package:PiliPlus/services/service_locator.dart';
import 'package:PiliPlus/utils/storage.dart';
import 'package:flutter/services.dart';

abstract final class ReVideoHost {
  static const _channel = MethodChannel('io.github.rebrowser.revideo/host');
  static Future<void>? _closing;

  static Future<void> close() =>
      _closing ??= _close().whenComplete(() => _closing = null);

  static Future<void> _close() async {
    try {
      await videoPlayerServiceHandler?.onPause?.call();
      if (PlPlayerController.instance case final player?) {
        await player.pause();
        await player.makeHeartBeat(
          player.position.value,
          type: .completed,
          isManual: true,
        );
      }
    } finally {
      await GStorage.flushOpenBoxes();
      await _channel.invokeMethod<void>('close');
    }
  }
}
