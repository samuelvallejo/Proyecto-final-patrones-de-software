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
                  toast("No se pudo mostrar la respuesta del servidor", true);
                }
              } else {
                String error =
                    "No se pudo completar la operación (" + response.getStatusCode() + ")";
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
              toast(
                  "No se pudo conectar al backend. Revisa la dirección de la API y vuelve a"
                      + " intentar.",
                  true);
              if (failed != null) failed.run();
            }
          });
    } catch (RequestException e) {
      toast("Error al enviar la solicitud", true);
      if (failed != null) failed.run();
    }
  }

  private void shell() {
    root.clear();
    root.setStyleName("app-shell");
    FlowPanel side = panel("sidebar");
    side.add(
        html(
            "<a class='brand' href='#' aria-label='StreamGuard, inicio'>"
                + icon("shield")
                + "<span>stream<span class='brand-accent'>guard</span><small>LIVE, CON"
                + " CONFIANZA</small></span></a>"));
    side.add(html("<p class='nav-label'>DESCUBRE</p>"));
    nav = panel("nav-list");
    side.add(nav);
    navButton("explore", "Explorar", "grid");
    navButton("studio", "Mi estudio", "video");
    side.add(html("<p class='nav-label nav-second'>TU COMUNIDAD</p>"));
    FlowPanel second = panel("nav-list");
    FlowPanel first = nav;
    nav = second;
    navButton("moderation", "Moderación", "shield");
    navButton("clips", "Biblioteca de clips", "play");
    navButton("assistant", "Asistente IA", "spark");
    navButton("settings", "Configuración", "settings");
    nav = first;
    side.add(second);
    side.add(
        html(
            "<div class='sidebar-bottom'><div class='status-dot'></div><strong>Un espacio para"
                + " crear</strong><p>Tu comunidad, cuidada.<br>Tu contenido, en"
                + " movimiento.</p><span class='version-pill'>StreamGuard · 1.0</span></div>"));
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
    menu.getElement().setAttribute("aria-label", "Mostrar menú");
    header.add(menu);
    FlowPanel find = panel("global-search");
    find.add(html(icon("search")));
    TextBox query = input("Busca una transmisión o un canal", search);
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
    account.add(html("<span class='desktop-note'>CREA. CONECTA. COMPARTE.</span>"));
    if (user == null)
      account.add(button("Iniciar sesión", "button primary small", () -> route("login")));
    else {
      account.add(button("Avisos", "button icon-button", this::notifications));
      Button badge =
          button(
              text(user, "username").substring(0, 1).toUpperCase(),
              "avatar",
              () -> route("account"));
      badge.getElement().setAttribute("aria-label", "Mi cuenta");
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
            + (key.equals("moderation") ? "<span class='nav-chip'>IA</span>" : ""));
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
            "<div class='hero-copy'><span class='eyebrow'><span class='status-dot'></span>EL"
                + " DIRECTO EMPIEZA CONTIGO</span><h1>Tu comunidad.<br>En directo.<br><span>Bajo"
                + " control.</span></h1><p>Un lugar para compartir lo que te mueve.<br>Un asistente"
                + " para cuidar lo que más importa.</p></div><div class='hero-art'"
                + " aria-hidden='true'><div class='orbit orbit-one'></div><div class='orbit"
                + " orbit-two'></div><div class='signal-core'>"
                + icon("shield")
                + "</div><div class='floating-label label-top'>"
                + icon("spark")
                + "Un asistente a tu lado</div><div class='floating-label label-bottom'><span"
                + " class='status-dot'></span>Conecta con tu audiencia</div><div"
                + " class='sound-wave'><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i></div></div>"));
    FlowPanel heroActions = panel("hero-actions");
    heroActions.add(button("Abrir mi estudio  ↗", "button primary", () -> route("studio")));
    heroActions.add(
        button("Ver clips de la comunidad", "button subtle", () -> route("public-clips")));
    hero.add(heroActions);
    content.add(hero);
    FlowPanel filters = panel("category-row");
    filters.add(filter("Todo", ""));
    for (int i = 0; i < categories.size(); i++) {
      JSONObject cat = obj(categories.get(i));
      filters.add(filter(text(cat, "name"), text(cat, "slug")));
    }
    content.add(filters);
    FlowPanel section = panel("section-title");
    section.add(
        html("<div><p class='eyebrow'>ENCUENTRA TU COMUNIDAD</p><h2>Ahora en directo</h2></div>"));
    section.add(html("<span class='live-pill'><span class='status-dot'></span>TIEMPO REAL</span>"));
    content.add(section);
    FlowPanel cards = panel("stream-grid");
    content.add(cards);
    cards.add(empty("Conectando con la comunidad…", "Buscando transmisiones disponibles."));
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
                    "Aquí empieza la próxima gran conversación",
                    search.isEmpty()
                        ? "Todavía no hay transmisiones en directo. Crea tu canal y sé el primero"
                              + " en compartir."
                        : "No encontramos directos con esa búsqueda. Prueba otro término."));
            return;
          }
          for (int i = 0; i < rows.size(); i++) {
            JSONObject s = obj(rows.get(i));
            FlowPanel card = panel("stream-card");
            card.add(
                html(
                    "<div class='stream-cover'><span class='live-tag'>EN VIVO</span><div"
                        + " class='cover-waves'></div>"
                        + icon("video")
                        + "<span class='viewer-tag'>"
                        + esc(text(s, "viewers"))
                        + " espectadores</span></div><div class='stream-copy'><span"
                        + " class='category-text'>"
                        + esc(text(s, "category"))
                        + "</span><h3>"
                        + esc(text(s, "title"))
                        + "</h3><p>"
                        + esc(text(s, "channel_name"))
                        + " <span class='verified'>✓</span></p></div>"));
            card.add(
                button(
                    "Entrar al directo  →",
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
          cards.add(
              empty(
                  "No logramos conectar",
                  "El servidor puede estar apagado. Inicia el backend para explorar"
                      + " transmisiones."));
        });
    content.add(
        html(
            "<div class='feature-strip'><article>"
                + icon("shield")
                + "<div><h3>El chat, en buenas manos</h3><p>Reglas claras, apoyo de IA y revisión"
                + " humana.</p></div></article><article>"
                + icon("play")
                + "<div><h3>Momentos que se quedan</h3><p>Marca, revisa y comparte tus mejores"
                + " clips.</p></div></article><article>"
                + icon("video")
                + "<div><h3>Tu directo, donde estés</h3><p>Desde tu computador o desde tu"
                + " celular.</p></div></article></div>"));
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
        "BIENVENIDO A STREAMGUARD",
        register ? "Tu comunidad empieza aquí" : "Qué bueno verte de nuevo",
        register
            ? "Crea una cuenta para transmitir, conversar y cuidar tu comunidad."
            : "Entra a tu cuenta y vuelve a conectar.");
    FlowPanel card = panel("form-card auth-card");
    TextBox username = input("Tu nombre de usuario", "");
    TextBox email = input("tu@correo.com", "");
    email.getElement().setAttribute("type", "email");
    PasswordTextBox password = new PasswordTextBox();
    password.getElement().setAttribute("placeholder", "Mínimo 10 caracteres");
    password
        .getElement()
        .setAttribute("autocomplete", register ? "new-password" : "current-password");
    if (register) card.add(field("Nombre de usuario", username));
    card.add(field("Correo electrónico", email));
    card.add(field("Contraseña", password));
    CheckBox consent =
        new CheckBox(
            "Acepto que los mensajes se analicen para la moderación del chat, incluyendo el envío a"
                + " Gemini cuando esté configurado.");
    consent.setStyleName("consent");
    if (register) card.add(consent);
    Button submit =
        button(
            register ? "Crear mi cuenta" : "Entrar a mi cuenta", "button primary full", () -> {});
    Runnable send =
        () -> {
          if (register
              && (username.getText().length() < 3
                  || password.getText().length() < 10
                  || !consent.getValue())) {
            toast("Revisa el usuario, la contraseña y el consentimiento para la moderación", true);
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
            register ? "Ya tengo una cuenta" : "Quiero crear una cuenta",
            "text-button",
            () -> route(register ? "login" : "register")));
    content.add(card);
  }

  private void chooseChannel(JSONArray rows) {
    title(
        "TUS CANALES",
        "Elige un espacio",
        "Puedes administrar tu canal o moderar una comunidad que te haya invitado.");
    for (int i = 0; i < rows.size(); i++) {
      JSONObject c = obj(rows.get(i));
      content.add(
          button(
              text(c, "name") + (bool(c, "is_owner") ? " · Mi canal" : " · Moderador"),
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
        "TU PRIMER PASO",
        "Dale un hogar a tu comunidad",
        "Elige un nombre y una dirección para tu canal.");
    FlowPanel form = panel("form-card auth-card");
    TextBox name = input("Nombre del canal", "");
    TextBox slug = input("mi-canal", text(user, "username").toLowerCase().replace('_', '-'));
    TextArea description = area("¿Qué vas a compartir?", "");
    form.add(field("Nombre del canal", name));
    form.add(field("Dirección · letras minúsculas, números y guiones", slug));
    form.add(field("Descripción", description));
    form.add(
        button(
            "Crear canal",
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
    grid.add(metric("Seguidores", text(stats, "followers"), "Tu comunidad"));
    grid.add(metric("Mensajes", text(stats, "messages"), "Participación total"));
    grid.add(metric("En revisión u ocultos", text(stats, "blocked"), "Moderación del canal"));
    grid.add(metric("Clips por revisar", text(stats, "pending_clips"), "Listos para decidir"));
    content.add(grid);
  }

  private boolean owner() {
    return user != null && text(obj(dash.get("channel")), "owner_id").equals(text(user, "id"));
  }

  private void studio() {
    title(
        "TU ESPACIO DE CREACIÓN",
        "Hola, " + text(user, "username") + " ✦",
        "Todo lo que necesitas para conectar con tu audiencia, en un solo lugar.");
    stats();
    JSONObject latest = obj(dash.get("stream"));
    if (!owner()) {
      content.add(
          html(
              "<div class='info-banner'>Participas como moderador de "
                  + esc(text(obj(dash.get("channel")), "name"))
                  + ".</div>"));
      if (text(latest, "status").equals("LIVE"))
        content.add(
            button(
                "Abrir directo y moderar",
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
            "<div class='panel-heading'><h2>Tu transmisión</h2><span class='tag'>"
                + (broadcasting ? "EN VIVO" : "ESTUDIO")
                + "</span></div><div class='video-stage'><video id='live-video' autoplay"
                + " playsinline muted></video><div class='video-placeholder'"
                + " id='video-placeholder'>"
                + icon("video")
                + "<h3>El escenario es tuyo</h3><p>Prepara tu cámara y empieza a"
                + " compartir.</p></div><div id='live-caption' class='live-caption'></div></div>"));
    if (broadcasting) {
      FlowPanel actions = panel("action-row");
      actions.add(
          button(
              "Marcar momento",
              "button primary",
              () ->
                  api(
                      "POST",
                      "/streams/" + stream + "/highlights",
                      object("source", "MANUAL", "reason", "Momento marcado por el creador"),
                      v ->
                          toast(
                              "Momento marcado. Se capturará el segmento más reciente.", false))));
      actions.add(
          button(
              "Activar subtítulos",
              "button subtle",
              () ->
                  MediaBridge.captions(
                      error -> {
                        if (!error.isEmpty()) toast(error, true);
                        else toast("Transcripción activada en este navegador", false);
                      })));
      actions.add(button("Finalizar directo", "button danger", () -> finish()));
      left.add(actions);
      MediaBridge.attach();
    } else if (text(latest, "status").equals("LIVE")) {
      left.add(
          html(
              "<div class='info-banner'>Este canal tiene un directo activo en otra pestaña."
                  + " Finalízalo allí o ciérralo antes de comenzar.</div>"));
      left.add(
          button(
              "Cerrar directo activo",
              "button danger",
              () ->
                  api(
                      "POST",
                      "/streams/" + text(latest, "id") + "/end",
                      new JSONObject(),
                      v -> loadDashboard(true))));
    } else {
      FlowPanel form = panel("stream-form");
      TextBox name = input("Un título que invite a quedarse", "");
      TextArea description = area("Cuéntale a tu audiencia de qué trata el directo", "");
      ListBox cats = new ListBox();
      for (int i = 0; i < categories.size(); i++) {
        JSONObject c = obj(categories.get(i));
        cats.addItem(text(c, "name"), text(c, "id"));
      }
      CheckBox shareScreen = new CheckBox("Compartir pantalla en lugar de cámara");
      form.add(field("Título del directo", name));
      FlowPanel row = panel("form-row");
      row.add(field("Categoría", cats));
      row.add(field("Descripción", description));
      form.add(row);
      form.add(shareScreen);
      Button start = button("Empezar transmisión  ↗", "button primary", () -> {});
      start.addClickHandler(
          e -> {
            if (name.getText().isBlank()) {
              toast("Agrega un título a tu directo", true);
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
    right.add(
        html(
            "<div class='panel-heading'><h2>Asistente de comunidad</h2>"
                + icon("spark")
                + "</div>"));
    right.add(aiStatus());
    JSONObject policy = obj(dash.get("policy"));
    right.add(
        html(
            "<div class='assistant-card'><span class='shield-orb'>"
                + icon("shield")
                + "</span><h3>Tu equipo tiene un apoyo extra</h3><p>La moderación está en nivel"
                + " <strong>"
                + esc(levelName(text(policy, "level")))
                + "</strong>. Los casos dudosos quedan pendientes de una decisión"
                + " humana.</p></div>"));
    right.add(button("Revisar moderación  →", "button subtle full", () -> route("moderation")));
    right.add(button("Configurar reglas  →", "button subtle full", () -> route("settings")));
    right.add(
        html(
            "<div class='quick-tip'><strong>Antes de ir en vivo</strong><p>Verifica cámara y"
                + " micrófono. Marca tus momentos favoritos y revisa cada clip antes de"
                + " publicarlo.</p></div>"));
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
          toast("Transmisión finalizada", false);
          loadDashboard(true);
        });
  }

  private void watch() {
    title("CONECTA EN TIEMPO REAL", "Entrando al directo…", "La comunidad te espera.");
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
                      + "<h3>Conectando con el creador</h3><p>El video aparecerá cuando el emisor"
                      + " esté listo.</p></div><div id='live-caption'"
                      + " class='live-caption'></div></div>"));
          FlowPanel bar = panel("action-row");
          audience = new Label(text(s, "viewers") + " espectadores");
          bar.add(audience);
          bar.add(
              button(
                  "Seguir canal",
                  "button primary small",
                  () -> {
                    if (user == null) toast("Inicia sesión para seguir este canal", true);
                    else
                      api(
                          "POST",
                          "/channels/" + text(s, "channel_id") + "/follow",
                          new JSONObject(),
                          r -> toast("Ya sigues este canal", false));
                  }));
          player.add(bar);
          layout.add(player);
          layout.add(chatPanel());
          content.add(layout);
          if (text(s, "status").equals("LIVE")) {
            loadMessages();
            MediaBridge.connect(stream, false, this::realtime);
          } else toast("Esta transmisión ya terminó", false);
        });
  }

  private FlowPanel chatPanel() {
    messageIds.clear();
    FlowPanel panel = panel("surface chat-panel");
    panel.add(
        html(
            "<div class='panel-heading'><h2>Chat de la comunidad</h2><span class='mini-tag'>"
                + icon("shield")
                + "Moderado</span></div>"));
    chatList = panel("chat-list");
    panel.add(chatList);
    TextBox message =
        input(user == null ? "Inicia sesión para conversar" : "Comparte algo con la comunidad", "");
    message.setMaxLength(1000);
    FlowPanel send = panel("chat-compose");
    send.add(message);
    Button submit = button("Enviar", "button primary small", () -> {});
    Runnable action =
        () -> {
          if (user == null) {
            toast("Inicia sesión para escribir en el chat", true);
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
    panel.add(
        html(
            "<p class='chat-footnote'>Sé respetuoso. Tu mensaje se revisa con las reglas del"
                + " canal.</p>"));
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
        audience.setText(text(e, "viewers") + " espectadores");
      else if (type.equals("notice")) toast(text(e, "body"), false);
      else if (type.equals("error")) toast(text(e, "message"), true);
      else if (type.equals("ended")) {
        broadcasting = false;
        toast(text(e, "message"), false);
        MediaBridge.stop();
        if (screen.equals("studio")) loadDashboard(true);
      } else if (type.equals("queue-updated") && screen.equals("moderation")) loadDashboard(true);
      else if (type.equals("clips-updated")) {
        toast("Hay un nuevo clip en tu biblioteca", false);
        if (screen.equals("clips")) loadDashboard(true);
      } else if (type.equals("capture-status")) toast(text(e, "message"), bool(e, "error"));
    } catch (Exception ignored) {
    }
  }

  private void moderation() {
    title(
        "CUIDA TU COMUNIDAD",
        "Moderación",
        "La IA detecta señales. Tú y tu equipo toman las decisiones que necesitan contexto.");
    stats();
    content.add(aiStatus());
    JSONArray queue = arr(dash.get("queue"));
    FlowPanel list = panel("surface moderation-list");
    list.add(
        html(
            "<div class='panel-heading'><h2>Mensajes por revisar</h2><span class='count-pill'>"
                + queue.size()
                + "</span></div>"));
    if (queue.size() == 0)
      list.add(empty("Todo está al día", "Los mensajes que necesiten contexto aparecerán aquí."));
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
      actions.add(button("Permitir", "button primary small", () -> reviewMessage(q, true)));
      actions.add(button("Ocultar", "button danger small", () -> reviewMessage(q, false)));
      actions.add(
          button(
              "Silenciar 5 min",
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
                          "Sanción aplicada por un moderador humano"),
                      v -> {
                        toast("Usuario silenciado durante 5 minutos", false);
                        loadDashboard(true);
                      })));
      row.add(actions);
      list.add(row);
    }
    content.add(list);
    FlowPanel sanctions = panel("surface");
    sanctions.add(html("<div class='panel-heading'><h2>Sanciones activas</h2></div>"));
    JSONArray rows = arr(dash.get("sanctions"));
    if (rows.size() == 0)
      sanctions.add(html("<p class='muted padded'>No hay usuarios sancionados.</p>"));
    for (int i = 0; i < rows.size(); i++) {
      JSONObject s = obj(rows.get(i));
      FlowPanel row = panel("simple-row");
      row.add(
          new Label(
              "@"
                  + text(s, "username")
                  + " · "
                  + (text(s, "type").equals("BAN") ? "Bloqueado" : "Silenciado")
                  + " · "
                  + text(s, "expires_at")));
      row.add(
          button(
              "Retirar sanción",
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
          toast(approve ? "Mensaje aprobado" : "Mensaje ocultado", false);
          loadDashboard(true);
        });
  }

  private void clips() {
    title(
        "CONTENIDO QUE SIGUE VIVO",
        "Tu biblioteca de clips",
        "Revisa, edita y publica los momentos que quieres compartir.");
    JSONArray rows = arr(dash.get("clips"));
    FlowPanel grid = panel("clip-grid");
    if (rows.size() == 0)
      grid.add(
          empty(
              "Tus mejores momentos vivirán aquí",
              "Durante el directo, marca un momento o activa la detección automática en la"
                  + " configuración."));
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
            "Ver video",
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
            "Descargar",
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
      actions.add(button("Editar", "button subtle small", () -> editClip(clip)));
      actions.add(button("Publicar", "button primary small", () -> clipReview(clip, true)));
      actions.add(button("Rechazar", "button danger small", () -> clipReview(clip, false)));
    }
    if (text(clip, "status").equals("APPROVED"))
      actions.add(
          button(
              "Compartir",
              "button subtle small",
              () -> {
                MediaBridge.share(asset, text(clip, "title"));
                toast("Enlace listo para compartir", false);
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
          toast(approve ? "Clip publicado" : "Clip rechazado", false);
          loadDashboard(true);
        });
  }

  private void editClip(JSONObject clip) {
    DialogBox dialog = new DialogBox();
    dialog.setText("Editar clip");
    dialog.setGlassEnabled(true);
    FlowPanel form = panel("dialog-form");
    TextBox title = input("Título", text(clip, "title"));
    TextArea desc = area("Descripción", text(clip, "description"));
    TextBox start = input("0", "0"),
        end =
            input(
                "Fin",
                Integer.toString(
                    (int) (number(clip, "end_seconds") - number(clip, "start_seconds"))));
    form.add(field("Título", title));
    form.add(field("Descripción", desc));
    form.add(
        html(
            "<p class='muted'>Recorta usando segundos relativos al inicio del video. Tras editar,"
                + " el clip vuelve a quedar pendiente de aprobación.</p>"));
    FlowPanel row = panel("form-row");
    row.add(field("Desde (segundos)", start));
    row.add(field("Hasta (segundos)", end));
    form.add(row);
    form.add(
        button(
            "Guardar cambios",
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
                toast("Ingresa segundos válidos", true);
              }
            }));
    form.add(button("Cancelar", "button subtle", dialog::hide));
    dialog.setWidget(form);
    dialog.center();
    title.setFocus(true);
  }

  private void publicClips() {
    title(
        "MOMENTOS DE LA COMUNIDAD",
        "Una buena historia sigue aquí",
        "Clips revisados y publicados por sus creadores.");
    FlowPanel grid = panel("clip-grid");
    content.add(grid);
    api(
        "GET",
        "/clips/public",
        null,
        v -> {
          JSONArray rows = arr(v);
          if (rows.size() == 0)
            grid.add(
                empty(
                    "Todavía no hay clips publicados",
                    "Vuelve cuando los creadores compartan sus primeros momentos."));
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
        "UNA MIRADA EXTRA",
        "Tu asistente IA",
        "Convierte la conversación y las transcripciones en contexto para tu contenido.");
    content.add(aiStatus());
    JSONObject latest = obj(dash.get("stream"));
    if (text(latest, "id").isEmpty()) {
      content.add(
          empty(
              "Primero, una conversación",
              "Realiza un directo para generar un resumen, temas y preguntas frecuentes."));
      return;
    }
    content.add(
        button(
            "Generar análisis de la última transmisión",
            "button primary",
            () ->
                api(
                    "POST",
                    "/streams/" + text(latest, "id") + "/summary",
                    new JSONObject(),
                    v -> {
                      toast("Análisis guardado · " + providerName(text(obj(v), "provider")), false);
                      loadDashboard(true);
                    })));
    JSONObject ai = obj(dash.get("ai"));
    JSONArray summaries = arr(ai.get("summaries"));
    FlowPanel surface = panel("surface assistant-summary");
    surface.add(
        html(
            "<div class='panel-heading'><h2>Resumen de la transmisión</h2>"
                + icon("spark")
                + "</div>"));
    surface.add(
        html(
            "<p class='summary-text'>"
                + esc(
                    summaries.size() > 0
                        ? text(obj(summaries.get(0)), "content")
                        : "Aún no has generado un resumen de esta transmisión.")
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
    faqs.add(html("<div class='panel-heading'><h2>Preguntas del chat</h2></div>"));
    JSONArray faq = arr(ai.get("faqs"));
    if (faq.size() == 0)
      faqs.add(html("<p class='muted padded'>Aún no hay preguntas frecuentes identificadas.</p>"));
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
    content.add(
        html(
            "<p class='muted'>El análisis usa mensajes visibles, marcadores y transcripciones"
                + " disponibles. Su alcance depende del contexto registrado.</p>"));
  }

  private void settings() {
    title(
        "LAS REGLAS LAS PONES TÚ",
        "Configuración del canal",
        "Ajusta el apoyo de moderación a la comunidad que quieres construir.");
    if (!owner()) {
      content.add(
          empty(
              "Configuración del propietario",
              "Los moderadores pueden revisar mensajes y sanciones. El creador define las reglas"
                  + " del canal."));
      return;
    }
    JSONObject p = obj(dash.get("policy")), s = obj(dash.get("settings"));
    FlowPanel form = panel("surface settings-form");
    ListBox level = new ListBox();
    level.addItem("Relajado", "RELAXED");
    level.addItem("Equilibrado", "BALANCED");
    level.addItem("Estricto", "STRICT");
    for (int i = 0; i < level.getItemCount(); i++)
      if (level.getValue(i).equals(text(p, "level"))) level.setSelectedIndex(i);
    form.add(field("Nivel de moderación", level));
    CheckBox
        hide =
            check(
                "Ocultar automáticamente las infracciones de alta confianza", bool(p, "autoHide")),
        mute =
            check(
                "Silenciar temporalmente al autor cuando se oculta una infracción",
                bool(p, "autoMute")),
        links = check("Permitir enlaces en el chat", bool(p, "allowLinks")),
        auto = check("Detectar momentos por aumentos del chat y del audio", bool(s, "auto_clips"));
    form.add(hide);
    form.add(mute);
    form.add(links);
    form.add(auto);
    TextBox seconds = input("300", text(p, "muteSeconds")),
        slow = input("0", text(s, "slow_mode_seconds"));
    FlowPanel row = panel("form-row");
    row.add(field("Duración del silencio (segundos)", seconds));
    row.add(field("Modo lento del chat (0–120 segundos)", slow));
    form.add(row);
    TextArea words = area("Una palabra o frase por línea", join(arr(p.get("blockedWords")))),
        topics = area("Un tema por línea", join(arr(p.get("blockedTopics"))));
    form.add(field("Palabras restringidas", words));
    form.add(field("Temas restringidos · requieren análisis con Gemini", topics));
    form.add(
        button(
            "Guardar configuración",
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
                      toast("Configuración guardada", false);
                      loadDashboard(true);
                    });
              } catch (Exception e) {
                toast("Revisa los campos numéricos", true);
              }
            }));
    content.add(form);
    FlowPanel mods = panel("surface settings-form");
    mods.add(
        html(
            "<h2>Tu equipo de moderación</h2><p class='muted'>Invita a usuarios que ya tengan"
                + " cuenta en StreamGuard.</p>"));
    TextBox name = input("Nombre de usuario", "");
    mods.add(field("Usuario", name));
    mods.add(
        button(
            "Agregar moderador",
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
              "Retirar",
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
    dialog.setText("Tus avisos");
    FlowPanel list = panel("dialog-form");
    dialog.setWidget(list);
    list.add(button("Cerrar", "button subtle", dialog::hide));
    api(
        "GET",
        "/notifications",
        null,
        v -> {
          JSONArray rows = arr(v);
          if (rows.size() == 0) list.add(new Label("Todavía no tienes avisos."));
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
    title("TU PERFIL", "@" + text(user, "username"), text(user, "email"));
    content.add(
        button(
            "Cambiar canal de administración",
            "button subtle",
            () -> {
              channel = "";
              route("studio");
            }));
    content.add(
        button(
            "Cerrar sesión",
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
            + (gemini ? "Gemini está conectado" : "Reglas locales activas")
            + "</strong><p>"
            + (gemini
                ? "Análisis semántico y apoyo editorial disponibles."
                : "Gemini aún no está configurado. Las palabras restringidas, los enlaces y el"
                      + " control de repetición siguen funcionando.")
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
    return s.equals("STRICT") ? "estricto" : s.equals("RELAXED") ? "relajado" : "equilibrado";
  }

  private static String providerName(String s) {
    return s.equals("GEMINI")
        ? "Gemini"
        : s.equals("UNAVAILABLE") ? "IA no disponible" : "Reglas locales";
  }

  private static String statusName(String s) {
    return s.equals("APPROVED") ? "Publicado" : s.equals("REJECTED") ? "Rechazado" : "Por revisar";
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
