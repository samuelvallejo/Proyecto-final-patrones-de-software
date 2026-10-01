package com.streamguard.client;

/** Narrow browser adapter: WebRTC, camera, recorder and native speech APIs. UI stays in Java. */
public final class MediaBridge {
  public interface Result {
    void done(String error);
  }

  public interface Event {
    void receive(String event);
  }

  public static native String
      apiUrl() /*-{ return $wnd.STREAMGUARD_API || "http://localhost:8080"; }-*/;

  public static native String
      token() /*-{ return $wnd.sessionStorage.getItem("streamguard.token") || ""; }-*/;

  public static native void token(
      String
          value) /*-{ if(value)$wnd.sessionStorage.setItem("streamguard.token",value);else $wnd.sessionStorage.removeItem("streamguard.token"); }-*/;

  public static native void configure(
      String api,
      String token,
      String config) /*-{ $wnd.StreamMedia.configure(api,token,JSON.parse(config)); }-*/;

  public static native void prepare(
      boolean screen,
      Result
          callback) /*-{ $wnd.StreamMedia.prepare(screen).then($entry(function(){callback.@com.streamguard.client.MediaBridge.Result::done(Ljava/lang/String;)("");}))["catch"]($entry(function(e){callback.@com.streamguard.client.MediaBridge.Result::done(Ljava/lang/String;)(e.message || "No se pudo acceder a cámara y micrófono");})); }-*/;

  public static native void connect(
      String stream,
      boolean host,
      Event
          callback) /*-{ $wnd.StreamMedia.connect(stream,host,$entry(function(event){callback.@com.streamguard.client.MediaBridge.Event::receive(Ljava/lang/String;)(JSON.stringify(event));})); }-*/;

  public static native void attach() /*-{ $wnd.StreamMedia.attach(); }-*/;

  public static native void stop() /*-{ $wnd.StreamMedia.stop(); }-*/;

  public static native void captions(
      Result
          callback) /*-{ $wnd.StreamMedia.captions().then($entry(function(){callback.@com.streamguard.client.MediaBridge.Result::done(Ljava/lang/String;)("");}))["catch"]($entry(function(e){callback.@com.streamguard.client.MediaBridge.Result::done(Ljava/lang/String;)(e.message);})); }-*/;

  public static native void playback(
      String asset,
      String element,
      boolean download,
      Result
          callback) /*-{ $wnd.StreamMedia.playback(asset,element,download).then($entry(function(){callback.@com.streamguard.client.MediaBridge.Result::done(Ljava/lang/String;)("");}))["catch"]($entry(function(e){callback.@com.streamguard.client.MediaBridge.Result::done(Ljava/lang/String;)(e.message);})); }-*/;

  public static native void share(
      String asset,
      String
          title) /*-{ var url=$wnd.location.origin+"/#clip="+asset; if($wnd.navigator.share)$wnd.navigator.share({title:title,url:url})["catch"](function(){}); else if($wnd.navigator.clipboard)$wnd.navigator.clipboard.writeText(url); }-*/;

  public static native String hash() /*-{ return $wnd.location.hash; }-*/;
}
