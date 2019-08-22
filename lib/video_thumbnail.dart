/// The Flutter plugin for creating thumbnail from video
///
/// To use, import `package:video_thumbnail/video_thumbnail.dart`.
///
/// See also:
///
///  * [video_thumbnail](https://pub.dev/packages/video_thumbnail)
///
import 'dart:async';
import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Support most popular image formats.
/// Uses libwebp to encode WebP image on iOS platform.
enum ImageFormat { JPEG, PNG, WEBP }

class VideoThumbnail {
  static const MethodChannel _channel = const MethodChannel('video_thumbnail');
  static const String _VIDEO_FILE_CHANNEL_PREFIX = "video_thumbnail/file/";
  static const String _VIDEO_DATA_CHANNEL_PREFIX = "video_thumbnail/data/";

  /// Generates a thumbnail file under specified thumbnail folder or given full path and name which matches expected ext.
  /// The video can be a local video file, or an URL repreents iOS or Android native supported video format.
  /// If the thumbnailPath is ommited or null, a thumbnail image file will be created under the same folder as the video file.
  /// Speicify the maximum height or width for the thumbnail or 0 for same resolution as the original video.
  /// The lower quality value creates lower quality of the thumbnail image, but it gets ignored for PNG format.
  static Future<String> thumbnailFile(
      {@required String video,
      String thumbnailPath,
      ImageFormat imageFormat,
      int maxHeightOrWidth = 0,
      int quality}) async {
    assert(video != null && video.isNotEmpty);
    // Async receive file path from host
    var randomWord = video.hashCode.toString() + Random().nextInt(1000000).toString();
    Completer completer = new Completer<String>();
    defaultBinaryMessenger.setMessageHandler(_VIDEO_FILE_CHANNEL_PREFIX + randomWord,
        (ByteData message) async {
      completer.complete(utf8.decode(message.buffer.asUint8List()));
      defaultBinaryMessenger.setMessageHandler(
          _VIDEO_FILE_CHANNEL_PREFIX + randomWord, null);
      return message;
    });
    final reqMap = <String, dynamic>{
      'video': video,
      'path': thumbnailPath,
      'format': imageFormat.index,
      'maxhow': maxHeightOrWidth,
      'quality': quality,
      'randomword': randomWord
    };
    await _channel.invokeMethod('file', reqMap);
    return completer.future;
  }

  /// Generates a thumbnail image data in memory as UInt8List, it can be easily used by Image.memory(...).
  /// The video can be a local video file, or an URL repreents iOS or Android native supported video format.
  /// Speicify the maximum height or width for the thumbnail or 0 for same resolution as the original video.
  /// The lower quality value creates lower quality of the thumbnail image, but it gets ignored for PNG format.
  static Future<Uint8List> thumbnailData(
      {@required String video,
      ImageFormat imageFormat,
      int maxHeightOrWidth = 0,
      int quality}) async {
    assert(video != null && video.isNotEmpty);
    // Async receive data from host
    var randomWord = video.hashCode.toString() + Random().nextInt(1000000).toString();
    Completer completer = new Completer<Uint8List>();
    defaultBinaryMessenger.setMessageHandler(_VIDEO_DATA_CHANNEL_PREFIX + randomWord,
        (ByteData message) async {
      completer.complete(message.buffer.asUint8List());
      defaultBinaryMessenger.setMessageHandler(
          _VIDEO_DATA_CHANNEL_PREFIX + randomWord, null);
      return message;
    });
    final reqMap = <String, dynamic>{
      'video': video,
      'format': imageFormat.index,
      'maxhow': maxHeightOrWidth,
      'quality': quality,
      'randomword': randomWord
    };
    await _channel.invokeMethod('data', reqMap);
    return completer.future;
  }
}
