package com.streamguard.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.http.client.*;
import com.google.gwt.json.client.*;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.*;
import java.util.*;

/** Responsive PWA application written in Java and compiled by GWT. */
public class StreamGuardApp implements EntryPoint {
  private FlowPanel root, content, nav;
  private JSONObject user, config, dash;
  private JSONArray categories = new JSONArray();
  private String token = MediaBridge.token(),
      api = MediaBridge.apiUrl(),
      screen = "explore",
      channel = "",
      stream = "",
      search = "",
      category = "";
  private boolean broadcasting = false, mobileNav = false;
  private FlowPanel chatList;
  private Label audience;
  private Timer refresh;
  private HTML currentToast;
  private int fieldCounter = 0;
  private final Set<String> messageIds = new HashSet<String>();

  private interface Done {
    void accept(JSONValue value);
  }

  public void onModuleLoad() {
    if (MediaBridge.hash().startsWith("#clip=")) screen = "public-clips";
    RootPanel.get("app").getElement().setInnerHTML("");
    root = new FlowPanel();
    RootPanel.get("app").add(root);
    shell();
    api(
        "GET",
        "/config",
        null,
        v -> {
          config = obj(v);
          configureMedia();
          render();
        });
    api(
        "GET",
        "/categories",
        null,
        v -> {
          categories = arr(v);
          if (screen.equals("explore")) render();
        });
    if (!token.isEmpty())
      api(
          "GET",
          "/auth/me",
          null,
          v -> {
            user = obj(v);
            shell();
            render();
          });
    else render();
    refresh =
        new Timer() {
          public void run() {
            if ((screen.equals("moderation") || screen.equals("studio")) && !channel.isEmpty())
              loadDashboard(false);
          }
        };
    refresh.scheduleRepeating(15000);
  }

  private void configureMedia() {
    MediaBridge.configure(api, token, config == null ? "{}" : config.toString());
  }

  private void api(String method, String path, JSONValue body, Done done) {
    api(method, path, body, done, null);
  }

  private void api(String method, String path, JSONValue body, Done done, Runnable failed) {
    RequestBuilder.Method verb =
        method.equals("POST")
            ? RequestBuilder.POST
            : method.equals("PUT")
                ? RequestBuilder.PUT
                : method.equals("DELETE") ? RequestBuilder.DELETE : RequestBuilder.GET;
    RequestBuilder b = new RequestBuilder(verb, api + "/api" + path);
    b.setTimeoutMillis(55000);
    b.setHeader("Content-Type", "application/json");
    if (!token.isEmpty()) b.setHeader("Authorization", "Bearer " + token);
    try {
      b.sendRequest(
          body == null ? null : body.toString(),
          new RequestCallback() {
            public void onResponseReceived(Request request, Response response) {
              if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                try {
                  done.accept(
                      response.getText().isEmpty()
                          ? new JSONObject()
                          : JSONParser.parseStrict(response.getText()));
                } catch (Exception e) {
                  toast(UiText.get("uiOnResponseReceivedText01"), true);
                }
              } else {
                String error =
                    UiText.get("uiOnResponseReceivedText02") + response.getStatusCode() + ")";
                try {
                  error = text(obj(JSONParser.parseStrict(response.getText())), "error");
                } catch (Exception ignored) {
                }
                if (response.getStatusCode() == 401) {
                  token = "";
                  user = null;
                  MediaBridge.token("");
                  configureMedia();
                }
                toast(error, true);
                if (failed != null) failed.run();
              }
            }

            public void onError(Request request, Throwable e) {
              toast(UiText.get("uiOnErrorText03"), true);
              if (failed != null) failed.run();
            }
          });
    } catch (RequestException e) {
      toast(UiText.get("uiOnErrorText04"), true);
      if (failed != null) failed.run();
    }
  }

  private void shell() {
    root.clear();
    root.setStyleName("app-shell");
    FlowPanel side = panel("sidebar");
    side.add(html(UiText.get("uiShellText05") + icon("shield") + UiText.get("uiShellText06")));
    side.add(html(UiText.get("uiShellText07")));
    nav = panel("nav-list");
    side.add(nav);
    navButton("explore", UiText.get("uiShellText08"), "grid");
    navButton("studio", UiText.get("myStudio"), "video");
    side.add(html(UiText.get("uiShellText09")));
    FlowPanel second = panel("nav-list");
    FlowPanel first = nav;
    nav = second;
    navButton("moderation", UiText.get("uiShellText10"), "shield");
    navButton("clips", UiText.get("uiShellText11"), "play");
    navButton("assistant", UiText.get("uiShellText12"), "spark");
    navButton("settings", UiText.get("uiShellText13"), "settings");
    nav = first;
    side.add(second);
    side.add(html(UiText.get("uiShellText14")));
    root.add(side);
    FlowPanel main = panel("main-shell");
    FlowPanel header = panel("topbar");
    Button menu =
        button(
            "☰",
            "menu-button",
            () -> {
              mobileNav = !mobileNav;
              root.setStyleName("app-shell" + (mobileNav ? " nav-open" : ""));
            });
    menu.getElement().setAttribute("aria-label", UiText.get("uiShellText15"));
    header.add(menu);
    FlowPanel find = panel("global-search");
    find.add(html(icon("search")));
    TextBox query = input(UiText.get("uiShellText16"), search);
    query.addKeyDownHandler(
        e -> {
          if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            search = query.getText();
            route("explore");
          }
        });
    find.add(query);
    header.add(find);
    FlowPanel account = panel("account-actions");
    account.add(html(UiText.get("uiShellText17")));
    if (user == null)
      account.add(
          button(UiText.get("uiShellText18"), "button primary small", () -> route("login")));
    else {
      account.add(button(UiText.get("uiShellText19"), "button icon-button", this::notifications));
      Button badge =
          button(
              text(user, "username").substring(0, 1).toUpperCase(),
              "avatar",
              () -> route("account"));
      badge.getElement().setAttribute("aria-label", UiText.get("myAccount"));
      account.add(badge);
    }
    header.add(account);
    main.add(header);
    content = panel("page-content");
    main.add(content);
    root.add(main);
  }

  private void navButton(String key, String label, String glyph) {
    Button b = new Button();
    b.setHTML(
        icon(glyph)
            + "<span>"
            + esc(label)
            + "</span>"
            + (key.equals("moderation") ? UiText.get("uiNavButtonText20") : ""));
    b.getElement().setAttribute("aria-label", label);
    b.setStyleName("nav-item" + (screen.equals(key) ? " active" : ""));
    b.addClickHandler(e -> route(key));
    nav.add(b);
  }

  private void route(String next) {
    if (!broadcasting && screen.equals("watch")) MediaBridge.stop();
    screen = next;
    mobileNav = false;
    messageIds.clear();
    shell();
    render();
  }

  private void render() {
    content.clear();
    chatList = null;
    if (screen.equals("explore")) {
      explore();
      return;
    }
    if (screen.equals("login") || screen.equals("register")) {
      authView(screen.equals("register"));
      return;
    }
    if (screen.equals("watch")) {
      watch();
      return;
    }
    if (screen.equals("public-clips")) {
      publicClips();
      return;
    }
    if (user == null) {
      authView(false);
      return;
    }
    if (screen.equals("account")) {
      account();
      return;
    }
    if (channel.isEmpty()) {
      api(
          "GET",
          "/channels/mine",
          null,
          v -> {
            JSONArray rows = arr(v);
            if (rows.size() == 0) createChannel();
            else if (rows.size() > 1) chooseChannel(rows);
            else {
              channel = text(obj(rows.get(0)), "id");
              loadDashboard(true);
            }
          });
      return;
    }
    loadDashboard(true);
  }

  private void title(String eyebrow, String heading, String detail) {
    content.add(
        html(
            "<div class='page-heading'><p class='eyebrow'>"
                + esc(eyebrow)
                + "</p><h1>"
                + esc(heading)
                + "</h1><p class='muted'>"
                + esc(detail)
                + "</p></div>"));
  }

  private void explore() {
    FlowPanel hero = panel("hero");
    hero.add(
        html(
            UiText.get("uiExploreText21")
                + icon("shield")
                + "</div><div class='floating-label label-top'>"
                + icon("spark")
                + UiText.get("uiExploreText22")));
    FlowPanel heroActions = panel("hero-actions");
    heroActions.add(button(UiText.get("openStudio"), "button primary", () -> route("studio")));
    heroActions.add(
        button(UiText.get("uiExploreText23"), "button subtle", () -> route("public-clips")));
    hero.add(heroActions);
    content.add(hero);
    FlowPanel filters = panel("category-row");
    filters.add(filter(UiText.get("uiExploreText24"), ""));
    for (int i = 0; i < categories.size(); i++) {
      JSONObject cat = obj(categories.get(i));
      filters.add(filter(text(cat, "name"), text(cat, "slug")));
    }
    content.add(filters);
    FlowPanel section = panel("section-title");
    section.add(html(UiText.get("uiExploreText25")));
    section.add(html("<span class='live-pill'><span class='status-dot'></span>TIEMPO REAL</span>"));
    content.add(section);
    FlowPanel cards = panel("stream-grid");
    content.add(cards);
    cards.add(empty(UiText.get("uiExploreText26"), UiText.get("uiExploreText27")));
    api(
        "GET",
        "/explore?q="
            + URL.encodeQueryString(search)
            + "&category="
            + URL.encodeQueryString(category),
        null,
        v -> {
          cards.clear();
          JSONArray rows = arr(v);
          if (rows.size() == 0) {
            cards.add(
                empty(
                    UiText.get("uiExploreText28"),
                    search.isEmpty()
                        ? UiText.get("uiExploreText29")
                        : UiText.get("uiExploreText30")));
            return;
          }
          for (int i = 0; i < rows.size(); i++) {
            JSONObject s = obj(rows.get(i));
            FlowPanel card = panel("stream-card");
            card.add(
                html(
                    UiText.get("uiExploreText31")
                        + icon("video")
                        + "<span class='viewer-tag'>"
                        + esc(text(s, "viewers"))
                        + UiText.get("streamViewerCountSuffix")
                        + esc(text(s, "category"))
                        + "</span><h3>"
                        + esc(text(s, "title"))
                        + "</h3><p>"
                        + esc(text(s, "channel_name"))
                        + " <span class='verified'>✓</span></p></div>"));
            card.add(
                button(
                    UiText.get("uiExploreText32"),
                    "button subtle full",
                    () -> {
                      stream = text(s, "id");
                      route("watch");
                    }));
            cards.add(card);
          }
        },
        () -> {
          cards.clear();
          cards.add(empty(UiText.get("uiExploreText33"), UiText.get("uiExploreText34")));
        });
    content.add(
        html(
            "<div class='feature-strip'><article>"
                + icon("shield")
                + UiText.get("uiExploreText35")
                + icon("play")
                + UiText.get("uiExploreText36")
                + icon("video")
                + UiText.get("uiExploreText37")));
  }

  private Button filter(String label, String slug) {
    return button(
        label,
        "category-chip" + (category.equals(slug) ? " selected" : ""),
        () -> {
          category = slug;
          exploreReload();
        });
  }

  private void exploreReload() {
    content.clear();
    explore();
  }

  private void authView(boolean register) {
    title(
        UiText.get("welcomeEyebrow"),
        register ? UiText.get("uiAuthViewText38") : UiText.get("uiAuthViewText39"),
        register ? UiText.get("uiAuthViewText40") : UiText.get("uiAuthViewText41"));
    FlowPanel card = panel("form-card auth-card");
    TextBox username = input(UiText.get("uiAuthViewText42"), "");
    TextBox email = input(UiText.get("uiAuthViewText43"), "");
    email.getElement().setAttribute("type", "email");
    PasswordTextBox password = new PasswordTextBox();
    password.getElement().setAttribute("placeholder", UiText.get("uiAuthViewText44"));
    password
        .getElement()
        .setAttribute("autocomplete", register ? "new-password" : "current-password");
    if (register) card.add(field(UiText.get("uiAuthViewText45"), username));
    card.add(field(UiText.get("uiAuthViewText46"), email));
    card.add(field(UiText.get("uiAuthViewText47"), password));
    CheckBox consent = new CheckBox(UiText.get("uiAuthViewText48"));
    consent.setStyleName("consent");
    if (register) card.add(consent);
    Button submit =
        button(
            register ? UiText.get("createAccount") : UiText.get("signIn"),
            "button primary full",
            () -> {});
    Runnable send =
        () -> {
          if (register
              && (username.getText().length() < 3
                  || password.getText().length() < 10
                  || !consent.getValue())) {
            toast(UiText.get("uiAuthViewText49"), true);
            return;
          }
          submit.setEnabled(false);
          JSONObject body = object("email", email.getText(), "password", password.getText());
          if (register) {
            body.put("username", new JSONString(username.getText()));
            body.put("aiConsent", JSONBoolean.getInstance(consent.getValue()));
          }
          api(
              "POST",
              register ? "/auth/register" : "/auth/login",
              body,
              v -> {
                JSONObject data = obj(v);
                token = text(data, "token");
                MediaBridge.token(token);
                user = obj(data.get("user"));
                configureMedia();
                channel = "";
                dash = null;
                route("studio");
              },
              () -> submit.setEnabled(true));
        };
    submit.addClickHandler(e -> send.run());
    password.addKeyDownHandler(
        e -> {
          if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER) send.run();
        });
    card.add(submit);
    card.add(
        button(
            register ? UiText.get("uiAuthViewText50") : UiText.get("uiAuthViewText51"),
            "text-button",
            () -> route(register ? "login" : "register")));
    content.add(card);
  }

  private void chooseChannel(JSONArray rows) {
    title(
        UiText.get("uiChooseChannelText52"),
        UiText.get("uiChooseChannelText53"),
        UiText.get("uiChooseChannelText54"));
    for (int i = 0; i < rows.size(); i++) {
      JSONObject c = obj(rows.get(i));
      content.add(
          button(
              text(c, "name")
                  + (bool(c, "is_owner")
                      ? UiText.get("uiChooseChannelText55")
                      : UiText.get("moderatorSuffix")),
              "button subtle",
              () -> {
                channel = text(c, "id");
                loadDashboard(true);
              }));
    }
  }

  private void createChannel() {
    content.clear();
    title(
        UiText.get("uiCreateChannelText56"),
        UiText.get("uiCreateChannelText57"),
        UiText.get("uiCreateChannelText58"));
    FlowPanel form = panel("form-card auth-card");
    TextBox name = input(UiText.get("uiCreateChannelText59"), "");
    TextBox slug =
        input(
            UiText.get("uiCreateChannelText60"),
            text(user, "username").toLowerCase().replace('_', '-'));
    TextArea description = area(UiText.get("uiCreateChannelText61"), "");
    form.add(field(UiText.get("uiCreateChannelText59"), name));
    form.add(field(UiText.get("uiCreateChannelText63"), slug));
    form.add(field(UiText.get("uiCreateChannelText64"), description));
    form.add(
        button(
            UiText.get("uiCreateChannelText65"),
            "button primary",
            () ->
                api(
                    "POST",
                    "/channels",
                    object(
                        "name",
                        name.getText(),
                        "slug",
                        slug.getText(),
                        "description",
                        description.getText()),
                    v -> {
                      channel = text(obj(v), "id");
                      loadDashboard(true);
                    })));
    content.add(form);
  }

  private void loadDashboard(boolean render) {
    api(
        "GET",
        "/channels/" + channel + "/dashboard",
        null,
        v -> {
          dash = obj(v);
          if (render) {
            content.clear();
            if (screen.equals("settings")) settings();
            else if (screen.equals("moderation")) moderation();
            else if (screen.equals("clips")) clips();
            else if (screen.equals("assistant")) assistant();
            else studio();
          } else if (screen.equals("moderation")) {
            content.clear();
            moderation();
          }
        });
  }

  private void stats() {
    JSONObject stats = obj(dash.get("stats"));
    FlowPanel grid = panel("stats-grid");
    grid.add(
        metric(UiText.get("uiStatsText66"), text(stats, "followers"), UiText.get("uiStatsText67")));
    grid.add(
        metric(UiText.get("uiStatsText68"), text(stats, "messages"), UiText.get("uiStatsText69")));
    grid.add(
        metric(UiText.get("uiStatsText70"), text(stats, "blocked"), UiText.get("uiStatsText71")));
    grid.add(
        metric(
            UiText.get("uiStatsText72"),
            text(stats, "pending_clips"),
            UiText.get("uiStatsText73")));
    content.add(grid);
  }

  private boolean owner() {
    return user != null && text(obj(dash.get("channel")), "owner_id").equals(text(user, "id"));
  }

  private void studio() {
    title(
        UiText.get("uiStudioText74"),
        UiText.get("studioGreeting") + text(user, "username") + " ✦",
        UiText.get("uiStudioText75"));
    stats();
    JSONObject latest = obj(dash.get("stream"));
    if (!owner()) {
      content.add(
          html(
              UiText.get("uiStudioText76")
                  + esc(text(obj(dash.get("channel")), "name"))
                  + ".</div>"));
      if (text(latest, "status").equals("LIVE"))
        content.add(
            button(
                UiText.get("uiStudioText77"),
                "button primary",
                () -> {
                  stream = text(latest, "id");
                  route("watch");
                }));
      return;
    }
    FlowPanel layout = panel("studio-grid");
    FlowPanel left = panel("surface");
    left.add(
        html(
            UiText.get("uiStudioText78")
                + (broadcasting ? UiText.get("uiStudioText79") : UiText.get("uiStudioText80"))
                + "</span></div><div class='video-stage'><video id='live-video' autoplay"
                + " playsinline muted></video><div class='video-placeholder'"
                + " id='video-placeholder'>"
                + icon("video")
                + UiText.get("uiStudioText81")));
    if (broadcasting) {
      FlowPanel actions = panel("action-row");
      actions.add(
          button(
              UiText.get("uiStudioText82"),
              "button primary",
              () ->
                  api(
                      "POST",
                      "/streams/" + stream + "/highlights",
                      object("source", "MANUAL", "reason", UiText.get("uiStudioText83")),
                      v -> toast(UiText.get("uiStudioText84"), false))));
      actions.add(
          button(
              UiText.get("uiStudioText85"),
              "button subtle",
              () ->
                  MediaBridge.captions(
                      error -> {
                        if (!error.isEmpty()) toast(error, true);
                        else toast(UiText.get("uiStudioText86"), false);
                      })));
      actions.add(button(UiText.get("uiStudioText87"), "button danger", () -> finish()));
      left.add(actions);
      MediaBridge.attach();
    } else if (text(latest, "status").equals("LIVE")) {
      left.add(html(UiText.get("uiStudioText88")));
      left.add(
          button(
              UiText.get("uiStudioText89"),
              "button danger",
              () ->
                  api(
                      "POST",
                      "/streams/" + text(latest, "id") + "/end",
                      new JSONObject(),
                      v -> loadDashboard(true))));
    } else {
      FlowPanel form = panel("stream-form");
      TextBox name = input(UiText.get("uiStudioText90"), "");
      TextArea description = area(UiText.get("uiStudioText91"), "");
      ListBox cats = new ListBox();
      for (int i = 0; i < categories.size(); i++) {
        JSONObject c = obj(categories.get(i));
        cats.addItem(text(c, "name"), text(c, "id"));
      }
      CheckBox shareScreen = new CheckBox(UiText.get("uiStudioText92"));
      form.add(field(UiText.get("uiStudioText93"), name));
      FlowPanel row = panel("form-row");
      row.add(field(UiText.get("uiStudioText94"), cats));
      row.add(field(UiText.get("uiCreateChannelText64"), description));
      form.add(row);
      form.add(shareScreen);
      Button start = button(UiText.get("uiStudioText96"), "button primary", () -> {});
      start.addClickHandler(
          e -> {
            if (name.getText().isBlank()) {
              toast(UiText.get("uiStudioText97"), true);
              return;
            }
            start.setEnabled(false);
            MediaBridge.prepare(
                shareScreen.getValue(),
                error -> {
                  if (!error.isEmpty()) {
                    toast(error, true);
                    start.setEnabled(true);
                    return;
                  }
                  JSONObject body =
                      object(
                          "title",
                          name.getText(),
                          "description",
                          description.getText(),
                          "categoryId",
                          cats.getSelectedValue());
                  api(
                      "POST",
                      "/streams",
                      body,
                      v -> {
                        stream = text(obj(v), "id");
                        broadcasting = true;
                        MediaBridge.connect(stream, true, this::realtime);
                        loadDashboard(true);
                      },
                      () -> {
                        start.setEnabled(true);
                        MediaBridge.stop();
                      });
                });
          });
      form.add(start);
      left.add(form);
    }
    layout.add(left);
    FlowPanel right = panel("surface studio-side");
    right.add(html(UiText.get("uiStudioText98") + icon("spark") + "</div>"));
    right.add(aiStatus());
    JSONObject policy = obj(dash.get("policy"));
    right.add(
        html(
            "<div class='assistant-card'><span class='shield-orb'>"
                + icon("shield")
                + UiText.get("uiStudioText99")
                + esc(levelName(text(policy, "level")))
                + UiText.get("uiStudioText100")));
    right.add(
        button(UiText.get("uiStudioText101"), "button subtle full", () -> route("moderation")));
    right.add(button(UiText.get("uiStudioText102"), "button subtle full", () -> route("settings")));
    right.add(html(UiText.get("uiStudioText103")));
    layout.add(right);
    content.add(layout);
    if (broadcasting) {
      content.add(chatPanel());
      loadMessages();
      new Timer() {
        public void run() {
          MediaBridge.attach();
        }
      }.schedule(50);
    }
  }

  private void finish() {
    api(
        "POST",
        "/streams/" + stream + "/end",
        new JSONObject(),
        v -> {
          broadcasting = false;
          MediaBridge.stop();
          toast(UiText.get("uiFinishText104"), false);
          loadDashboard(true);
        });
  }

  private void watch() {
    title(UiText.get("uiWatchText105"), UiText.get("uiWatchText106"), UiText.get("uiWatchText107"));
    api(
        "GET",
        "/streams/" + stream,
        null,
        v -> {
          JSONObject s = obj(v);
          content.clear();
          title(text(s, "channel_name"), text(s, "title"), text(s, "description"));
          FlowPanel layout = panel("watch-grid");
          FlowPanel player = panel("surface");
          player.add(
              html(
                  "<div class='video-stage'><video id='live-video' autoplay playsinline"
                      + " controls></video><div class='video-placeholder' id='video-placeholder'>"
                      + icon("video")
                      + UiText.get("uiWatchText108")));
          FlowPanel bar = panel("action-row");
          audience = new Label(text(s, "viewers") + UiText.get("viewerCountSuffix"));
          bar.add(audience);
          bar.add(
              button(
                  UiText.get("uiWatchText109"),
                  "button primary small",
                  () -> {
                    if (user == null) toast(UiText.get("uiWatchText110"), true);
                    else
                      api(
                          "POST",
                          "/channels/" + text(s, "channel_id") + "/follow",
                          new JSONObject(),
                          r -> toast(UiText.get("uiWatchText111"), false));
                  }));
          player.add(bar);
          layout.add(player);
          layout.add(chatPanel());
          content.add(layout);
          if (text(s, "status").equals("LIVE")) {
            loadMessages();
            MediaBridge.connect(stream, false, this::realtime);
          } else toast(UiText.get("uiWatchText112"), false);
        });
  }

  private FlowPanel chatPanel() {
    messageIds.clear();
    FlowPanel panel = panel("surface chat-panel");
    panel.add(
        html(
            UiText.get("uiChatPanelText113") + icon("shield") + UiText.get("chatModeratedSuffix")));
    chatList = panel("chat-list");
    panel.add(chatList);
    TextBox message =
        input(
            user == null ? UiText.get("uiChatPanelText114") : UiText.get("uiChatPanelText115"), "");
    message.setMaxLength(1000);
    FlowPanel send = panel("chat-compose");
    send.add(message);
    Button submit = button(UiText.get("uiChatPanelText116"), "button primary small", () -> {});
    Runnable action =
        () -> {
          if (user == null) {
            toast(UiText.get("uiChatPanelText117"), true);
            return;
          }
          if (message.getText().isBlank()) return;
          submit.setEnabled(false);
          api(
              "POST",
              "/streams/" + stream + "/messages",
              object("content", message.getText()),
              v -> {
                message.setText("");
                submit.setEnabled(true);
                JSONObject m = obj(v);
                if (!text(m, "status").equals("VISIBLE")) toast(text(m, "reason"), false);
              },
              () -> submit.setEnabled(true));
        };
    submit.addClickHandler(e -> action.run());
    message.addKeyDownHandler(
        e -> {
          if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER) action.run();
        });
    send.add(submit);
    panel.add(send);
    panel.add(html(UiText.get("uiChatPanelText118")));
    return panel;
  }

  private void loadMessages() {
    if (stream.isEmpty()) return;
    api(
        "GET",
        "/streams/" + stream + "/messages",
        null,
        v -> {
          JSONArray rows = arr(v);
          for (int i = rows.size() - 1; i >= 0; i--) appendMessage(obj(rows.get(i)));
        });
  }

  private void appendMessage(JSONObject m) {
    if (chatList == null) return;
    String id = text(m, "id");
    if (!messageIds.add(id)) return;
    FlowPanel row = panel("chat-message");
    row.add(
        html(
            "<span class='chat-author'>"
                + esc(text(m, "username"))
                + "</span><span>"
                + esc(text(m, "content"))
                + "</span>"));
    chatList.add(row);
    if (chatList.getWidgetCount() > 100) chatList.remove(0);
    chatList.getElement().setScrollTop(chatList.getElement().getScrollHeight());
  }

  private void realtime(String event) {
    try {
      JSONObject e = obj(JSONParser.parseStrict(event));
      String type = text(e, "type");
      if (type.equals("message")) appendMessage(obj(e.get("message")));
      else if (type.equals("presence") && audience != null)
        audience.setText(text(e, "viewers") + UiText.get("viewerCountSuffix"));
      else if (type.equals("notice")) toast(text(e, "body"), false);
      else if (type.equals("error")) toast(text(e, "message"), true);
      else if (type.equals("ended")) {
        broadcasting = false;
        toast(text(e, "message"), false);
        MediaBridge.stop();
        if (screen.equals("studio")) loadDashboard(true);
      } else if (type.equals("queue-updated") && screen.equals("moderation")) loadDashboard(true);
      else if (type.equals("clips-updated")) {
        toast(UiText.get("uiRealtimeText119"), false);
        if (screen.equals("clips")) loadDashboard(true);
      } else if (type.equals("capture-status")) toast(text(e, "message"), bool(e, "error"));
    } catch (Exception ignored) {
    }
  }

  private void moderation() {
    title(
        UiText.get("uiModerationText120"),
        UiText.get("uiShellText10"),
        UiText.get("uiModerationText122"));
    stats();
    content.add(aiStatus());
    JSONArray queue = arr(dash.get("queue"));
    FlowPanel list = panel("surface moderation-list");
    list.add(html(UiText.get("uiModerationText123") + queue.size() + "</span></div>"));
    if (queue.size() == 0)
      list.add(empty(UiText.get("uiModerationText124"), UiText.get("uiModerationText125")));
    for (int i = 0; i < queue.size(); i++) {
      JSONObject q = obj(queue.get(i));
      FlowPanel row = panel("review-row");
      row.add(
          html(
              "<div class='review-content'><div class='review-meta'><strong>@"
                  + esc(text(q, "username"))
                  + "</strong><span class='tag'>"
                  + esc(text(q, "category"))
                  + "</span><span class='muted'>"
                  + esc(providerName(text(q, "provider")))
                  + "</span></div><p class='quoted-message'>"
                  + esc(text(q, "content"))
                  + "</p><p class='muted'>"
                  + esc(text(q, "reason"))
                  + "</p></div>"));
      FlowPanel actions = panel("action-row");
      actions.add(
          button(
              UiText.get("uiModerationText126"),
              "button primary small",
              () -> reviewMessage(q, true)));
      actions.add(
          button(
              UiText.get("uiModerationText127"),
              "button danger small",
              () -> reviewMessage(q, false)));
      actions.add(
          button(
              UiText.get("muteFiveMinutes"),
              "button subtle small",
              () ->
                  api(
                      "POST",
                      "/channels/" + channel + "/sanctions",
                      object(
                          "userId",
                          text(q, "user_id"),
                          "type",
                          "MUTE",
                          "seconds",
                          300,
                          "reason",
                          UiText.get("uiModerationText128")),
                      v -> {
                        toast(UiText.get("uiModerationText129"), false);
                        loadDashboard(true);
                      })));
      row.add(actions);
      list.add(row);
    }
    content.add(list);
    FlowPanel sanctions = panel("surface");
    sanctions.add(html("<div class='panel-heading'><h2>Sanciones activas</h2></div>"));
    JSONArray rows = arr(dash.get("sanctions"));
    if (rows.size() == 0) sanctions.add(html(UiText.get("uiModerationText130")));
    for (int i = 0; i < rows.size(); i++) {
      JSONObject s = obj(rows.get(i));
      FlowPanel row = panel("simple-row");
      row.add(
          new Label(
              "@"
                  + text(s, "username")
                  + " · "
                  + (text(s, "type").equals("BAN")
                      ? UiText.get("uiModerationText131")
                      : UiText.get("uiModerationText132"))
                  + " · "
                  + text(s, "expires_at")));
      row.add(
          button(
              UiText.get("uiModerationText133"),
              "button subtle small",
              () -> api("DELETE", "/sanctions/" + text(s, "id"), null, v -> loadDashboard(true))));
      sanctions.add(row);
    }
    content.add(sanctions);
  }

  private void reviewMessage(JSONObject row, boolean approve) {
    api(
        "POST",
        "/moderation/" + text(row, "id") + "/review",
        object("approve", approve),
        v -> {
          toast(
              approve ? UiText.get("uiReviewMessageText134") : UiText.get("uiReviewMessageText135"),
              false);
          loadDashboard(true);
        });
  }

  private void clips() {
    title(UiText.get("uiClipsText136"), UiText.get("uiClipsText137"), UiText.get("uiClipsText138"));
    JSONArray rows = arr(dash.get("clips"));
    FlowPanel grid = panel("clip-grid");
    if (rows.size() == 0)
      grid.add(empty(UiText.get("uiClipsText139"), UiText.get("uiClipsText140")));
    for (int i = 0; i < rows.size(); i++) grid.add(clipCard(obj(rows.get(i)), owner()));
    content.add(grid);
  }

  private FlowPanel clipCard(JSONObject clip, boolean manage) {
    FlowPanel card = panel("surface clip-card");
    String asset = text(clip, "asset_id"), id = "clip-" + text(clip, "id");
    card.add(
        html(
            "<div class='clip-stage'><video id='"
                + esc(id)
                + "' playsinline controls></video></div><div class='clip-copy'><div"
                + " class='review-meta'><span class='tag'>"
                + esc(statusName(text(clip, "status")))
                + "</span><span class='muted'>"
                + esc(text(clip, "start_seconds"))
                + " – "
                + esc(text(clip, "end_seconds"))
                + " s</span></div><h3>"
                + esc(text(clip, "title"))
                + "</h3><p class='muted'>"
                + esc(text(clip, "description"))
                + "</p></div>"));
    FlowPanel actions = panel("action-row clip-actions");
    actions.add(
        button(
            UiText.get("viewVideo"),
            "button subtle small",
            () ->
                MediaBridge.playback(
                    asset,
                    id,
                    false,
                    e -> {
                      if (!e.isEmpty()) toast(e, true);
                    })));
    actions.add(
        button(
            UiText.get("uiClipCardText141"),
            "button subtle small",
            () ->
                MediaBridge.playback(
                    asset,
                    id,
                    true,
                    e -> {
                      if (!e.isEmpty()) toast(e, true);
                    })));
    if (manage) {
      actions.add(
          button(UiText.get("uiClipCardText142"), "button subtle small", () -> editClip(clip)));
      actions.add(
          button(
              UiText.get("uiClipCardText143"),
              "button primary small",
              () -> clipReview(clip, true)));
      actions.add(
          button(
              UiText.get("uiClipCardText144"),
              "button danger small",
              () -> clipReview(clip, false)));
    }
    if (text(clip, "status").equals("APPROVED"))
      actions.add(
          button(
              UiText.get("uiClipCardText145"),
              "button subtle small",
              () -> {
                MediaBridge.share(asset, text(clip, "title"));
                toast(UiText.get("uiClipCardText146"), false);
              }));
    card.add(actions);
    return card;
  }

  private void clipReview(JSONObject clip, boolean approve) {
    api(
        "POST",
        "/clips/" + text(clip, "id") + "/review",
        object("approve", approve),
        v -> {
          toast(approve ? UiText.get("clipPublished") : UiText.get("clipRejected"), false);
          loadDashboard(true);
        });
  }

  private void editClip(JSONObject clip) {
    DialogBox dialog = new DialogBox();
    dialog.setText(UiText.get("editClip"));
    dialog.setGlassEnabled(true);
    FlowPanel form = panel("dialog-form");
    TextBox title = input(UiText.get("uiEditClipText147"), text(clip, "title"));
    TextArea desc = area(UiText.get("uiCreateChannelText64"), text(clip, "description"));
    TextBox start = input("0", "0"),
        end =
            input(
                UiText.get("uiEditClipText149"),
                Integer.toString(
                    (int) (number(clip, "end_seconds") - number(clip, "start_seconds"))));
    form.add(field(UiText.get("uiEditClipText147"), title));
    form.add(field(UiText.get("uiCreateChannelText64"), desc));
    form.add(html(UiText.get("uiEditClipText152")));
    FlowPanel row = panel("form-row");
    row.add(field(UiText.get("trimStart"), start));
    row.add(field(UiText.get("trimEnd"), end));
    form.add(row);
    form.add(
        button(
            UiText.get("saveChanges"),
            "button primary",
            () -> {
              try {
                api(
                    "PUT",
                    "/clips/" + text(clip, "id"),
                    object(
                        "title",
                        title.getText(),
                        "description",
                        desc.getText(),
                        "start",
                        Double.parseDouble(start.getText()),
                        "end",
                        Double.parseDouble(end.getText())),
                    v -> {
                      dialog.hide();
                      loadDashboard(true);
                    });
              } catch (Exception e) {
                toast(UiText.get("uiEditClipText153"), true);
              }
            }));
    form.add(button(UiText.get("uiEditClipText154"), "button subtle", dialog::hide));
    dialog.setWidget(form);
    dialog.center();
    title.setFocus(true);
  }

  private void publicClips() {
    title(
        UiText.get("uiPublicClipsText155"),
        UiText.get("uiPublicClipsText156"),
        UiText.get("uiPublicClipsText157"));
    FlowPanel grid = panel("clip-grid");
    content.add(grid);
    api(
        "GET",
        "/clips/public",
        null,
        v -> {
          JSONArray rows = arr(v);
          if (rows.size() == 0)
            grid.add(empty(UiText.get("uiPublicClipsText158"), UiText.get("uiPublicClipsText159")));
          for (int i = 0; i < rows.size(); i++) grid.add(clipCard(obj(rows.get(i)), false));
          String hash = MediaBridge.hash();
          if (hash.startsWith("#clip=")) {
            String asset = hash.substring(6);
            for (int i = 0; i < rows.size(); i++) {
              JSONObject c = obj(rows.get(i));
              if (text(c, "asset_id").equals(asset))
                MediaBridge.playback(
                    asset,
                    "clip-" + text(c, "id"),
                    false,
                    e -> {
                      if (!e.isEmpty()) toast(e, true);
                    });
            }
          }
        });
  }

  private void assistant() {
    title(
        UiText.get("uiAssistantText160"),
        UiText.get("uiAssistantText161"),
        UiText.get("uiAssistantText162"));
    content.add(aiStatus());
    JSONObject latest = obj(dash.get("stream"));
    if (text(latest, "id").isEmpty()) {
      content.add(empty(UiText.get("uiAssistantText163"), UiText.get("uiAssistantText164")));
      return;
    }
    content.add(
        button(
            UiText.get("uiAssistantText165"),
            "button primary",
            () ->
                api(
                    "POST",
                    "/streams/" + text(latest, "id") + "/summary",
                    new JSONObject(),
                    v -> {
                      toast(
                          UiText.get("uiAssistantText166") + providerName(text(obj(v), "provider")),
                          false);
                      loadDashboard(true);
                    })));
    JSONObject ai = obj(dash.get("ai"));
    JSONArray summaries = arr(ai.get("summaries"));
    FlowPanel surface = panel("surface assistant-summary");
    surface.add(html(UiText.get("uiAssistantText167") + icon("spark") + "</div>"));
    surface.add(
        html(
            "<p class='summary-text'>"
                + esc(
                    summaries.size() > 0
                        ? text(obj(summaries.get(0)), "content")
                        : UiText.get("uiAssistantText168"))
                + "</p>"));
    JSONArray topics = arr(ai.get("topics"));
    FlowPanel tags = panel("category-row");
    for (int i = 0; i < topics.size(); i++)
      tags.add(
          html(
              "<span class='category-chip'>" + esc(text(obj(topics.get(i)), "label")) + "</span>"));
    surface.add(tags);
    content.add(surface);
    FlowPanel faqs = panel("surface assistant-summary");
    faqs.add(html(UiText.get("uiAssistantText169")));
    JSONArray faq = arr(ai.get("faqs"));
    if (faq.size() == 0) faqs.add(html(UiText.get("uiAssistantText170")));
    for (int i = 0; i < faq.size(); i++) {
      JSONObject f = obj(faq.get(i));
      faqs.add(
          html(
              "<article class='faq-item'><h3>"
                  + esc(text(f, "question"))
                  + "</h3><p>"
                  + esc(text(f, "answer"))
                  + "</p></article>"));
    }
    content.add(faqs);
    content.add(html(UiText.get("uiAssistantText171")));
  }

  private void settings() {
    title(
        UiText.get("uiSettingsText172"),
        UiText.get("uiSettingsText173"),
        UiText.get("uiSettingsText174"));
    if (!owner()) {
      content.add(empty(UiText.get("uiSettingsText175"), UiText.get("uiSettingsText176")));
      return;
    }
    JSONObject p = obj(dash.get("policy")), s = obj(dash.get("settings"));
    FlowPanel form = panel("surface settings-form");
    ListBox level = new ListBox();
    level.addItem(UiText.get("uiSettingsText177"), "RELAXED");
    level.addItem(UiText.get("uiSettingsText178"), "BALANCED");
    level.addItem(UiText.get("uiSettingsText179"), "STRICT");
    for (int i = 0; i < level.getItemCount(); i++)
      if (level.getValue(i).equals(text(p, "level"))) level.setSelectedIndex(i);
    form.add(field(UiText.get("uiSettingsText180"), level));
    CheckBox hide = check(UiText.get("uiSettingsText181"), bool(p, "autoHide")),
        mute = check(UiText.get("uiSettingsText182"), bool(p, "autoMute")),
        links = check(UiText.get("uiSettingsText183"), bool(p, "allowLinks")),
        auto = check(UiText.get("uiSettingsText184"), bool(s, "auto_clips"));
    form.add(hide);
    form.add(mute);
    form.add(links);
    form.add(auto);
    TextBox seconds = input("300", text(p, "muteSeconds")),
        slow = input("0", text(s, "slow_mode_seconds"));
    FlowPanel row = panel("form-row");
    row.add(field(UiText.get("uiSettingsText185"), seconds));
    row.add(field(UiText.get("uiSettingsText186"), slow));
    form.add(row);
    TextArea words = area(UiText.get("uiSettingsText187"), join(arr(p.get("blockedWords")))),
        topics = area(UiText.get("uiSettingsText188"), join(arr(p.get("blockedTopics"))));
    form.add(field(UiText.get("restrictedWords"), words));
    form.add(field(UiText.get("uiSettingsText189"), topics));
    form.add(
        button(
            UiText.get("uiSettingsText190"),
            "button primary",
            () -> {
              try {
                double review =
                    level.getSelectedValue().equals("STRICT")
                        ? .4
                        : level.getSelectedValue().equals("RELAXED") ? .7 : .55;
                double block =
                    level.getSelectedValue().equals("STRICT")
                        ? .7
                        : level.getSelectedValue().equals("RELAXED") ? .95 : .85;
                JSONObject body =
                    object(
                        "level",
                        level.getSelectedValue(),
                        "autoHide",
                        hide.getValue(),
                        "autoMute",
                        mute.getValue(),
                        "muteSeconds",
                        Integer.parseInt(seconds.getText()),
                        "reviewThreshold",
                        review,
                        "blockThreshold",
                        block,
                        "allowLinks",
                        links.getValue(),
                        "slowMode",
                        Integer.parseInt(slow.getText()),
                        "autoClips",
                        auto.getValue());
                body.put("blockedWords", lines(words.getText()));
                body.put("blockedTopics", lines(topics.getText()));
                api(
                    "PUT",
                    "/channels/" + channel + "/policy",
                    body,
                    v -> {
                      toast(UiText.get("uiSettingsText191"), false);
                      loadDashboard(true);
                    });
              } catch (Exception e) {
                toast(UiText.get("uiSettingsText192"), true);
              }
            }));
    content.add(form);
    FlowPanel mods = panel("surface settings-form");
    mods.add(html(UiText.get("uiSettingsText193")));
    TextBox name = input(UiText.get("uiAuthViewText45"), "");
    mods.add(field(UiText.get("uiSettingsText195"), name));
    mods.add(
        button(
            UiText.get("addModerator"),
            "button subtle",
            () ->
                api(
                    "POST",
                    "/channels/" + channel + "/moderators",
                    object("username", name.getText()),
                    v -> loadDashboard(true))));
    JSONArray rows = arr(dash.get("moderators"));
    for (int i = 0; i < rows.size(); i++) {
      JSONObject mod = obj(rows.get(i));
      FlowPanel r = panel("simple-row");
      r.add(new Label("@" + text(mod, "username")));
      r.add(
          button(
              UiText.get("uiSettingsText196"),
              "button danger small",
              () ->
                  api(
                      "DELETE",
                      "/channels/" + channel + "/moderators/" + text(mod, "user_id"),
                      null,
                      v -> loadDashboard(true))));
      mods.add(r);
    }
    content.add(mods);
  }

  private void notifications() {
    DialogBox dialog = new DialogBox();
    dialog.setGlassEnabled(true);
    dialog.setText(UiText.get("uiNotificationsText197"));
    FlowPanel list = panel("dialog-form");
    dialog.setWidget(list);
    list.add(button(UiText.get("uiNotificationsText198"), "button subtle", dialog::hide));
    api(
        "GET",
        "/notifications",
        null,
        v -> {
          JSONArray rows = arr(v);
          if (rows.size() == 0) list.add(new Label(UiText.get("uiNotificationsText199")));
          for (int i = 0; i < rows.size(); i++) {
            JSONObject n = obj(rows.get(i));
            list.add(
                html(
                    "<article class='faq-item'><h3>"
                        + esc(text(n, "title"))
                        + "</h3><p>"
                        + esc(text(n, "body"))
                        + "</p></article>"));
            api("POST", "/notifications/" + text(n, "id") + "/read", new JSONObject(), r -> {});
          }
          dialog.center();
        });
  }

  private void account() {
    title(UiText.get("uiAccountText200"), "@" + text(user, "username"), text(user, "email"));
    content.add(
        button(
            UiText.get("uiAccountText201"),
            "button subtle",
            () -> {
              channel = "";
              route("studio");
            }));
    content.add(
        button(
            UiText.get("uiAccountText202"),
            "button danger",
            () -> {
              Runnable logout =
                  () ->
                      api(
                          "POST",
                          "/auth/logout",
                          new JSONObject(),
                          v -> {
                            MediaBridge.stop();
                            broadcasting = false;
                            token = "";
                            user = null;
                            channel = "";
                            dash = null;
                            MediaBridge.token("");
                            configureMedia();
                            route("explore");
                          });
              if (broadcasting)
                api("POST", "/streams/" + stream + "/end", new JSONObject(), v -> logout.run());
              else logout.run();
            }));
  }

  private Widget aiStatus() {
    boolean gemini = config != null && bool(config, "geminiConfigured");
    return html(
        "<div class='info-banner "
            + (gemini ? "success-banner" : "")
            + "'>"
            + icon("spark")
            + "<div><strong>"
            + (gemini ? UiText.get("uiAiStatusText203") : UiText.get("uiAiStatusText204"))
            + "</strong><p>"
            + (gemini ? UiText.get("uiAiStatusText205") : UiText.get("uiAiStatusText206"))
            + "</p></div></div>");
  }

  private Widget metric(String label, String value, String hint) {
    return html(
        "<article class='metric'><span>"
            + esc(label)
            + "</span><strong>"
            + esc(value)
            + "</strong><small>"
            + esc(hint)
            + "</small></article>");
  }

  private Widget empty(String heading, String body) {
    HTML widget =
        html(
            "<div class='empty-state'><span class='empty-symbol'>"
                + icon("spark")
                + "</span><h3>"
                + esc(heading)
                + "</h3><p>"
                + esc(body)
                + "</p></div>");
    widget.getElement().getStyle().setProperty("gridColumn", "1 / -1");
    return widget;
  }

  private FlowPanel panel(String style) {
    FlowPanel p = new FlowPanel();
    p.setStyleName(style);
    return p;
  }

  private HTML html(String safeHtml) {
    return new HTML(safeHtml);
  }

  private TextBox input(String placeholder, String value) {
    TextBox b = new TextBox();
    b.setText(value);
    b.getElement().setAttribute("placeholder", placeholder);
    return b;
  }

  private TextArea area(String placeholder, String value) {
    TextArea b = new TextArea();
    b.setText(value);
    b.setVisibleLines(3);
    b.getElement().setAttribute("placeholder", placeholder);
    return b;
  }

  private CheckBox check(String label, boolean value) {
    CheckBox box = new CheckBox(label);
    box.setValue(value);
    box.setStyleName("setting-checkbox");
    return box;
  }

  private Widget field(String label, Widget input) {
    String id = "field-" + (++fieldCounter);
    input.getElement().setId(id);
    FlowPanel field = panel("form-field");
    field.add(html("<label for='" + id + "'>" + esc(label) + "</label>"));
    field.add(input);
    return field;
  }

  private Button button(String text, String style, Runnable action) {
    Button b = new Button(text);
    b.setStyleName(style);
    b.addClickHandler(e -> action.run());
    return b;
  }

  private void toast(String message, boolean error) {
    if (currentToast != null) currentToast.removeFromParent();
    HTML notice = new HTML(esc(message));
    currentToast = notice;
    notice.setStyleName("toast" + (error ? " toast-error" : ""));
    notice.getElement().setAttribute("role", error ? "alert" : "status");
    RootPanel.get().add(notice);
    new Timer() {
      public void run() {
        notice.removeFromParent();
      }
    }.schedule(error ? 9000 : 5500);
  }

  private static JSONObject object(Object... pairs) {
    JSONObject out = new JSONObject();
    for (int i = 0; i < pairs.length; i += 2) {
      Object value = pairs[i + 1];
      out.put(
          pairs[i].toString(),
          value instanceof Boolean
              ? JSONBoolean.getInstance((Boolean) value)
              : value instanceof Number
                  ? new JSONNumber(((Number) value).doubleValue())
                  : value == null ? JSONNull.getInstance() : new JSONString(value.toString()));
    }
    return out;
  }

  private static JSONObject obj(JSONValue v) {
    return v != null && v.isObject() != null ? v.isObject() : new JSONObject();
  }

  private static JSONArray arr(JSONValue v) {
    return v != null && v.isArray() != null ? v.isArray() : new JSONArray();
  }

  private static String text(JSONObject o, String key) {
    JSONValue v = o.get(key);
    if (v == null || v.isNull() != null) return "";
    if (v.isString() != null) return v.isString().stringValue();
    if (v.isNumber() != null) {
      double d = v.isNumber().doubleValue();
      return d == (long) d ? Long.toString((long) d) : Double.toString(d);
    }
    return v.toString();
  }

  private static boolean bool(JSONObject o, String key) {
    return o.get(key) != null
        && o.get(key).isBoolean() != null
        && o.get(key).isBoolean().booleanValue();
  }

  private static double number(JSONObject o, String key) {
    try {
      return Double.parseDouble(text(o, key));
    } catch (Exception e) {
      return 0;
    }
  }

  private static String esc(String s) {
    return SafeHtmlUtils.htmlEscape(s == null ? "" : s);
  }

  private static JSONArray lines(String s) {
    JSONArray out = new JSONArray();
    int i = 0;
    for (String word : s.split("\\n"))
      if (!word.trim().isEmpty()) out.set(i++, new JSONString(word.trim()));
    return out;
  }

  private static String join(JSONArray values) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) out.append('\n');
      out.append(values.get(i).isString().stringValue());
    }
    return out.toString();
  }

  private static String levelName(String s) {
    return s.equals("STRICT")
        ? UiText.get("uiLevelNameText207")
        : s.equals("RELAXED") ? UiText.get("uiLevelNameText208") : UiText.get("uiLevelNameText209");
  }

  private static String providerName(String s) {
    return s.equals("GEMINI")
        ? "Gemini"
        : s.equals("UNAVAILABLE")
            ? UiText.get("uiProviderNameText210")
            : UiText.get("uiProviderNameText211");
  }

  private static String statusName(String s) {
    return s.equals("APPROVED")
        ? UiText.get("uiStatusNameText212")
        : s.equals("REJECTED")
            ? UiText.get("uiStatusNameText213")
            : UiText.get("uiStatusNameText214");
  }

  private static String icon(String key) {
    String path;
    if (key.equals("shield"))
      path = "<path d='M12 3 4 6v6c0 5 8 9 8 9s8-4 8-9V6l-8-3Z'/><path d='m8 12 3 3 5-6'/>";
    else if (key.equals("grid"))
      path =
          "<rect x='3' y='3' width='7' height='7' rx='2'/><rect x='14' y='3' width='7' height='7'"
              + " rx='2'/><rect x='3' y='14' width='7' height='7' rx='2'/><rect x='14' y='14'"
              + " width='7' height='7' rx='2'/>";
    else if (key.equals("video"))
      path = "<rect x='3' y='5' width='13' height='14' rx='3'/><path d='m16 9 5-3v12l-5-3Z'/>";
    else if (key.equals("play"))
      path = "<rect x='3' y='3' width='18' height='18' rx='5'/><path d='m10 8 6 4-6 4Z'/>";
    else if (key.equals("search")) path = "<circle cx='10' cy='10' r='6'/><path d='m15 15 6 6'/>";
    else if (key.equals("settings"))
      path =
          "<path d='M4 7h16M4 17h16'/><circle cx='8' cy='7' r='3'/><circle cx='16' cy='17' r='3'/>";
    else
      path =
          "<path d='m12 3 2.4 6.6L21 12l-6.6 2.4L12 21l-2.4-6.6L3 12l6.6-2.4Z'/><path d='m20 2 .8"
              + " 2.2L23 5l-2.2.8L20 8l-.8-2.2L17 5l2.2-.8Z'/>";
    return "<svg viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.7'"
               + " stroke-linecap='round' stroke-linejoin='round' aria-hidden='true'>"
        + path
        + "</svg>";
  }
}
