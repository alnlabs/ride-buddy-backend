package com.alnlabs.ridebuddy.share;

import com.alnlabs.ridebuddy.common.ApiException;
import com.alnlabs.ridebuddy.config.AppProperties;
import com.alnlabs.ridebuddy.request.RideRequestService;
import com.alnlabs.ridebuddy.ride.RideService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class SharePageController {

    private final RideService rideService;
    private final RideRequestService rideRequestService;
    private final AppProperties appProperties;

    public SharePageController(
            RideService rideService,
            RideRequestService rideRequestService,
            AppProperties appProperties
    ) {
        this.rideService = rideService;
        this.rideRequestService = rideRequestService;
        this.appProperties = appProperties;
    }

    @GetMapping(value = "/r/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public String ridePage(@PathVariable UUID id) {
        try {
            return render(rideService.share(id));
        } catch (ApiException e) {
            return notFound("Ride");
        }
    }

    @GetMapping(value = "/n/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public String needPage(@PathVariable UUID id) {
        try {
            return render(rideRequestService.share(id));
        } catch (ApiException e) {
            return notFound("Seat request");
        }
    }

    private String render(SharePayload p) {
        boolean ride = "ride".equals(p.type());
        String eyebrow = ride ? "Seat available" : "Looking for a seat";
        String personLabel = ride ? "Hosted by" : "Posted by";
        String store = firstNonBlank(
                appProperties.share().playStoreUrl(),
                appProperties.share().appStoreUrl(),
                "https://ridebuddy.alnlabs.com"
        );
        String deep = escape(p.deepLink());
        String title = escape(p.title()) + " · Ride Buddy";
        String desc = escape(p.subtitle() + (p.personName() != null ? " · " + p.personName() : ""));

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"en\"><head>");
        html.append("<meta charset=\"utf-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        html.append("<title>").append(title).append("</title>");
        html.append("<meta property=\"og:type\" content=\"website\">");
        html.append("<meta property=\"og:title\" content=\"").append(title).append("\">");
        html.append("<meta property=\"og:description\" content=\"").append(desc).append("\">");
        html.append("<meta property=\"og:url\" content=\"").append(escape(p.link())).append("\">");
        html.append("<meta name=\"twitter:card\" content=\"summary\">");
        html.append("<meta name=\"twitter:title\" content=\"").append(title).append("\">");
        html.append("<meta name=\"twitter:description\" content=\"").append(desc).append("\">");
        html.append("<style>");
        html.append("*{box-sizing:border-box}body{margin:0;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;");
        html.append("background:linear-gradient(165deg,#e8f1fb 0%,#f7f4ef 45%,#fff 100%);color:#122033;min-height:100vh;");
        html.append("display:flex;align-items:center;justify-content:center;padding:24px}");
        html.append(".card{max-width:420px;width:100%;background:#fff;border:1px solid #d7e0ea;border-radius:20px;");
        html.append("padding:28px 24px;box-shadow:0 18px 40px rgba(18,32,51,.08)}");
        html.append(".brand{font-weight:800;letter-spacing:-.02em;font-size:1.35rem;color:#1a5fb4;margin:0 0 4px}");
        html.append(".eyebrow{font-size:.75rem;font-weight:700;text-transform:uppercase;letter-spacing:.06em;color:#6b7c93;margin:0 0 16px}");
        html.append("h1{font-size:1.25rem;line-height:1.35;margin:0 0 8px;font-weight:750}");
        html.append(".row{margin:0 0 12px;font-size:.95rem;line-height:1.45;color:#3d4f66}");
        html.append(".row .label{display:block;font-size:.72rem;font-weight:700;text-transform:uppercase;");
        html.append("letter-spacing:.05em;color:#6b7c93;margin:0 0 2px}");
        html.append(".row .value{color:#122033;white-space:pre-wrap}");
        html.append(".person{margin:16px 0 0;font-size:.9rem;color:#3d4f66}");
        html.append(".person strong{color:#122033}");
        html.append(".note{margin:18px 0 0;font-size:.8rem;color:#6b7c93;line-height:1.4}");
        html.append(".actions{display:flex;flex-direction:column;gap:10px;margin-top:22px}");
        html.append("a.btn{display:block;text-align:center;text-decoration:none;border-radius:12px;padding:14px 16px;");
        html.append("font-weight:700;font-size:.95rem}");
        html.append("a.primary{background:#1a5fb4;color:#fff}");
        html.append("a.secondary{background:#f0f4f8;color:#122033;border:1px solid #d7e0ea}");
        html.append("</style></head><body><div class=\"card\">");
        html.append("<p class=\"brand\">Ride Buddy</p>");
        html.append("<p class=\"eyebrow\">").append(escape(eyebrow)).append("</p>");
        html.append("<div class=\"row\"><span class=\"label\">📍 From</span><span class=\"value\">")
                .append(escape(p.fromLabel())).append("</span></div>");
        html.append("<div class=\"row\"><span class=\"label\">📌 To</span><span class=\"value\">")
                .append(escape(p.toLabel())).append("</span></div>");
        html.append("<div class=\"row\"><span class=\"label\">🕒 When</span><span class=\"value\">")
                .append(escape(p.whenLabel())).append("</span></div>");
        html.append("<div class=\"row\"><span class=\"label\">💺 Details</span><span class=\"value\">")
                .append(escape(p.metaLine())).append("</span></div>");
        html.append("<p class=\"person\">👤 ").append(escape(personLabel)).append(" <strong>")
                .append(escape(p.personName())).append("</strong>");
        if (p.personRole() != null && !p.personRole().isBlank()) {
            html.append("<br>").append(escape(p.personRole()));
        }
        html.append("</p>");
        html.append("<p class=\"note\">Office carpool · share the seat cost in cash. Not a taxi.</p>");
        html.append("<div class=\"actions\">");
        html.append("<a class=\"btn primary\" href=\"").append(deep).append("\">Open in Ride Buddy</a>");
        html.append("<a class=\"btn secondary\" href=\"").append(escape(store)).append("\">Get the app</a>");
        html.append("</div></div></body></html>");
        return html.toString();
    }

    private static String notFound(String kind) {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>Not found · Ride Buddy</title></head><body style=\"font-family:system-ui;padding:40px;text-align:center\">"
                + "<h1>" + escape(kind) + " not found</h1>"
                + "<p>This post may have been cancelled or expired.</p></body></html>";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }
}
