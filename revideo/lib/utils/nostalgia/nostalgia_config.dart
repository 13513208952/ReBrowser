import 'package:PiliPlus/utils/storage_pref.dart';

enum NostalgiaMode {
  disabled,
  online,
  offline;

  static NostalgiaMode fromStorage(int value) => switch (value) {
    1 => online,
    2 => offline,
    _ => disabled,
  };
}

abstract final class NostalgiaConfig {
  static NostalgiaMode get mode =>
      NostalgiaMode.fromStorage(Pref.nostalgiaMode);

  static bool get enabled => mode != NostalgiaMode.disabled;
  static bool get online => mode == NostalgiaMode.online;
  static bool get offline => mode == NostalgiaMode.offline;
}
