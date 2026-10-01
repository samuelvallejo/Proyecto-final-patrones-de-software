package com.streamguard.client;

/** Reads Spanish display copy loaded before the GWT entry point. */
public final class UiText {
  private UiText() {}

  public static native String get(String key) /*-{
    var value = $wnd.StreamGuardLocale[key];
    if (typeof value !== "string") throw new Error("Missing UI translation: " + key);
    return value;
  }-*/;
}
